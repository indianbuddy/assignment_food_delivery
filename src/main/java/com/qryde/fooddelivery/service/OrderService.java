package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.event.OrderPlacedEvent;
import com.qryde.fooddelivery.event.OrderStatusChangedEvent;
import com.qryde.fooddelivery.exception.AccessDeniedBusinessException;
import com.qryde.fooddelivery.exception.ConflictException;
import com.qryde.fooddelivery.exception.InsufficientStockException;
import com.qryde.fooddelivery.exception.InvalidStateTransitionException;
import com.qryde.fooddelivery.exception.ResourceNotFoundException;
import com.qryde.fooddelivery.repository.MenuItemRepository;
import com.qryde.fooddelivery.repository.OrderItemRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import com.qryde.fooddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final MenuItemRepository menuItemRepository;
	private final UserRepository userRepository;
	private final RestaurantService restaurantService;
	private final DeliveryPartnerService deliveryPartnerService;
	private final PaymentService paymentService;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * Places an order atomically: every item's stock reservation, the order +
	 * order-item rows, and the payment charge happen inside one transaction.
	 * Stock reservation uses a single conditional UPDATE per item
	 * (MenuItemRepository#decrementStock) rather than read-then-write, so
	 * concurrent orders for the same item can never both succeed past
	 * available stock - whichever request's UPDATE lands first wins the
	 * remaining units, and the next one simply sees 0 rows affected. If
	 * *any* item in the cart can't be reserved, or the payment is declined,
     * the exception thrown here rolls back the whole transaction - including
	 * stock already decremented earlier in this same loop - so nothing is
	 * left half-applied.
	 */
	@Transactional
	public OrderResponse placeOrder(Long customerId, PlaceOrderRequest request) {
		User customer = userRepository.findById(customerId)
				.orElseThrow(() -> new ResourceNotFoundException("User " + customerId + " not found"));
		Restaurant restaurant = restaurantService.findEntity(request.restaurantId());
		if (!restaurant.isActive()) {
			throw new IllegalArgumentException("Restaurant is not currently accepting orders");
		}

		Order order = Order.builder()
				.customer(customer)
				.restaurant(restaurant)
				.status(OrderStatus.PLACED)
				.totalAmount(BigDecimal.ZERO)
				.build();
		order = orderRepository.save(order);

		BigDecimal total = BigDecimal.ZERO;
		for (OrderItemRequest itemRequest : request.items()) {
			MenuItem menuItem = menuItemRepository.findById(itemRequest.menuItemId())
					.orElseThrow(() -> new ResourceNotFoundException(
							"Menu item " + itemRequest.menuItemId() + " not found"));

			if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
				throw new IllegalArgumentException(
						"Menu item " + menuItem.getId() + " does not belong to restaurant " + restaurant.getId());
			}
			if (!menuItem.isAvailable()) {
				throw new IllegalArgumentException("Menu item '" + menuItem.getName() + "' is not available");
			}

			int updatedRows = menuItemRepository.decrementStock(menuItem.getId(), itemRequest.quantity());
			if (updatedRows == 0) {
				throw new InsufficientStockException(
						"Insufficient stock for '" + menuItem.getName() + "'");
			}

			BigDecimal lineTotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
			total = total.add(lineTotal);

			// Not saved directly here: Order.items cascades ALL, so adding to the
			// in-memory collection is the single way these get persisted, once,
			// when the order itself is saved below.
			OrderItem orderItem = OrderItem.builder()
					.order(order)
					.menuItem(menuItem)
					.menuItemName(menuItem.getName())
					.quantity(itemRequest.quantity())
					.priceAtOrder(menuItem.getPrice())
					.build();
			order.getItems().add(orderItem);
		}

		order.setTotalAmount(total);
		order = orderRepository.save(order);

		var payment = paymentService.charge(order, total, request.paymentMethod());
		if (payment.getStatus() == PaymentStatus.FAILED) {
			throw new ConflictException("Payment was declined; order could not be placed");
		}

		eventPublisher.publishEvent(new OrderPlacedEvent(order.getId()));
		return OrderMapper.toResponse(order);
	}

	/**
	 * Order tracking is only visible to the parties actually involved in it
	 * (the customer who placed it, the restaurant that owns it, the partner
	 * assigned to it) or an admin - otherwise any authenticated user could
	 * page through arbitrary order ids.
	 */
	public OrderResponse getForViewer(Long orderId, Long userId, Role role) {
		Order order = findEntity(orderId);
		boolean allowed = switch (role) {
			case ADMIN -> true;
			case CUSTOMER -> order.getCustomer().getId().equals(userId);
			case RESTAURANT_OWNER -> order.getRestaurant().getOwner().getId().equals(userId);
			case DELIVERY_PARTNER -> order.getDeliveryPartner() != null
					&& order.getDeliveryPartner().getUser().getId().equals(userId);
		};
		if (!allowed) {
			throw new AccessDeniedBusinessException("You do not have access to this order");
		}
		return OrderMapper.toResponse(order);
	}

	public List<OrderResponse> listForCustomer(Long customerId) {
		return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
				.map(OrderMapper::toResponse).toList();
	}

	public List<OrderResponse> listForRestaurantOwner(Long restaurantId, Long ownerId) {
		Restaurant restaurant = restaurantService.findEntity(restaurantId);
		restaurantService.assertOwnership(restaurant, ownerId);
		return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
				.map(OrderMapper::toResponse).toList();
	}

	@Transactional
	public OrderResponse acceptOrder(Long orderId, Long ownerId) {
		Order order = findEntity(orderId);
		restaurantService.assertOwnership(order.getRestaurant(), ownerId);
		return transition(order, OrderStatus.PLACED, OrderStatus.ACCEPTED);
	}

	@Transactional
	public OrderResponse rejectOrder(Long orderId, Long ownerId) {
		Order order = findEntity(orderId);
		restaurantService.assertOwnership(order.getRestaurant(), ownerId);
		OrderResponse response = transition(order, OrderStatus.PLACED, OrderStatus.REJECTED);
		restock(order.getId());
		return response;
	}

	@Transactional
	public OrderResponse markPreparing(Long orderId, Long ownerId) {
		Order order = findEntity(orderId);
		restaurantService.assertOwnership(order.getRestaurant(), ownerId);
		return transition(order, OrderStatus.ACCEPTED, OrderStatus.PREPARING);
	}

	@Transactional
	public OrderResponse cancelOrder(Long orderId, Long customerId) {
		Order order = findEntity(orderId);
		if (!order.getCustomer().getId().equals(customerId)) {
			throw new AccessDeniedBusinessException("This order does not belong to you");
		}
		// No separate "is this status cancellable" check here - OrderStatus's
		// transition table is the single source of truth for that (only PLACED
		// and ACCEPTED allow CANCELLED as a next state), so transition() below
		// already rejects e.g. cancelling a PREPARING order with a clear
		// InvalidStateTransitionException, without a second, hand-maintained
		// copy of the same rule that could drift from the enum.
		OrderResponse response = transition(order, order.getStatus(), OrderStatus.CANCELLED);
		restock(order.getId());
		return response;
	}

	@Transactional
	public OrderResponse markOutForDelivery(Long orderId, Long partnerUserId) {
		Order order = findEntity(orderId);
		assertAssignedPartner(order, partnerUserId);
		return transition(order, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY);
	}

	@Transactional
	public OrderResponse markDelivered(Long orderId, Long partnerUserId) {
		Order order = findEntity(orderId);
		assertAssignedPartner(order, partnerUserId);
		Long partnerId = order.getDeliveryPartner().getId();
		OrderResponse response = transition(order, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
		deliveryPartnerService.markAvailable(partnerId);
		return response;
	}

	private void assertAssignedPartner(Order order, Long partnerUserId) {
		if (order.getDeliveryPartner() == null ||
				!order.getDeliveryPartner().getUser().getId().equals(partnerUserId)) {
			throw new AccessDeniedBusinessException("You are not the assigned partner for this order");
		}
	}

	/**
	 * Validates the transition against the state machine for a clear error
	 * message, then applies it via a guarded conditional UPDATE
	 * (OrderRepository#updateStatusIfCurrent) so a second concurrent caller
	 * acting on stale state gets a conflict instead of silently clobbering
	 * the first caller's change.
	 */
	private OrderResponse transition(Order order, OrderStatus expected, OrderStatus target) {
		if (order.getStatus() != expected || !expected.canTransitionTo(target)) {
			throw new InvalidStateTransitionException(
					"Cannot move order from " + order.getStatus() + " to " + target);
		}

		int updated = orderRepository.updateStatusIfCurrent(order.getId(), expected, target);
		if (updated == 0) {
			throw new ConflictException(
					"Order status changed concurrently; please refresh and retry");
		}

		Order refreshed = findEntity(order.getId());
		eventPublisher.publishEvent(new OrderStatusChangedEvent(refreshed.getId(), expected, target));
		return OrderMapper.toResponse(refreshed);
	}

	private void restock(Long orderId) {
		for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
			menuItemRepository.restock(item.getMenuItem().getId(), item.getQuantity());
		}
	}

	Order findEntity(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
	}
}

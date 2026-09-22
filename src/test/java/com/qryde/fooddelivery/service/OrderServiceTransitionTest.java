package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.exception.AccessDeniedBusinessException;
import com.qryde.fooddelivery.exception.ConflictException;
import com.qryde.fooddelivery.exception.InvalidStateTransitionException;
import com.qryde.fooddelivery.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the order-lifecycle state machine in OrderService: legal
 * transitions, illegal ones, ownership checks, and the "someone else already
 * moved this order" conflict path.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTransitionTest {

	@Mock private OrderRepository orderRepository;
	@Mock private OrderItemRepository orderItemRepository;
	@Mock private MenuItemRepository menuItemRepository;
	@Mock private UserRepository userRepository;
	@Mock private RestaurantService restaurantService;
	@Mock private DeliveryPartnerService deliveryPartnerService;
	@Mock private PaymentService paymentService;
	@Mock private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private OrderService orderService;

	private User owner;
	private Restaurant restaurant;
	private Order order;

	@BeforeEach
	void setUp() {
		owner = User.builder().id(2L).role(Role.RESTAURANT_OWNER).build();
		User customer = User.builder().id(1L).role(Role.CUSTOMER).build();
		restaurant = Restaurant.builder().id(10L).owner(owner).active(true).build();
		order = Order.builder().id(500L).customer(customer).restaurant(restaurant)
				.status(OrderStatus.PLACED).totalAmount(BigDecimal.TEN)
				.items(List.of()).createdAt(Instant.now()).updatedAt(Instant.now()).build();
	}

	@Test
	void acceptOrder_validTransition_succeeds() {
		when(orderRepository.updateStatusIfCurrent(500L, OrderStatus.PLACED, OrderStatus.ACCEPTED)).thenReturn(1);

		Order accepted = cloneWithStatus(order, OrderStatus.ACCEPTED);
		when(orderRepository.findById(500L)).thenReturn(Optional.of(order), Optional.of(accepted));

		OrderResponse response = orderService.acceptOrder(500L, 2L);

		assertThat(response.status()).isEqualTo("ACCEPTED");
		verify(eventPublisher).publishEvent(any(com.qryde.fooddelivery.event.OrderStatusChangedEvent.class));
	}

	@Test
	void acceptOrder_wrongOwner_throwsAccessDenied() {
		when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
		doThrow(new AccessDeniedBusinessException("not your restaurant"))
				.when(restaurantService).assertOwnership(restaurant, 999L);

		assertThatThrownBy(() -> orderService.acceptOrder(500L, 999L))
				.isInstanceOf(AccessDeniedBusinessException.class);

		verify(orderRepository, never()).updateStatusIfCurrent(anyLong(), any(), any());
	}

	@Test
	void acceptOrder_alreadyAccepted_rejectedByStateMachineBeforeHittingDb() {
		Order alreadyAccepted = cloneWithStatus(order, OrderStatus.ACCEPTED);
		when(orderRepository.findById(500L)).thenReturn(Optional.of(alreadyAccepted));

		assertThatThrownBy(() -> orderService.acceptOrder(500L, 2L))
				.isInstanceOf(InvalidStateTransitionException.class);

		verify(orderRepository, never()).updateStatusIfCurrent(anyLong(), any(), any());
	}

	@Test
	void acceptOrder_concurrentlyChangedUnderneathUs_throwsConflict() {
		when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
		// Passes the in-memory state-machine check (order looks like PLACED),
		// but the CAS update reports 0 rows: someone else changed it first.
		when(orderRepository.updateStatusIfCurrent(500L, OrderStatus.PLACED, OrderStatus.ACCEPTED)).thenReturn(0);

		assertThatThrownBy(() -> orderService.acceptOrder(500L, 2L))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void cancelOrder_notOwnedByCustomer_throwsAccessDenied() {
		when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

		assertThatThrownBy(() -> orderService.cancelOrder(500L, 999L))
				.isInstanceOf(AccessDeniedBusinessException.class);
	}

	@Test
	void cancelOrder_afterPreparing_rejectedByStateMachine() {
		Order preparing = cloneWithStatus(order, OrderStatus.PREPARING);
		when(orderRepository.findById(500L)).thenReturn(Optional.of(preparing));

		assertThatThrownBy(() -> orderService.cancelOrder(500L, 1L))
				.isInstanceOf(InvalidStateTransitionException.class);

		verify(orderRepository, never()).updateStatusIfCurrent(anyLong(), any(), any());
	}

	@Test
	void cancelOrder_whilePlaced_restocksItems() {
		MenuItem menuItem = MenuItem.builder().id(77L).stockQuantity(3).build();
		OrderItem item = OrderItem.builder().id(1L).order(order).menuItem(menuItem).quantity(2).build();

		when(orderRepository.updateStatusIfCurrent(500L, OrderStatus.PLACED, OrderStatus.CANCELLED)).thenReturn(1);
		Order cancelled = cloneWithStatus(order, OrderStatus.CANCELLED);
		when(orderRepository.findById(500L)).thenReturn(Optional.of(order), Optional.of(cancelled));
		when(orderItemRepository.findByOrderId(500L)).thenReturn(List.of(item));

		orderService.cancelOrder(500L, 1L);

		verify(menuItemRepository).restock(77L, 2);
	}

	private Order cloneWithStatus(Order source, OrderStatus status) {
		return Order.builder()
				.id(source.getId())
				.customer(source.getCustomer())
				.restaurant(source.getRestaurant())
				.deliveryPartner(source.getDeliveryPartner())
				.status(status)
				.totalAmount(source.getTotalAmount())
				.items(source.getItems())
				.createdAt(source.getCreatedAt())
				.updatedAt(source.getUpdatedAt())
				.build();
	}
}

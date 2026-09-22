package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.exception.InsufficientStockException;
import com.qryde.fooddelivery.repository.MenuItemRepository;
import com.qryde.fooddelivery.repository.OrderItemRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import com.qryde.fooddelivery.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-level (Mockito, no Spring context) coverage of the order placement
 * business logic in isolation from the database. The real concurrency
 * guarantee is verified separately against a real Postgres in
 * ConcurrentOrderPlacementTest - these tests just pin down the sequencing and
 * error-handling contract.
 */
@ExtendWith(MockitoExtension.class)
class OrderServicePlacementTest {

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

	private User customer;
	private Restaurant restaurant;
	private MenuItem menuItem;

	@BeforeEach
	void setUp() {
		customer = User.builder().id(1L).role(Role.CUSTOMER).build();
		User owner = User.builder().id(2L).role(Role.RESTAURANT_OWNER).build();
		City city = City.builder().id(1L).name("Testville").active(true).build();
		restaurant = Restaurant.builder().id(10L).name("Testaurant").city(city).owner(owner).active(true).build();
		menuItem = MenuItem.builder().id(100L).restaurant(restaurant).name("Burger")
				.description("").price(new BigDecimal("9.50")).stockQuantity(5).available(true).build();
	}

	@Test
	void placeOrder_happyPath_decrementsStockAndChargesPayment() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(restaurantService.findEntity(10L)).thenReturn(restaurant);
		when(menuItemRepository.findById(100L)).thenReturn(Optional.of(menuItem));
		when(menuItemRepository.decrementStock(100L, 2)).thenReturn(1);
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
			Order o = inv.getArgument(0);
			if (o.getId() == null) {
				o.setId(500L);
			}
			return o;
		});
		Payment successfulPayment = Payment.builder().status(PaymentStatus.SUCCESS).build();
		when(paymentService.charge(any(), any(), any())).thenReturn(successfulPayment);

		PlaceOrderRequest request = new PlaceOrderRequest(10L, List.of(new OrderItemRequest(100L, 2)), null);
		OrderResponse response = orderService.placeOrder(1L, request);

		assertThat(response.totalAmount()).isEqualByComparingTo("19.00");
		verify(menuItemRepository).decrementStock(100L, 2);
		verify(paymentService).charge(any(), eq(new BigDecimal("19.00")), isNull());
		verify(eventPublisher).publishEvent(any(com.qryde.fooddelivery.event.OrderPlacedEvent.class));
	}

	@Test
	void placeOrder_insufficientStock_throwsAndNeverCharges() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(restaurantService.findEntity(10L)).thenReturn(restaurant);
		when(menuItemRepository.findById(100L)).thenReturn(Optional.of(menuItem));
		when(menuItemRepository.decrementStock(100L, 2)).thenReturn(0);
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

		PlaceOrderRequest request = new PlaceOrderRequest(10L, List.of(new OrderItemRequest(100L, 2)), null);

		assertThatThrownBy(() -> orderService.placeOrder(1L, request))
				.isInstanceOf(InsufficientStockException.class);

		verifyNoInteractions(paymentService);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void placeOrder_paymentDeclined_throwsAfterStockAlreadyReserved() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(restaurantService.findEntity(10L)).thenReturn(restaurant);
		when(menuItemRepository.findById(100L)).thenReturn(Optional.of(menuItem));
		when(menuItemRepository.decrementStock(100L, 1)).thenReturn(1);
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
		Payment declined = Payment.builder().status(PaymentStatus.FAILED).build();
		when(paymentService.charge(any(), any(), any())).thenReturn(declined);

		PlaceOrderRequest request = new PlaceOrderRequest(10L, List.of(new OrderItemRequest(100L, 1)), "FAIL_FOR_TEST");

		assertThatThrownBy(() -> orderService.placeOrder(1L, request))
				.isInstanceOf(com.qryde.fooddelivery.exception.ConflictException.class)
				.hasMessageContaining("declined");
		// Note: in the real (transactional) flow this exception rolls back the
		// stock decrement too - that all-or-nothing behavior is what
		// ConcurrentOrderPlacementTest / the real DB transaction guarantees,
		// not something a mocked repository can demonstrate.
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void placeOrder_menuItemFromDifferentRestaurant_rejected() {
		Restaurant otherRestaurant = Restaurant.builder().id(99L).name("Other").active(true).build();
		when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(restaurantService.findEntity(10L)).thenReturn(restaurant);
		MenuItem foreignItem = MenuItem.builder().id(200L).restaurant(otherRestaurant)
				.name("Fries").price(BigDecimal.ONE).stockQuantity(10).available(true).build();
		when(menuItemRepository.findById(200L)).thenReturn(Optional.of(foreignItem));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

		PlaceOrderRequest request = new PlaceOrderRequest(10L, List.of(new OrderItemRequest(200L, 1)), null);

		assertThatThrownBy(() -> orderService.placeOrder(1L, request))
				.isInstanceOf(IllegalArgumentException.class);
		verify(menuItemRepository, never()).decrementStock(anyLong(), anyInt());
	}
}

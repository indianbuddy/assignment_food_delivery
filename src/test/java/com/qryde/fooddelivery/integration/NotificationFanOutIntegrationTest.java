package com.qryde.fooddelivery.integration;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.repository.NotificationLogRepository;
import com.qryde.fooddelivery.service.OrderService;
import com.qryde.fooddelivery.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies status changes fan out asynchronously (NotificationService is
 * @Async, triggered AFTER_COMMIT) rather than as part of the caller's
 * request/response cycle. placeOrder() returning does NOT mean the
 * notification has been written yet, so this test polls with a timeout
 * instead of asserting immediately after the call.
 */
class NotificationFanOutIntegrationTest extends AbstractIntegrationTest {

	@Autowired private TestDataFactory fixtures;
	@Autowired private OrderService orderService;
	@Autowired private NotificationLogRepository notificationLogRepository;

	@Test
	void placingAnOrderNotifiesCustomerAndRestaurantAsynchronously() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("15.00"), 5);
		User customer = fixtures.createUser(Role.CUSTOMER);

		OrderResponse placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 1)), null));

		awaitTrue(() -> notificationLogRepository.findByOrderId(placed.id()).size() >= 2,
				Duration.ofSeconds(5));

		var logs = notificationLogRepository.findByOrderId(placed.id());
		assertThat(logs).extracting("audience").contains("CUSTOMER", "RESTAURANT");
	}

	private void awaitTrue(BooleanSupplier condition, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			if (condition.getAsBoolean()) {
				return;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		}
		throw new AssertionError("Condition not met within " + timeout);
	}
}

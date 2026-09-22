package com.qryde.fooddelivery.integration;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.repository.MenuItemRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import com.qryde.fooddelivery.service.OrderService;
import com.qryde.fooddelivery.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "concurrent orders for the same menu item should not oversell
 * limited stock" requirement: fires many simultaneous placeOrder calls, each
 * on its own real thread and its own real transaction, at a menu item with
 * exactly ONE unit of stock. Because the reservation is a single conditional
 * UPDATE (MenuItemRepository#decrementStock), the database - not application
 * code - is what decides the winner, so this should hold regardless of
 * thread scheduling.
 */
class ConcurrentOrderPlacementTest extends AbstractIntegrationTest {

	@Autowired
	private TestDataFactory fixtures;
	@Autowired
	private OrderService orderService;
	@Autowired
	private MenuItemRepository menuItemRepository;
	@Autowired
	private OrderRepository orderRepository;

	@Test
	void onlyOneConcurrentOrderSucceedsWhenStockIsOne() throws InterruptedException {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("10.00"), 1);

		int attempts = 12;
		List<User> customers = new java.util.ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			customers.add(fixtures.createUser(Role.CUSTOMER));
		}

		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch startLine = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger insufficientStockCount = new AtomicInteger();
		List<Future<?>> futures = new java.util.ArrayList<>();

		for (int i = 0; i < attempts; i++) {
			User customer = customers.get(i);
			futures.add(pool.submit(() -> {
				try {
					startLine.await();
					PlaceOrderRequest request = new PlaceOrderRequest(
							restaurant.getId(),
							List.of(new OrderItemRequest(item.getId(), 1)),
							null);
					orderService.placeOrder(customer.getId(), request);
					successCount.incrementAndGet();
				} catch (com.qryde.fooddelivery.exception.InsufficientStockException e) {
					insufficientStockCount.incrementAndGet();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}));
		}

		startLine.countDown();
		for (Future<?> f : futures) {
			try {
				f.get(30, TimeUnit.SECONDS);
			} catch (ExecutionException | TimeoutException e) {
				throw new RuntimeException(e);
			}
		}
		pool.shutdown();

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(insufficientStockCount.get()).isEqualTo(attempts - 1);

		MenuItem reloaded = menuItemRepository.findById(item.getId()).orElseThrow();
		assertThat(reloaded.getStockQuantity()).isEqualTo(0);

		long ordersPersisted = orderRepository.findAll().stream()
				.filter(o -> o.getRestaurant().getId().equals(restaurant.getId()))
				.count();
		assertThat(ordersPersisted).isEqualTo(1);
	}
}

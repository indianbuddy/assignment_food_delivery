package com.qryde.fooddelivery.integration;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.exception.ConflictException;
import com.qryde.fooddelivery.repository.DeliveryPartnerRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import com.qryde.fooddelivery.service.DeliveryPartnerService;
import com.qryde.fooddelivery.service.OrderService;
import com.qryde.fooddelivery.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "partner assignment should handle multiple partners contending
 * for the same order" requirement: many delivery partners race to accept the
 * same eligible order at once. DeliveryPartnerService#acceptAssignment
 * resolves the race with a single conditional UPDATE
 * (OrderRepository#assignPartnerIfUnassigned), so exactly one partner should
 * win no matter how the threads interleave.
 */
class ConcurrentPartnerAssignmentTest extends AbstractIntegrationTest {

	@Autowired
	private TestDataFactory fixtures;
	@Autowired
	private OrderService orderService;
	@Autowired
	private DeliveryPartnerService deliveryPartnerService;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private DeliveryPartnerRepository deliveryPartnerRepository;

	@Test
	void onlyOnePartnerWinsConcurrentAssignment() throws Exception {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("5.00"), 100);
		User customer = fixtures.createUser(Role.CUSTOMER);

		var placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 1)), null));
		orderService.acceptOrder(placed.id(), owner.getId());

		int partnerCount = 10;
		List<DeliveryPartner> partners = new ArrayList<>();
		for (int i = 0; i < partnerCount; i++) {
			User partnerUser = fixtures.createUser(Role.DELIVERY_PARTNER);
			partners.add(fixtures.createDeliveryPartner(city, partnerUser));
		}

		ExecutorService pool = Executors.newFixedThreadPool(partnerCount);
		CountDownLatch startLine = new CountDownLatch(1);
		AtomicInteger wins = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (DeliveryPartner partner : partners) {
			Long partnerUserId = partner.getUser().getId();
			futures.add(pool.submit(() -> {
				try {
					startLine.await();
					deliveryPartnerService.acceptAssignment(placed.id(), partnerUserId);
					wins.incrementAndGet();
				} catch (ConflictException e) {
					conflicts.incrementAndGet();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}));
		}

		startLine.countDown();
		for (Future<?> f : futures) {
			f.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		assertThat(wins.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(partnerCount - 1);

		Order reloaded = orderRepository.findById(placed.id()).orElseThrow();
		assertThat(reloaded.getDeliveryPartner()).isNotNull();
	}

	/**
	 * The flip side of the race above: the same partner can't win two
	 * different orders at once just because both accept-assignment calls
	 * read "AVAILABLE" before either had committed "BUSY". Regression test
	 * for exactly that bug, which code review caught: without a CAS on the
	 * partner's own status (DeliveryPartnerRepository#updateStatusIfCurrent),
	 * assignPartnerIfUnassigned's order-scoped WHERE clause has no way to
	 * know about a double-booked partner.
	 */
	@Test
	void singlePartnerCannotBeAssignedTwoOrdersAtOnce() throws Exception {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		User customer = fixtures.createUser(Role.CUSTOMER);
		User partnerUser = fixtures.createUser(Role.DELIVERY_PARTNER);
		fixtures.createDeliveryPartner(city, partnerUser);

		int orderCount = 8;
		List<Long> orderIds = new ArrayList<>();
		for (int i = 0; i < orderCount; i++) {
			MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("5.00"), 100);
			var placed = orderService.placeOrder(customer.getId(),
					new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 1)), null));
			orderService.acceptOrder(placed.id(), owner.getId());
			orderIds.add(placed.id());
		}

		ExecutorService pool = Executors.newFixedThreadPool(orderCount);
		CountDownLatch startLine = new CountDownLatch(1);
		AtomicInteger wins = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (Long orderId : orderIds) {
			futures.add(pool.submit(() -> {
				try {
					startLine.await();
					deliveryPartnerService.acceptAssignment(orderId, partnerUser.getId());
					wins.incrementAndGet();
				} catch (ConflictException e) {
					conflicts.incrementAndGet();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}));
		}

		startLine.countDown();
		for (Future<?> f : futures) {
			f.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		assertThat(wins.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(orderCount - 1);

		// Compare by the partner's own id (a lazy proxy answers getId() without
		// initializing/needing an open session) rather than walking into
		// getUser(), which would require a live persistence context this
		// plain repository call, outside any transaction, doesn't have.
		Long partnerId = deliveryPartnerRepository.findByUserId(partnerUser.getId()).orElseThrow().getId();
		long ordersAssignedToThisPartner = orderIds.stream()
				.map(id -> orderRepository.findById(id).orElseThrow())
				.filter(o -> o.getDeliveryPartner() != null && o.getDeliveryPartner().getId().equals(partnerId))
				.count();
		assertThat(ordersAssignedToThisPartner).isEqualTo(1);
	}
}

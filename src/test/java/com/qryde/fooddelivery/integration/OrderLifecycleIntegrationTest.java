package com.qryde.fooddelivery.integration;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.dto.rating.RatingRequest;
import com.qryde.fooddelivery.exception.AccessDeniedBusinessException;
import com.qryde.fooddelivery.exception.InsufficientStockException;
import com.qryde.fooddelivery.exception.InvalidStateTransitionException;
import com.qryde.fooddelivery.repository.MenuItemRepository;
import com.qryde.fooddelivery.service.DeliveryPartnerService;
import com.qryde.fooddelivery.service.OrderService;
import com.qryde.fooddelivery.service.RatingService;
import com.qryde.fooddelivery.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end (real Postgres, no mocks) walk through the full order lifecycle:
 * PLACED -> ACCEPTED -> PREPARING -> (partner assigned) -> OUT_FOR_DELIVERY ->
 * DELIVERED -> rated, plus the illegal-transition and ownership guardrails
 * along the way.
 */
class OrderLifecycleIntegrationTest extends AbstractIntegrationTest {

	@Autowired private TestDataFactory fixtures;
	@Autowired private OrderService orderService;
	@Autowired private DeliveryPartnerService deliveryPartnerService;
	@Autowired private RatingService ratingService;
	@Autowired private MenuItemRepository menuItemRepository;

	@Test
	void happyPathThroughEntireLifecycle() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("12.00"), 10);
		User customer = fixtures.createUser(Role.CUSTOMER);
		User partnerUser = fixtures.createUser(Role.DELIVERY_PARTNER);
		DeliveryPartner partner = fixtures.createDeliveryPartner(city, partnerUser);

		OrderResponse placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 3)), null));
		assertThat(placed.status()).isEqualTo("PLACED");
		assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStockQuantity()).isEqualTo(7);

		OrderResponse accepted = orderService.acceptOrder(placed.id(), owner.getId());
		assertThat(accepted.status()).isEqualTo("ACCEPTED");

		OrderResponse preparing = orderService.markPreparing(placed.id(), owner.getId());
		assertThat(preparing.status()).isEqualTo("PREPARING");

		deliveryPartnerService.acceptAssignment(placed.id(), partnerUser.getId());

		OrderResponse outForDelivery = orderService.markOutForDelivery(placed.id(), partnerUser.getId());
		assertThat(outForDelivery.status()).isEqualTo("OUT_FOR_DELIVERY");
		assertThat(outForDelivery.deliveryPartnerId()).isEqualTo(partner.getId());

		OrderResponse delivered = orderService.markDelivered(placed.id(), partnerUser.getId());
		assertThat(delivered.status()).isEqualTo("DELIVERED");

		var rating = ratingService.rate(placed.id(), customer.getId(),
				new RatingRequest(5, 4, "Great food, on time"));
		assertThat(rating.restaurantRating()).isEqualTo(5);

		assertThatThrownBy(() -> ratingService.rate(placed.id(), customer.getId(),
				new RatingRequest(3, 3, "duplicate")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cannotSkipStraightFromPlacedToOutForDelivery() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("8.00"), 5);
		User customer = fixtures.createUser(Role.CUSTOMER);
		User partnerUser = fixtures.createUser(Role.DELIVERY_PARTNER);

		OrderResponse placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 1)), null));

		assertThatThrownBy(() -> orderService.markOutForDelivery(placed.id(), partnerUser.getId()))
				.isInstanceOf(AccessDeniedBusinessException.class);
	}

	@Test
	void rejectingAnOrderRestocksItsItems() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("6.00"), 4);
		User customer = fixtures.createUser(Role.CUSTOMER);

		OrderResponse placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 4)), null));
		assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStockQuantity()).isZero();

		OrderResponse rejected = orderService.rejectOrder(placed.id(), owner.getId());
		assertThat(rejected.status()).isEqualTo("REJECTED");
		assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
	}

	@Test
	void cannotMarkPreparingBeforeRestaurantAccepts() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("6.00"), 4);
		User customer = fixtures.createUser(Role.CUSTOMER);

		OrderResponse placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 1)), null));

		assertThatThrownBy(() -> orderService.markPreparing(placed.id(), owner.getId()))
				.isInstanceOf(InvalidStateTransitionException.class);
	}

	@Test
	void placingOrderWithMoreThanAvailableStockFailsAndReservesNothing() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("6.00"), 2);
		User customer = fixtures.createUser(Role.CUSTOMER);

		assertThatThrownBy(() -> orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 5)), null)))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
	}

	/**
	 * Regression test for an IDOR code review caught: a rating is order-linked
	 * data, so it must only be visible to the parties who could see the order
	 * itself (RatingService#getForOrder now delegates to
	 * OrderService#getForViewer instead of skipping access control entirely).
	 */
	@Test
	void unrelatedCustomerCannotReadAnotherCustomersOrderRating() {
		City city = fixtures.createCity();
		User owner = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurant = fixtures.createRestaurant(city, owner);
		MenuItem item = fixtures.createMenuItem(restaurant, new BigDecimal("9.00"), 5);
		User customer = fixtures.createUser(Role.CUSTOMER);
		User partnerUser = fixtures.createUser(Role.DELIVERY_PARTNER);
		fixtures.createDeliveryPartner(city, partnerUser);
		User unrelatedCustomer = fixtures.createUser(Role.CUSTOMER);

		OrderResponse placed = orderService.placeOrder(customer.getId(),
				new PlaceOrderRequest(restaurant.getId(), List.of(new OrderItemRequest(item.getId(), 1)), null));
		orderService.acceptOrder(placed.id(), owner.getId());
		orderService.markPreparing(placed.id(), owner.getId());
		deliveryPartnerService.acceptAssignment(placed.id(), partnerUser.getId());
		orderService.markOutForDelivery(placed.id(), partnerUser.getId());
		orderService.markDelivered(placed.id(), partnerUser.getId());
		ratingService.rate(placed.id(), customer.getId(), new RatingRequest(5, 5, "great"));

		assertThatThrownBy(() -> ratingService.getForOrder(placed.id(), unrelatedCustomer.getId(), Role.CUSTOMER))
				.isInstanceOf(AccessDeniedBusinessException.class);

		// The customer who actually placed and rated the order can still read it.
		assertThat(ratingService.getForOrder(placed.id(), customer.getId(), Role.CUSTOMER).restaurantRating())
				.isEqualTo(5);
	}

	/**
	 * Regression test for a cross-tenant IDOR code review caught: restaurant
	 * owner A must not be able to list restaurant owner B's ratings just by
	 * passing B's restaurant id (RatingService#listForRestaurant now checks
	 * ownership the same way every sibling owner-scoped method does).
	 */
	@Test
	void restaurantOwnerCannotListAnotherOwnersRatings() {
		City city = fixtures.createCity();
		User ownerA = fixtures.createUser(Role.RESTAURANT_OWNER);
		User ownerB = fixtures.createUser(Role.RESTAURANT_OWNER);
		Restaurant restaurantB = fixtures.createRestaurant(city, ownerB);

		assertThatThrownBy(() -> ratingService.listForRestaurant(restaurantB.getId(), ownerA.getId()))
				.isInstanceOf(AccessDeniedBusinessException.class);
	}
}

package com.qryde.fooddelivery.util;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Builds real, persisted fixtures for integration tests via the actual
 * repositories - no mocking - so tests exercise the same schema/constraints
 * the app runs against.
 */
@Component
@RequiredArgsConstructor
public class TestDataFactory {

	private final UserRepository userRepository;
	private final CityRepository cityRepository;
	private final RestaurantRepository restaurantRepository;
	private final MenuItemRepository menuItemRepository;
	private final DeliveryPartnerRepository deliveryPartnerRepository;
	private final PasswordEncoder passwordEncoder;

	private int counter = 0;

	public User createUser(Role role) {
		int n = ++counter;
		User user = User.builder()
				.email("user" + n + "-" + role.name().toLowerCase() + "@test.qryde.com")
				.passwordHash(passwordEncoder.encode("Password123!"))
				.fullName("Test User " + n)
				.phone("+1000000" + n)
				.role(role)
				.active(true)
				.build();
		return userRepository.save(user);
	}

	public City createCity() {
		int n = ++counter;
		City city = City.builder().name("Test City " + n).active(true).build();
		return cityRepository.save(city);
	}

	public Restaurant createRestaurant(City city, User owner) {
		int n = ++counter;
		Restaurant restaurant = Restaurant.builder()
				.name("Test Restaurant " + n)
				.address("123 Test St")
				.city(city)
				.owner(owner)
				.active(true)
				.build();
		return restaurantRepository.save(restaurant);
	}

	public MenuItem createMenuItem(Restaurant restaurant, BigDecimal price, int stock) {
		int n = ++counter;
		MenuItem item = MenuItem.builder()
				.restaurant(restaurant)
				.name("Test Item " + n)
				.description("")
				.price(price)
				.stockQuantity(stock)
				.available(true)
				.build();
		return menuItemRepository.save(item);
	}

	public DeliveryPartner createDeliveryPartner(City city, User user) {
		DeliveryPartner partner = DeliveryPartner.builder()
				.user(user)
				.city(city)
				.status(PartnerStatus.AVAILABLE)
				.build();
		return deliveryPartnerRepository.save(partner);
	}
}

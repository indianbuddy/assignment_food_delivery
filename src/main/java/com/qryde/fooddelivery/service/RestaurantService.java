package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.City;
import com.qryde.fooddelivery.domain.Restaurant;
import com.qryde.fooddelivery.domain.Role;
import com.qryde.fooddelivery.domain.User;
import com.qryde.fooddelivery.dto.restaurant.RestaurantRequest;
import com.qryde.fooddelivery.dto.restaurant.RestaurantResponse;
import com.qryde.fooddelivery.exception.AccessDeniedBusinessException;
import com.qryde.fooddelivery.exception.ResourceNotFoundException;
import com.qryde.fooddelivery.repository.RestaurantRepository;
import com.qryde.fooddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

	private final RestaurantRepository restaurantRepository;
	private final UserRepository userRepository;
	private final CityService cityService;

	@Transactional
	public RestaurantResponse create(RestaurantRequest request) {
		City city = cityService.findEntity(request.cityId());

		User owner = userRepository.findById(request.ownerId())
				.orElseThrow(() -> new ResourceNotFoundException("User " + request.ownerId() + " not found"));
		if (owner.getRole() != Role.RESTAURANT_OWNER) {
			throw new IllegalArgumentException("User " + owner.getId() + " is not a restaurant owner");
		}

		Restaurant restaurant = Restaurant.builder()
				.name(request.name())
				.address(request.address())
				.city(city)
				.owner(owner)
				.active(true)
				.build();

		return toResponse(restaurantRepository.save(restaurant));
	}

	public List<RestaurantResponse> listByCity(Long cityId) {
		return restaurantRepository.findByCityIdAndActiveTrue(cityId).stream().map(this::toResponse).toList();
	}

	public RestaurantResponse get(Long id) {
		return toResponse(findEntity(id));
	}

	public List<RestaurantResponse> listOwnedBy(Long ownerId) {
		return restaurantRepository.findByOwnerId(ownerId).stream().map(this::toResponse).toList();
	}

	@Transactional
	public RestaurantResponse setActive(Long id, boolean active) {
		Restaurant restaurant = findEntity(id);
		restaurant.setActive(active);
		return toResponse(restaurantRepository.save(restaurant));
	}

	Restaurant findEntity(Long id) {
		return restaurantRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Restaurant " + id + " not found"));
	}

	void assertOwnership(Restaurant restaurant, Long userId) {
		if (!restaurant.getOwner().getId().equals(userId)) {
			throw new AccessDeniedBusinessException("You do not own this restaurant");
		}
	}

	private RestaurantResponse toResponse(Restaurant restaurant) {
		return new RestaurantResponse(
				restaurant.getId(),
				restaurant.getName(),
				restaurant.getAddress(),
				restaurant.getCity().getId(),
				restaurant.getCity().getName(),
				restaurant.getOwner().getId(),
				restaurant.isActive()
		);
	}
}

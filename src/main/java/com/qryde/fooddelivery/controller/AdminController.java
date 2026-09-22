package com.qryde.fooddelivery.controller;

import com.qryde.fooddelivery.dto.city.CityRequest;
import com.qryde.fooddelivery.dto.city.CityResponse;
import com.qryde.fooddelivery.dto.partner.DeliveryPartnerResponse;
import com.qryde.fooddelivery.dto.partner.PartnerRegisterRequest;
import com.qryde.fooddelivery.dto.restaurant.RestaurantRequest;
import com.qryde.fooddelivery.dto.restaurant.RestaurantResponse;
import com.qryde.fooddelivery.service.CityService;
import com.qryde.fooddelivery.service.DeliveryPartnerService;
import com.qryde.fooddelivery.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only surface: cities, restaurants and delivery partners are
 * onboarded/managed by an admin per the role definitions in the brief.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

	private final CityService cityService;
	private final RestaurantService restaurantService;
	private final DeliveryPartnerService deliveryPartnerService;

	@PostMapping("/cities")
	public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(cityService.create(request));
	}

	@GetMapping("/cities")
	public List<CityResponse> listAllCities() {
		return cityService.listAll();
	}

	@PatchMapping("/cities/{id}/active")
	public CityResponse setCityActive(@PathVariable Long id, @RequestParam boolean active) {
		return cityService.setActive(id, active);
	}

	@PostMapping("/restaurants")
	public ResponseEntity<RestaurantResponse> createRestaurant(@Valid @RequestBody RestaurantRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.create(request));
	}

	@PatchMapping("/restaurants/{id}/active")
	public RestaurantResponse setRestaurantActive(@PathVariable Long id, @RequestParam boolean active) {
		return restaurantService.setActive(id, active);
	}

	@PostMapping("/delivery-partners")
	public ResponseEntity<DeliveryPartnerResponse> registerPartner(@Valid @RequestBody PartnerRegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(deliveryPartnerService.register(request));
	}
}

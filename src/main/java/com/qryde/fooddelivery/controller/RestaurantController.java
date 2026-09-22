package com.qryde.fooddelivery.controller;

import com.qryde.fooddelivery.dto.menuitem.MenuItemResponse;
import com.qryde.fooddelivery.dto.restaurant.RestaurantResponse;
import com.qryde.fooddelivery.service.MenuItemService;
import com.qryde.fooddelivery.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

	private final RestaurantService restaurantService;
	private final MenuItemService menuItemService;

	@GetMapping
	public List<RestaurantResponse> listByCity(@RequestParam Long cityId) {
		return restaurantService.listByCity(cityId);
	}

	@GetMapping("/{id}")
	public RestaurantResponse get(@PathVariable Long id) {
		return restaurantService.get(id);
	}

	@GetMapping("/{id}/menu-items")
	public List<MenuItemResponse> menu(@PathVariable Long id) {
		return menuItemService.listAvailableForRestaurant(id);
	}
}

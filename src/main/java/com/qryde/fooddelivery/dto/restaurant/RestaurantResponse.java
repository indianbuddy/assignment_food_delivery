package com.qryde.fooddelivery.dto.restaurant;

public record RestaurantResponse(
		Long id,
		String name,
		String address,
		Long cityId,
		String cityName,
		Long ownerId,
		boolean active
) {
}

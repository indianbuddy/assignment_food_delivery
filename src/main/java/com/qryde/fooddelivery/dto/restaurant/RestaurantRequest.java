package com.qryde.fooddelivery.dto.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RestaurantRequest(
		@NotBlank String name,
		@NotBlank String address,
		@NotNull Long cityId,
		@NotNull Long ownerId
) {
}

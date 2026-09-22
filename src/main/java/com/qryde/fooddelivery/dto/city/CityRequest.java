package com.qryde.fooddelivery.dto.city;

import jakarta.validation.constraints.NotBlank;

public record CityRequest(@NotBlank String name) {
}

package com.qryde.fooddelivery.dto.menuitem;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MenuItemRequest(
		@NotBlank String name,
		String description,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
		@NotNull @Min(0) Integer stockQuantity
) {
}

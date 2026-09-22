package com.qryde.fooddelivery.dto.menuitem;

import java.math.BigDecimal;

public record MenuItemResponse(
		Long id,
		Long restaurantId,
		String name,
		String description,
		BigDecimal price,
		Integer stockQuantity,
		boolean available
) {
}

package com.qryde.fooddelivery.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
		Long menuItemId,
		String menuItemName,
		Integer quantity,
		BigDecimal priceAtOrder
) {
}

package com.qryde.fooddelivery.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
		Long id,
		Long customerId,
		Long restaurantId,
		String restaurantName,
		Long deliveryPartnerId,
		String status,
		BigDecimal totalAmount,
		List<OrderItemResponse> items,
		Instant createdAt,
		Instant updatedAt
) {
}

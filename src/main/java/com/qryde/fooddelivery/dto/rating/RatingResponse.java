package com.qryde.fooddelivery.dto.rating;

import java.time.Instant;

public record RatingResponse(
		Long id,
		Long orderId,
		Integer restaurantRating,
		Integer deliveryRating,
		String comment,
		Instant createdAt
) {
}

package com.qryde.fooddelivery.dto.partner;

public record DeliveryPartnerResponse(
		Long id,
		Long userId,
		String fullName,
		Long cityId,
		String cityName,
		String status
) {
}

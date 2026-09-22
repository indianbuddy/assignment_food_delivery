package com.qryde.fooddelivery.dto.partner;

import jakarta.validation.constraints.NotNull;

public record PartnerRegisterRequest(
		@NotNull Long userId,
		@NotNull Long cityId
) {
}

package com.qryde.fooddelivery.dto.auth;

public record AuthResponse(
		String token,
		Long userId,
		String email,
		String role
) {
}

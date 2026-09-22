package com.qryde.fooddelivery.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

	private final JwtService jwtService = new JwtService(
			"unit-test-secret-key-needs-to-be-long-enough-for-hmac-sha", 60_000L);

	@Test
	void generatesTokenThatRoundTripsClaims() {
		String token = jwtService.generateToken(42L, "owner@test.com", "RESTAURANT_OWNER");

		assertThat(jwtService.isValid(token)).isTrue();
		assertThat(jwtService.extractEmail(token)).isEqualTo("owner@test.com");

		Claims claims = jwtService.parseClaims(token);
		assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
		assertThat(claims.get("role", String.class)).isEqualTo("RESTAURANT_OWNER");
	}

	@Test
	void expiredTokenIsNotValid() {
		JwtService shortLived = new JwtService(
				"unit-test-secret-key-needs-to-be-long-enough-for-hmac-sha", -1000L);
		String token = shortLived.generateToken(1L, "a@test.com", "CUSTOMER");

		assertThat(shortLived.isValid(token)).isFalse();
	}

	@Test
	void garbageTokenIsNotValid() {
		assertThat(jwtService.isValid("not-a-real-jwt")).isFalse();
	}
}

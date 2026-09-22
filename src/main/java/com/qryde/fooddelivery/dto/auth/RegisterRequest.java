package com.qryde.fooddelivery.dto.auth;

import com.qryde.fooddelivery.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
		@NotBlank String fullName,
		@NotBlank String phone,
		@NotNull Role role
) {
}

package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.User;
import com.qryde.fooddelivery.dto.auth.AuthResponse;
import com.qryde.fooddelivery.dto.auth.LoginRequest;
import com.qryde.fooddelivery.dto.auth.RegisterRequest;
import com.qryde.fooddelivery.repository.UserRepository;
import com.qryde.fooddelivery.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new IllegalArgumentException("An account with this email already exists");
		}

		User user = User.builder()
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.fullName(request.fullName())
				.phone(request.phone())
				.role(request.role())
				.active(true)
				.build();

		user = userRepository.save(user);
		String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
		return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name());
	}

	public AuthResponse login(LoginRequest request) {
		try {
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.email(), request.password()));
		} catch (org.springframework.security.core.AuthenticationException ex) {
			throw new BadCredentialsException("Invalid email or password");
		}

		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

		String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
		return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name());
	}
}

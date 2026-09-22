package com.qryde.fooddelivery.config;

import com.qryde.fooddelivery.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Anonymous auth is disabled so a missing/invalid token fails
				// authentication (401 via the entry point below) instead of
				// succeeding as an anonymous principal that only then fails
				// @PreAuthorize's role check (which would surface as a
				// misleading 403).
				.anonymous(anonymous -> anonymous.disable())
				.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
						response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/cities/**", "/api/restaurants/**", "/api/menu-items/**").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						// Static assets for the demo UI (see README - out of scope
						// per the assignment, added anyway for the video demo; the
						// UI itself calls the same JWT-protected /api/** endpoints
						// as everyone else, it gets no special access).
						.requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/styles.css", "/favicon.ico").permitAll()
						.anyRequest().authenticated()
				)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}

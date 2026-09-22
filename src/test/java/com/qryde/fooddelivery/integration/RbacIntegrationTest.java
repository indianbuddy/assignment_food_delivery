package com.qryde.fooddelivery.integration;

import com.qryde.fooddelivery.domain.Role;
import com.qryde.fooddelivery.domain.User;
import com.qryde.fooddelivery.dto.auth.AuthResponse;
import com.qryde.fooddelivery.dto.auth.RegisterRequest;
import com.qryde.fooddelivery.dto.city.CityRequest;
import com.qryde.fooddelivery.dto.order.OrderItemRequest;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.security.JwtService;
import com.qryde.fooddelivery.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real HTTP + Spring Security filter chain end to end (as
 * opposed to the service-layer unit tests): registers a user of each role,
 * logs in for a JWT, and confirms role-gated endpoints actually enforce
 * their @PreAuthorize boundaries rather than just looking correct on paper.
 */
class RbacIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;
	@Autowired
	private TestDataFactory fixtures;
	@Autowired
	private JwtService jwtService;

	@Test
	void selfRegisteringAsAdminIsRejected() {
		RegisterRequest request = new RegisterRequest(
				"wannabe-admin-" + System.nanoTime() + "@test.qryde.com",
				"Password123!", "Sneaky", "+10000000", Role.ADMIN);

		ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void adminOnlyEndpointRejectsOtherRolesAndAnonymous() {
		// Admins are never self-registered (see AuthService#register) - create
		// one directly, the way the real bootstrap mechanism would.
		User adminUser = fixtures.createUser(Role.ADMIN);
		String adminToken = jwtService.generateToken(adminUser.getId(), adminUser.getEmail(), Role.ADMIN.name());
		String customerToken = registerAndLogin(Role.CUSTOMER);

		CityRequest body = new CityRequest("Metropolis");

		ResponseEntity<String> anonymous = restTemplate.postForEntity(
				"/api/admin/cities", new HttpEntity<>(body, jsonHeaders(null)), String.class);
		assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> asCustomer = restTemplate.postForEntity(
				"/api/admin/cities", new HttpEntity<>(body, jsonHeaders(customerToken)), String.class);
		assertThat(asCustomer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<String> asAdmin = restTemplate.postForEntity(
				"/api/admin/cities", new HttpEntity<>(body, jsonHeaders(adminToken)), String.class);
		assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void customerOnlyEndpointRejectsRestaurantOwner() {
		String ownerToken = registerAndLogin(Role.RESTAURANT_OWNER);

		// Body must pass @Valid shape checks (restaurantId/items present) so the
		// request actually reaches @PreAuthorize's role check - argument
		// resolution/validation runs before the method (and its security
		// advice) is invoked, so a malformed body would 400 before the role
		// check ever gets a chance to run.
		PlaceOrderRequest body = new PlaceOrderRequest(1L, List.of(new OrderItemRequest(1L, 1)), null);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/orders", new HttpEntity<>(body, jsonHeaders(ownerToken)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void publicBrowseEndpointsRequireNoAuth() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/cities", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private String registerAndLogin(Role role) {
		String email = "rbac-" + role.name().toLowerCase() + "-" + System.nanoTime() + "@test.qryde.com";
		RegisterRequest request = new RegisterRequest(email, "Password123!", "RBAC Test", "+10000000", role);
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
				"/api/auth/register", request, AuthResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().token();
	}

	private HttpHeaders jsonHeaders(String bearerToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (bearerToken != null) {
			headers.setBearerAuth(bearerToken);
		}
		return headers;
	}
}

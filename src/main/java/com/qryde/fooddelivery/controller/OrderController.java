package com.qryde.fooddelivery.controller;

import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.order.PlaceOrderRequest;
import com.qryde.fooddelivery.dto.rating.RatingRequest;
import com.qryde.fooddelivery.dto.rating.RatingResponse;
import com.qryde.fooddelivery.security.UserPrincipal;
import com.qryde.fooddelivery.service.OrderService;
import com.qryde.fooddelivery.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;
	private final RatingService ratingService;

	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal UserPrincipal principal,
												@Valid @RequestBody PlaceOrderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(principal.getId(), request));
	}

	@GetMapping("/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	public List<OrderResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
		return orderService.listForCustomer(principal.getId());
	}

	@GetMapping("/{id}")
	public OrderResponse track(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.getForViewer(id, principal.getId(), principal.getUser().getRole());
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasRole('CUSTOMER')")
	public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.cancelOrder(id, principal.getId());
	}

	@PostMapping("/{id}/rating")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<RatingResponse> rate(@PathVariable Long id,
												@AuthenticationPrincipal UserPrincipal principal,
												@Valid @RequestBody RatingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(id, principal.getId(), request));
	}

	@GetMapping("/{id}/rating")
	public RatingResponse getRating(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		return ratingService.getForOrder(id, principal.getId(), principal.getUser().getRole());
	}
}

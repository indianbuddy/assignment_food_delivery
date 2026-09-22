package com.qryde.fooddelivery.controller;

import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.partner.DeliveryPartnerResponse;
import com.qryde.fooddelivery.dto.partner.PartnerStatusUpdateRequest;
import com.qryde.fooddelivery.security.UserPrincipal;
import com.qryde.fooddelivery.service.DeliveryPartnerService;
import com.qryde.fooddelivery.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Delivery-partner self-service: browse unclaimed orders in your city,
 * race to accept one, and update your own live/order status. The "multiple
 * partners contending for the same order" requirement is resolved entirely
 * inside DeliveryPartnerService#acceptAssignment via an atomic conditional
 * update - this controller just surfaces the resulting 200 (won) or 409
 * (someone else already claimed it).
 */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
public class DeliveryController {

	private final DeliveryPartnerService deliveryPartnerService;
	private final OrderService orderService;

	@PatchMapping("/me/status")
	public DeliveryPartnerResponse updateStatus(@AuthenticationPrincipal UserPrincipal principal,
												 @Valid @RequestBody PartnerStatusUpdateRequest request) {
		return deliveryPartnerService.updateStatus(principal.getId(), request.status());
	}

	@GetMapping("/available-orders")
	public List<OrderResponse> availableOrders(@AuthenticationPrincipal UserPrincipal principal) {
		return deliveryPartnerService.listAvailableOrders(principal.getId());
	}

	@PostMapping("/orders/{orderId}/accept")
	public void acceptAssignment(@PathVariable Long orderId, @AuthenticationPrincipal UserPrincipal principal) {
		deliveryPartnerService.acceptAssignment(orderId, principal.getId());
	}

	@GetMapping("/orders/mine")
	public List<OrderResponse> myAssignments(@AuthenticationPrincipal UserPrincipal principal) {
		return deliveryPartnerService.listMyAssignments(principal.getId());
	}

	@PostMapping("/orders/{orderId}/out-for-delivery")
	public OrderResponse outForDelivery(@PathVariable Long orderId, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.markOutForDelivery(orderId, principal.getId());
	}

	@PostMapping("/orders/{orderId}/delivered")
	public OrderResponse delivered(@PathVariable Long orderId, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.markDelivered(orderId, principal.getId());
	}
}

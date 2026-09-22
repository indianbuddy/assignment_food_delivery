package com.qryde.fooddelivery.controller;

import com.qryde.fooddelivery.dto.menuitem.MenuItemRequest;
import com.qryde.fooddelivery.dto.menuitem.MenuItemResponse;
import com.qryde.fooddelivery.dto.menuitem.StockAdjustmentRequest;
import com.qryde.fooddelivery.dto.order.OrderResponse;
import com.qryde.fooddelivery.dto.rating.RatingResponse;
import com.qryde.fooddelivery.dto.restaurant.RestaurantResponse;
import com.qryde.fooddelivery.security.UserPrincipal;
import com.qryde.fooddelivery.service.MenuItemService;
import com.qryde.fooddelivery.service.OrderService;
import com.qryde.fooddelivery.service.RatingService;
import com.qryde.fooddelivery.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Everything a restaurant owner manages for their own restaurant(s): menu and
 * the accept/reject/preparing steps of the order lifecycle. Ownership is
 * re-checked in the service layer against the authenticated user's id, not
 * just the ADMIN/RESTAURANT_OWNER role, so one owner can't act on another
 * owner's restaurant.
 */
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class RestaurantOwnerController {

	private final RestaurantService restaurantService;
	private final MenuItemService menuItemService;
	private final OrderService orderService;
	private final RatingService ratingService;

	@GetMapping("/restaurants")
	public List<RestaurantResponse> myRestaurants(@AuthenticationPrincipal UserPrincipal principal) {
		return restaurantService.listOwnedBy(principal.getId());
	}

	@PostMapping("/restaurants/{restaurantId}/menu-items")
	public ResponseEntity<MenuItemResponse> addMenuItem(@PathVariable Long restaurantId,
														 @AuthenticationPrincipal UserPrincipal principal,
														 @Valid @RequestBody MenuItemRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(menuItemService.create(restaurantId, principal.getId(), request));
	}

	@GetMapping("/restaurants/{restaurantId}/menu-items")
	public List<MenuItemResponse> myMenuItems(@PathVariable Long restaurantId,
											   @AuthenticationPrincipal UserPrincipal principal) {
		return menuItemService.listAllForOwner(restaurantId, principal.getId());
	}

	@PutMapping("/menu-items/{itemId}")
	public MenuItemResponse updateMenuItem(@PathVariable Long itemId,
											@AuthenticationPrincipal UserPrincipal principal,
											@Valid @RequestBody MenuItemRequest request) {
		return menuItemService.update(itemId, principal.getId(), request);
	}

	@PatchMapping("/menu-items/{itemId}/availability")
	public MenuItemResponse setAvailability(@PathVariable Long itemId,
											 @AuthenticationPrincipal UserPrincipal principal,
											 @RequestParam boolean available) {
		return menuItemService.setAvailability(itemId, principal.getId(), available);
	}

	@PatchMapping("/menu-items/{itemId}/stock")
	public MenuItemResponse adjustStock(@PathVariable Long itemId,
										 @AuthenticationPrincipal UserPrincipal principal,
										 @Valid @RequestBody StockAdjustmentRequest request) {
		return menuItemService.adjustStock(itemId, principal.getId(), request);
	}

	@GetMapping("/restaurants/{restaurantId}/orders")
	public List<OrderResponse> restaurantOrders(@PathVariable Long restaurantId,
												 @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.listForRestaurantOwner(restaurantId, principal.getId());
	}

	@PostMapping("/orders/{orderId}/accept")
	public OrderResponse acceptOrder(@PathVariable Long orderId, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.acceptOrder(orderId, principal.getId());
	}

	@PostMapping("/orders/{orderId}/reject")
	public OrderResponse rejectOrder(@PathVariable Long orderId, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.rejectOrder(orderId, principal.getId());
	}

	@PostMapping("/orders/{orderId}/preparing")
	public OrderResponse markPreparing(@PathVariable Long orderId, @AuthenticationPrincipal UserPrincipal principal) {
		return orderService.markPreparing(orderId, principal.getId());
	}

	@GetMapping("/restaurants/{restaurantId}/ratings")
	public List<RatingResponse> ratings(@PathVariable Long restaurantId) {
		return ratingService.listForRestaurant(restaurantId);
	}
}

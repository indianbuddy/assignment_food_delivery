package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.MenuItem;
import com.qryde.fooddelivery.domain.Restaurant;
import com.qryde.fooddelivery.dto.menuitem.MenuItemRequest;
import com.qryde.fooddelivery.dto.menuitem.MenuItemResponse;
import com.qryde.fooddelivery.dto.menuitem.StockAdjustmentRequest;
import com.qryde.fooddelivery.exception.ResourceNotFoundException;
import com.qryde.fooddelivery.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuItemService {

	private final MenuItemRepository menuItemRepository;
	private final RestaurantService restaurantService;

	@Transactional
	public MenuItemResponse create(Long restaurantId, Long ownerId, MenuItemRequest request) {
		Restaurant restaurant = restaurantService.findEntity(restaurantId);
		restaurantService.assertOwnership(restaurant, ownerId);

		MenuItem item = MenuItem.builder()
				.restaurant(restaurant)
				.name(request.name())
				.description(request.description() == null ? "" : request.description())
				.price(request.price())
				.stockQuantity(request.stockQuantity())
				.available(true)
				.build();

		return toResponse(menuItemRepository.save(item));
	}

	public List<MenuItemResponse> listAvailableForRestaurant(Long restaurantId) {
		return menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId).stream()
				.map(this::toResponse).toList();
	}

	public List<MenuItemResponse> listAllForOwner(Long restaurantId, Long ownerId) {
		Restaurant restaurant = restaurantService.findEntity(restaurantId);
		restaurantService.assertOwnership(restaurant, ownerId);
		return menuItemRepository.findByRestaurantId(restaurantId).stream().map(this::toResponse).toList();
	}

	@Transactional
	public MenuItemResponse update(Long itemId, Long ownerId, MenuItemRequest request) {
		MenuItem item = findEntity(itemId);
		restaurantService.assertOwnership(item.getRestaurant(), ownerId);

		item.setName(request.name());
		item.setDescription(request.description() == null ? "" : request.description());
		item.setPrice(request.price());
		item.setStockQuantity(request.stockQuantity());

		return toResponse(menuItemRepository.save(item));
	}

	@Transactional
	public MenuItemResponse setAvailability(Long itemId, Long ownerId, boolean available) {
		MenuItem item = findEntity(itemId);
		restaurantService.assertOwnership(item.getRestaurant(), ownerId);
		item.setAvailable(available);
		return toResponse(menuItemRepository.save(item));
	}

	@Transactional
	public MenuItemResponse adjustStock(Long itemId, Long ownerId, StockAdjustmentRequest request) {
		MenuItem item = findEntity(itemId);
		restaurantService.assertOwnership(item.getRestaurant(), ownerId);

		int newQuantity = item.getStockQuantity() + request.delta();
		if (newQuantity < 0) {
			throw new IllegalArgumentException("Resulting stock cannot be negative");
		}
		item.setStockQuantity(newQuantity);
		return toResponse(menuItemRepository.save(item));
	}

	MenuItem findEntity(Long id) {
		return menuItemRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Menu item " + id + " not found"));
	}

	private MenuItemResponse toResponse(MenuItem item) {
		return new MenuItemResponse(
				item.getId(),
				item.getRestaurant().getId(),
				item.getName(),
				item.getDescription(),
				item.getPrice(),
				item.getStockQuantity(),
				item.isAvailable()
		);
	}
}

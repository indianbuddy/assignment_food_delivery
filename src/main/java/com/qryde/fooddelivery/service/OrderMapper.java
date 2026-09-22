package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.Order;
import com.qryde.fooddelivery.domain.OrderItem;
import com.qryde.fooddelivery.dto.order.OrderItemResponse;
import com.qryde.fooddelivery.dto.order.OrderResponse;

final class OrderMapper {

	private OrderMapper() {
	}

	static OrderResponse toResponse(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getCustomer().getId(),
				order.getRestaurant().getId(),
				order.getRestaurant().getName(),
				order.getDeliveryPartner() == null ? null : order.getDeliveryPartner().getId(),
				order.getStatus().name(),
				order.getTotalAmount(),
				order.getItems().stream().map(OrderMapper::toItemResponse).toList(),
				order.getCreatedAt(),
				order.getUpdatedAt()
		);
	}

	private static OrderItemResponse toItemResponse(OrderItem item) {
		return new OrderItemResponse(
				item.getMenuItem().getId(),
				item.getMenuItemName(),
				item.getQuantity(),
				item.getPriceAtOrder()
		);
	}
}

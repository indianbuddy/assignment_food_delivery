package com.qryde.fooddelivery.event;

import com.qryde.fooddelivery.domain.OrderStatus;

public record OrderStatusChangedEvent(Long orderId, OrderStatus previousStatus, OrderStatus newStatus) {
}

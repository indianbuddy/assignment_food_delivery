package com.qryde.fooddelivery.dto.menuitem;

import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(@NotNull Integer delta) {
}

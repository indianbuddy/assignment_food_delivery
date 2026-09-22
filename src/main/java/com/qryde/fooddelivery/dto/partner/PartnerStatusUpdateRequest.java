package com.qryde.fooddelivery.dto.partner;

import com.qryde.fooddelivery.domain.PartnerStatus;
import jakarta.validation.constraints.NotNull;

public record PartnerStatusUpdateRequest(@NotNull PartnerStatus status) {
}

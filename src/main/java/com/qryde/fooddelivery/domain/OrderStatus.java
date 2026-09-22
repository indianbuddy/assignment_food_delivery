package com.qryde.fooddelivery.domain;

import java.util.Set;

public enum OrderStatus {
	PLACED(Set.of("ACCEPTED", "REJECTED", "CANCELLED")),
	ACCEPTED(Set.of("PREPARING", "CANCELLED")),
	PREPARING(Set.of("OUT_FOR_DELIVERY", "CANCELLED")),
	OUT_FOR_DELIVERY(Set.of("DELIVERED")),
	DELIVERED(Set.of()),
	REJECTED(Set.of()),
	CANCELLED(Set.of());

	private final Set<String> allowedNext;

	OrderStatus(Set<String> allowedNext) {
		this.allowedNext = allowedNext;
	}

	public boolean canTransitionTo(OrderStatus target) {
		return allowedNext.contains(target.name());
	}

	public boolean isTerminal() {
		return allowedNext.isEmpty();
	}
}

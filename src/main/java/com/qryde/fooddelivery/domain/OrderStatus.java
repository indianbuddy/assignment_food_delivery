package com.qryde.fooddelivery.domain;

import java.util.Set;

public enum OrderStatus {
	PLACED(Set.of("ACCEPTED", "REJECTED", "CANCELLED")),
	ACCEPTED(Set.of("PREPARING", "CANCELLED")),
	// No CANCELLED here deliberately: once a restaurant starts preparing an
	// order there is no cancellation path in this system (customer
	// self-cancel is only offered pre-preparation - see OrderService#cancelOrder).
	// Keeping this table's edges limited to what's actually reachable is what
	// lets cancelOrder rely on it as the single source of truth instead of
	// hand-duplicating the same restriction.
	PREPARING(Set.of("OUT_FOR_DELIVERY")),
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

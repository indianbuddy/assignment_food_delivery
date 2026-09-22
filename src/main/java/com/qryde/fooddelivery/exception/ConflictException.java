package com.qryde.fooddelivery.exception;

/**
 * Raised when an atomic conditional update (CAS) affects zero rows because
 * someone else won the race first - e.g. a delivery partner assignment that
 * another partner already claimed.
 */
public class ConflictException extends RuntimeException {
	public ConflictException(String message) {
		super(message);
	}
}

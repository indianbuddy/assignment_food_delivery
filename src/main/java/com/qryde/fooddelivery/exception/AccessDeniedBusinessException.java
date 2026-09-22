package com.qryde.fooddelivery.exception;

/**
 * Ownership-level authorization failures (e.g. a restaurant owner touching a
 * restaurant they don't own) - distinct from role-based 403s that Spring
 * Security's @PreAuthorize already handles at the method boundary.
 */
public class AccessDeniedBusinessException extends RuntimeException {
	public AccessDeniedBusinessException(String message) {
		super(message);
	}
}

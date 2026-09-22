package com.qryde.fooddelivery.exception;

public class ResourceNotFoundException extends RuntimeException {
	public ResourceNotFoundException(String message) {
		super(message);
	}
}

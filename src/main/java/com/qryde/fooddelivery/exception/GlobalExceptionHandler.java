package com.qryde.fooddelivery.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
	}

	@ExceptionHandler(InsufficientStockException.class)
	public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), req);
	}

	@ExceptionHandler(InvalidStateTransitionException.class)
	public ResponseEntity<ApiError> handleInvalidTransition(InvalidStateTransitionException ex, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), req);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), req);
	}

	@ExceptionHandler(AccessDeniedBusinessException.class)
	public ResponseEntity<ApiError> handleBusinessAccessDenied(AccessDeniedBusinessException ex, HttpServletRequest req) {
		return build(HttpStatus.FORBIDDEN, ex.getMessage(), req);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
		return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", req);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
		return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", req);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
		Map<String, String> fieldErrors = new HashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(fe ->
				fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
		ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request",
				"Validation failed", req.getRequestURI(), fieldErrors);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", req);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
		ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, req.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}

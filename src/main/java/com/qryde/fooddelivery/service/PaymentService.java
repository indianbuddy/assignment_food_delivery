package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.Order;
import com.qryde.fooddelivery.domain.Payment;
import com.qryde.fooddelivery.domain.PaymentStatus;
import com.qryde.fooddelivery.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stand-in for a real payment gateway (out of scope per the assignment).
 * Deliberately synchronous and called from within the placing transaction so
 * that stock, order and payment either all commit or all roll back together -
 * a real gateway call would instead go through a reservation/capture flow with
 * an idempotency key and a saga/outbox, but that's beyond what this exercise
 * asks for.
 *
 * Test hook: passing method "FAIL_FOR_TEST" deterministically simulates a
 * declined payment, so integration tests can assert the whole transaction
 * (including the stock decrement) rolls back.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

	private static final String FORCE_FAILURE_METHOD = "FAIL_FOR_TEST";

	private final PaymentRepository paymentRepository;

	public Payment charge(Order order, BigDecimal amount, String method) {
		PaymentStatus status = FORCE_FAILURE_METHOD.equalsIgnoreCase(method)
				? PaymentStatus.FAILED
				: PaymentStatus.SUCCESS;

		Payment payment = Payment.builder()
				.order(order)
				.amount(amount)
				.status(status)
				.method(method == null ? "MOCK_CARD" : method)
				.build();

		return paymentRepository.save(payment);
	}
}

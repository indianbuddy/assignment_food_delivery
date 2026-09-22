package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.NotificationLog;
import com.qryde.fooddelivery.domain.Order;
import com.qryde.fooddelivery.event.OrderPlacedEvent;
import com.qryde.fooddelivery.event.OrderStatusChangedEvent;
import com.qryde.fooddelivery.event.PartnerAssignedEvent;
import com.qryde.fooddelivery.repository.NotificationLogRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

/**
 * Fans out status updates to the affected parties. Runs @Async, after the
 * placing/updating transaction has committed (TransactionPhase.AFTER_COMMIT),
 * so a slow or failing notification never blocks or poisons the caller's
 * request, and we never notify anyone about a change that didn't actually
 * persist. A real system would push these to email/SMS/websocket providers;
 * here they're written to a NotificationLog table so behavior stays
 * observable and testable without standing up external infrastructure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

	private final OrderRepository orderRepository;
	private final NotificationLogRepository notificationLogRepository;

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onOrderPlaced(OrderPlacedEvent event) {
		orderRepository.findById(event.orderId()).ifPresent(order -> {
			notify(order.getId(), order.getRestaurant().getOwner().getId(), "RESTAURANT",
					"New order #" + order.getId() + " received");
			notify(order.getId(), order.getCustomer().getId(), "CUSTOMER",
					"Order #" + order.getId() + " placed successfully");
		});
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onOrderStatusChanged(OrderStatusChangedEvent event) {
		orderRepository.findById(event.orderId()).ifPresent(order -> {
			String message = "Order #" + order.getId() + " is now " + event.newStatus();
			notify(order.getId(), order.getCustomer().getId(), "CUSTOMER", message);
			notify(order.getId(), order.getRestaurant().getOwner().getId(), "RESTAURANT", message);
			if (order.getDeliveryPartner() != null) {
				notify(order.getId(), order.getDeliveryPartner().getUser().getId(), "DELIVERY_PARTNER", message);
			}
		});
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onPartnerAssigned(PartnerAssignedEvent event) {
		orderRepository.findById(event.orderId()).ifPresent(order -> {
			String message = "A delivery partner has been assigned to order #" + order.getId();
			notify(order.getId(), order.getCustomer().getId(), "CUSTOMER", message);
			notify(order.getId(), order.getRestaurant().getOwner().getId(), "RESTAURANT", message);
			if (order.getDeliveryPartner() != null) {
				notify(order.getId(), order.getDeliveryPartner().getUser().getId(), "DELIVERY_PARTNER",
						"You have been assigned order #" + order.getId());
			}
		});
	}

	private void notify(Long orderId, Long recipientUserId, String audience, String message) {
		NotificationLog entry = NotificationLog.builder()
				.orderId(orderId)
				.recipientUserId(recipientUserId)
				.audience(audience)
				.message(message)
				.build();
		notificationLogRepository.save(entry);
		log.info("[notification] {} -> user {} : {}", audience, recipientUserId, message);
	}
}

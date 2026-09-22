package com.qryde.fooddelivery.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Durable record of a fanned-out notification. Stands in for a real push/email/SMS
 * provider (out of scope) while still giving tests and reviewers something concrete
 * to assert against for the async fan-out requirement.
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long orderId;

	@Column(nullable = false)
	private Long recipientUserId;

	@Column(nullable = false)
	private String audience;

	@Column(nullable = false)
	private String message;

	@Column(nullable = false, updatable = false)
	private Instant sentAt;

	@PrePersist
	void onCreate() {
		this.sentAt = Instant.now();
	}
}

package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
	List<NotificationLog> findByOrderId(Long orderId);
}

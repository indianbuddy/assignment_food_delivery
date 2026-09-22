package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.DeliveryPartner;
import com.qryde.fooddelivery.domain.PartnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {
	Optional<DeliveryPartner> findByUserId(Long userId);
	List<DeliveryPartner> findByCityIdAndStatus(Long cityId, PartnerStatus status);
}

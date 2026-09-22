package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.DeliveryPartner;
import com.qryde.fooddelivery.domain.PartnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {
	Optional<DeliveryPartner> findByUserId(Long userId);
	List<DeliveryPartner> findByCityIdAndStatus(Long cityId, PartnerStatus status);

	/**
	 * Atomic guard for the partner's own side of "accept an assignment": a
	 * plain read-then-write here (check status == AVAILABLE, then save BUSY)
	 * would let the same partner win two different orders' independent
	 * assignPartnerIfUnassigned races at once, since that query only guards
	 * the order row, not the partner. This CAS closes that gap the same way.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE DeliveryPartner p SET p.status = :newStatus WHERE p.id = :id AND p.status = :expectedStatus")
	int updateStatusIfCurrent(@Param("id") Long id,
							   @Param("expectedStatus") PartnerStatus expectedStatus,
							   @Param("newStatus") PartnerStatus newStatus);
}

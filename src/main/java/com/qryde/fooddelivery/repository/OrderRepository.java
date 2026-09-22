package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.DeliveryPartner;
import com.qryde.fooddelivery.domain.Order;
import com.qryde.fooddelivery.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

	List<Order> findByDeliveryPartnerIdOrderByCreatedAtDesc(Long partnerId);

	/**
	 * Orders in a city, past acceptance and not yet claimed by a partner - the
	 * pool delivery partners poll/browse to self-assign from.
	 */
	@Query("SELECT o FROM Order o WHERE o.restaurant.city.id = :cityId " +
			"AND o.status IN ('ACCEPTED', 'PREPARING') AND o.deliveryPartner IS NULL " +
			"ORDER BY o.createdAt ASC")
	List<Order> findUnassignedInCity(@Param("cityId") Long cityId);

	/**
	 * Atomic compare-and-swap partner assignment. Only succeeds if the order is
	 * still unassigned and in an eligible status - this is what makes "multiple
	 * partners contending for the same order" resolve to exactly one winner
	 * without any explicit locking: the database's WHERE clause is the lock.
	 * Returns 0 if another partner already won the race (or the order moved on).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Order o SET o.deliveryPartner = :partner WHERE o.id = :orderId " +
			"AND o.deliveryPartner IS NULL AND o.status IN ('ACCEPTED', 'PREPARING')")
	int assignPartnerIfUnassigned(@Param("orderId") Long orderId, @Param("partner") DeliveryPartner partner);

	/**
	 * Atomic, guarded status transition: only applies if the order is still in
	 * the expected current status. Prevents two concurrent callers (e.g. a
	 * restaurant accept + a customer cancel racing each other) from both
	 * "succeeding" against stale state.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Order o SET o.status = :newStatus WHERE o.id = :orderId AND o.status = :expectedStatus")
	int updateStatusIfCurrent(@Param("orderId") Long orderId,
							   @Param("expectedStatus") OrderStatus expectedStatus,
							   @Param("newStatus") OrderStatus newStatus);
}

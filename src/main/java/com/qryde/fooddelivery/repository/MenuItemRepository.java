package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

	List<MenuItem> findByRestaurantIdAndAvailableTrue(Long restaurantId);

	List<MenuItem> findByRestaurantId(Long restaurantId);

	/**
	 * Atomic, single-statement stock decrement. This is the concurrency-safety
	 * mechanism for the "no oversell" requirement: instead of read-modify-write
	 * (which races under concurrent orders), the database itself only allows the
	 * decrement to succeed if enough stock is present, in one round trip.
	 * Returns the number of rows updated: 0 means insufficient stock (or item
	 * doesn't exist), and the caller must treat that as a failed reservation.
	 */
	// clearAutomatically is deliberately false: this runs mid-transaction inside
	// OrderService#placeOrder, and clearing the whole persistence context here
	// would detach the Order/OrderItem entities still being built in that same
	// transaction. We never re-read a MenuItem's stockQuantity from the
	// persistence context later in that flow, so the now-stale cached value is
	// harmless. flushAutomatically still ensures prior pending writes (e.g. the
	// Order insert) land before this bulk statement runs.
	@Modifying(flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.stockQuantity = m.stockQuantity - :quantity " +
			"WHERE m.id = :id AND m.stockQuantity >= :quantity")
	int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

	/**
	 * Reverses a prior decrement when an order is rejected/cancelled after stock
	 * was already reserved at placement time.
	 */
	@Modifying(flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.stockQuantity = m.stockQuantity + :quantity WHERE m.id = :id")
	int restock(@Param("id") Long id, @Param("quantity") int quantity);
}

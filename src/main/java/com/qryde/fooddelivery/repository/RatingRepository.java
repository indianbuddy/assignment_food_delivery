package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
	Optional<Rating> findByOrderId(Long orderId);
	List<Rating> findByOrderRestaurantId(Long restaurantId);
	boolean existsByOrderId(Long orderId);
}

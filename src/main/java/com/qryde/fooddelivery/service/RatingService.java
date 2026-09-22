package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.Order;
import com.qryde.fooddelivery.domain.OrderStatus;
import com.qryde.fooddelivery.domain.Rating;
import com.qryde.fooddelivery.dto.rating.RatingRequest;
import com.qryde.fooddelivery.dto.rating.RatingResponse;
import com.qryde.fooddelivery.exception.AccessDeniedBusinessException;
import com.qryde.fooddelivery.exception.ResourceNotFoundException;
import com.qryde.fooddelivery.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {

	private final RatingRepository ratingRepository;
	private final OrderService orderService;

	@Transactional
	public RatingResponse rate(Long orderId, Long customerId, RatingRequest request) {
		Order order = orderService.findEntity(orderId);

		if (!order.getCustomer().getId().equals(customerId)) {
			throw new AccessDeniedBusinessException("This order does not belong to you");
		}
		if (order.getStatus() != OrderStatus.DELIVERED) {
			throw new IllegalArgumentException("Only delivered orders can be rated");
		}
		if (ratingRepository.existsByOrderId(orderId)) {
			throw new IllegalArgumentException("This order has already been rated");
		}

		Rating rating = Rating.builder()
				.order(order)
				.customer(order.getCustomer())
				.restaurantRating(request.restaurantRating())
				.deliveryRating(request.deliveryRating())
				.comment(request.comment())
				.build();

		return toResponse(ratingRepository.save(rating));
	}

	public RatingResponse getForOrder(Long orderId) {
		return ratingRepository.findByOrderId(orderId)
				.map(this::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("No rating for order " + orderId));
	}

	public List<RatingResponse> listForRestaurant(Long restaurantId) {
		return ratingRepository.findByOrderRestaurantId(restaurantId).stream().map(this::toResponse).toList();
	}

	private RatingResponse toResponse(Rating rating) {
		return new RatingResponse(
				rating.getId(),
				rating.getOrder().getId(),
				rating.getRestaurantRating(),
				rating.getDeliveryRating(),
				rating.getComment(),
				rating.getCreatedAt()
		);
	}
}

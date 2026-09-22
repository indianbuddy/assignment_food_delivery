package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.dto.partner.DeliveryPartnerResponse;
import com.qryde.fooddelivery.dto.partner.PartnerRegisterRequest;
import com.qryde.fooddelivery.exception.AccessDeniedBusinessException;
import com.qryde.fooddelivery.exception.ConflictException;
import com.qryde.fooddelivery.exception.ResourceNotFoundException;
import com.qryde.fooddelivery.repository.DeliveryPartnerRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import com.qryde.fooddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qryde.fooddelivery.event.PartnerAssignedEvent;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryPartnerService {

	private final DeliveryPartnerRepository deliveryPartnerRepository;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final CityService cityService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public DeliveryPartnerResponse register(PartnerRegisterRequest request) {
		User user = userRepository.findById(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User " + request.userId() + " not found"));
		if (user.getRole() != Role.DELIVERY_PARTNER) {
			throw new IllegalArgumentException("User " + user.getId() + " is not a delivery partner");
		}
		deliveryPartnerRepository.findByUserId(user.getId()).ifPresent(p -> {
			throw new IllegalArgumentException("User " + user.getId() + " is already registered as a partner");
		});

		City city = cityService.findEntity(request.cityId());

		DeliveryPartner partner = DeliveryPartner.builder()
				.user(user)
				.city(city)
				.status(PartnerStatus.AVAILABLE)
				.build();

		return toResponse(deliveryPartnerRepository.save(partner));
	}

	@Transactional
	public DeliveryPartnerResponse updateStatus(Long userId, PartnerStatus status) {
		DeliveryPartner partner = findByUserId(userId);
		partner.setStatus(status);
		return toResponse(deliveryPartnerRepository.save(partner));
	}

	public List<com.qryde.fooddelivery.dto.order.OrderResponse> listAvailableOrders(Long userId) {
		DeliveryPartner partner = findByUserId(userId);
		return orderRepository.findUnassignedInCity(partner.getCity().getId()).stream()
				.map(OrderMapper::toResponse)
				.toList();
	}

	public List<com.qryde.fooddelivery.dto.order.OrderResponse> listMyAssignments(Long userId) {
		DeliveryPartner partner = findByUserId(userId);
		return orderRepository.findByDeliveryPartnerIdOrderByCreatedAtDesc(partner.getId()).stream()
				.map(OrderMapper::toResponse)
				.toList();
	}

	/**
	 * The core "multiple partners contending for the same order" flow. The
	 * conditional UPDATE in the repository is the single source of truth for
	 * who wins; this method just interprets the affected-row count.
	 */
	@Transactional
	public void acceptAssignment(Long orderId, Long userId) {
		DeliveryPartner partner = findByUserId(userId);
		if (partner.getStatus() != PartnerStatus.AVAILABLE) {
			throw new IllegalArgumentException("Partner is not available to accept new orders");
		}

		int updated = orderRepository.assignPartnerIfUnassigned(orderId, partner);
		if (updated == 0) {
			throw new ConflictException("Order is no longer available for assignment " +
					"(already claimed by another partner, or not yet eligible)");
		}

		partner.setStatus(PartnerStatus.BUSY);
		deliveryPartnerRepository.save(partner);

		eventPublisher.publishEvent(new PartnerAssignedEvent(orderId, partner.getId()));
	}

	@Transactional
	public void markAvailable(Long partnerId) {
		DeliveryPartner partner = deliveryPartnerRepository.findById(partnerId)
				.orElseThrow(() -> new ResourceNotFoundException("Delivery partner " + partnerId + " not found"));
		partner.setStatus(PartnerStatus.AVAILABLE);
		deliveryPartnerRepository.save(partner);
	}

	DeliveryPartner findByUserId(Long userId) {
		return deliveryPartnerRepository.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("No delivery partner profile for this user"));
	}

	void assertOwnership(DeliveryPartner partner, Long userId) {
		if (!partner.getUser().getId().equals(userId)) {
			throw new AccessDeniedBusinessException("This assignment does not belong to you");
		}
	}

	private DeliveryPartnerResponse toResponse(DeliveryPartner partner) {
		return new DeliveryPartnerResponse(
				partner.getId(),
				partner.getUser().getId(),
				partner.getUser().getFullName(),
				partner.getCity().getId(),
				partner.getCity().getName(),
				partner.getStatus().name()
		);
	}
}

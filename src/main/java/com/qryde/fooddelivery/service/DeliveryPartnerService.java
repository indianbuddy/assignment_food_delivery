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
	 * The core "multiple partners contending for the same order" flow -
	 * plus the less obvious flip side of the same race: the same partner
	 * accepting two different orders at once. Both sides are guarded by
	 * their own atomic conditional UPDATE (never a read-then-write), so
	 * either race resolves to exactly one winner with no explicit locking:
	 *
	 *  1. Claim the partner's own availability first
	 *     (DeliveryPartnerRepository#updateStatusIfCurrent, AVAILABLE->BUSY).
	 *     If this partner is already mid-assignment-request elsewhere, 0
	 *     rows are affected and neither order gets touched.
	 *  2. Claim the order (OrderRepository#assignPartnerIfUnassigned). If
	 *     someone else already claimed it, this throws - and since both
	 *     steps run in the same transaction, the partner-status flip from
	 *     step 1 rolls back too, so a lost race never leaves the partner
	 *     stuck BUSY with nothing assigned.
	 */
	@Transactional
	public void acceptAssignment(Long orderId, Long userId) {
		DeliveryPartner partner = findByUserId(userId);

		int partnerClaimed = deliveryPartnerRepository.updateStatusIfCurrent(
				partner.getId(), PartnerStatus.AVAILABLE, PartnerStatus.BUSY);
		if (partnerClaimed == 0) {
			throw new ConflictException("Partner is not currently available to accept new orders");
		}

		int orderClaimed = orderRepository.assignPartnerIfUnassigned(orderId, partner);
		if (orderClaimed == 0) {
			throw new ConflictException("Order is no longer available for assignment " +
					"(already claimed by another partner, or not yet eligible)");
		}

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

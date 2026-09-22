package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.*;
import com.qryde.fooddelivery.exception.ConflictException;
import com.qryde.fooddelivery.repository.DeliveryPartnerRepository;
import com.qryde.fooddelivery.repository.OrderRepository;
import com.qryde.fooddelivery.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryPartnerServiceTest {

	@Mock private DeliveryPartnerRepository deliveryPartnerRepository;
	@Mock private UserRepository userRepository;
	@Mock private OrderRepository orderRepository;
	@Mock private CityService cityService;
	@Mock private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private DeliveryPartnerService deliveryPartnerService;

	@Test
	void acceptAssignment_losingOrderRace_throwsConflictAndRollsBackPartnerClaim() {
		User partnerUser = User.builder().id(7L).role(Role.DELIVERY_PARTNER).build();
		DeliveryPartner partner = DeliveryPartner.builder().id(3L).user(partnerUser)
				.status(PartnerStatus.AVAILABLE).build();

		when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));
		// Partner successfully claims BUSY, but someone else already claimed
		// the order first - 0 rows affected on the order-side CAS.
		when(deliveryPartnerRepository.updateStatusIfCurrent(3L, PartnerStatus.AVAILABLE, PartnerStatus.BUSY))
				.thenReturn(1);
		when(orderRepository.assignPartnerIfUnassigned(eq(55L), eq(partner))).thenReturn(0);

		assertThatThrownBy(() -> deliveryPartnerService.acceptAssignment(55L, 7L))
				.isInstanceOf(ConflictException.class);

		// In the real transaction this failure rolls back the partner-status
		// CAS too - that atomicity is exercised for real in
		// ConcurrentPartnerAssignmentTest, not something a mock can show.
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void acceptAssignment_partnerNoLongerAvailable_throwsConflictBeforeTouchingOrder() {
		User partnerUser = User.builder().id(7L).role(Role.DELIVERY_PARTNER).build();
		DeliveryPartner partner = DeliveryPartner.builder().id(3L).user(partnerUser)
				.status(PartnerStatus.AVAILABLE).build();

		when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));
		// 0 rows affected = partner was no longer AVAILABLE by the time this
		// CAS ran (e.g. lost a race to accept a different order first).
		when(deliveryPartnerRepository.updateStatusIfCurrent(3L, PartnerStatus.AVAILABLE, PartnerStatus.BUSY))
				.thenReturn(0);

		assertThatThrownBy(() -> deliveryPartnerService.acceptAssignment(55L, 7L))
				.isInstanceOf(ConflictException.class);

		verifyNoInteractions(orderRepository);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void acceptAssignment_winningRace_publishesEvent() {
		User partnerUser = User.builder().id(7L).role(Role.DELIVERY_PARTNER).build();
		DeliveryPartner partner = DeliveryPartner.builder().id(3L).user(partnerUser)
				.status(PartnerStatus.AVAILABLE).build();

		when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));
		when(deliveryPartnerRepository.updateStatusIfCurrent(3L, PartnerStatus.AVAILABLE, PartnerStatus.BUSY))
				.thenReturn(1);
		when(orderRepository.assignPartnerIfUnassigned(eq(55L), eq(partner))).thenReturn(1);

		deliveryPartnerService.acceptAssignment(55L, 7L);

		verify(eventPublisher).publishEvent(any(com.qryde.fooddelivery.event.PartnerAssignedEvent.class));
	}
}

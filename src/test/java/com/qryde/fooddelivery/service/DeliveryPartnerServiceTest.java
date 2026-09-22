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
	void acceptAssignment_losingRace_throwsConflict() {
		User partnerUser = User.builder().id(7L).role(Role.DELIVERY_PARTNER).build();
		DeliveryPartner partner = DeliveryPartner.builder().id(3L).user(partnerUser)
				.status(PartnerStatus.AVAILABLE).build();

		when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));
		// 0 rows affected = someone else already claimed the order first.
		when(orderRepository.assignPartnerIfUnassigned(eq(55L), eq(partner))).thenReturn(0);

		assertThatThrownBy(() -> deliveryPartnerService.acceptAssignment(55L, 7L))
				.isInstanceOf(ConflictException.class);

		verify(deliveryPartnerRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void acceptAssignment_winningRace_marksPartnerBusyAndPublishesEvent() {
		User partnerUser = User.builder().id(7L).role(Role.DELIVERY_PARTNER).build();
		DeliveryPartner partner = DeliveryPartner.builder().id(3L).user(partnerUser)
				.status(PartnerStatus.AVAILABLE).build();

		when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));
		when(orderRepository.assignPartnerIfUnassigned(eq(55L), eq(partner))).thenReturn(1);
		when(deliveryPartnerRepository.save(any(DeliveryPartner.class))).thenAnswer(inv -> inv.getArgument(0));

		deliveryPartnerService.acceptAssignment(55L, 7L);

		verify(deliveryPartnerRepository).save(argThat(p -> p.getStatus() == PartnerStatus.BUSY));
		verify(eventPublisher).publishEvent(any(com.qryde.fooddelivery.event.PartnerAssignedEvent.class));
	}

	@Test
	void acceptAssignment_partnerNotAvailable_rejectedBeforeTouchingOrder() {
		User partnerUser = User.builder().id(7L).role(Role.DELIVERY_PARTNER).build();
		DeliveryPartner partner = DeliveryPartner.builder().id(3L).user(partnerUser)
				.status(PartnerStatus.BUSY).build();
		when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));

		assertThatThrownBy(() -> deliveryPartnerService.acceptAssignment(55L, 7L))
				.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(orderRepository);
	}
}

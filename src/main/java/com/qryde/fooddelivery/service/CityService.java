package com.qryde.fooddelivery.service;

import com.qryde.fooddelivery.domain.City;
import com.qryde.fooddelivery.dto.city.CityRequest;
import com.qryde.fooddelivery.dto.city.CityResponse;
import com.qryde.fooddelivery.exception.ResourceNotFoundException;
import com.qryde.fooddelivery.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

	private final CityRepository cityRepository;

	@Transactional
	public CityResponse create(CityRequest request) {
		cityRepository.findByNameIgnoreCase(request.name()).ifPresent(c -> {
			throw new IllegalArgumentException("City '" + request.name() + "' already exists");
		});
		City city = City.builder().name(request.name()).active(true).build();
		return toResponse(cityRepository.save(city));
	}

	public List<CityResponse> listActive() {
		return cityRepository.findByActiveTrue().stream().map(this::toResponse).toList();
	}

	public List<CityResponse> listAll() {
		return cityRepository.findAll().stream().map(this::toResponse).toList();
	}

	public CityResponse get(Long id) {
		return toResponse(findEntity(id));
	}

	@Transactional
	public CityResponse setActive(Long id, boolean active) {
		City city = findEntity(id);
		city.setActive(active);
		return toResponse(cityRepository.save(city));
	}

	City findEntity(Long id) {
		return cityRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("City " + id + " not found"));
	}

	private CityResponse toResponse(City city) {
		return new CityResponse(city.getId(), city.getName(), city.isActive());
	}
}

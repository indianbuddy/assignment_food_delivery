package com.qryde.fooddelivery.controller;

import com.qryde.fooddelivery.dto.city.CityResponse;
import com.qryde.fooddelivery.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

	private final CityService cityService;

	@GetMapping
	public List<CityResponse> list() {
		return cityService.listActive();
	}

	@GetMapping("/{id}")
	public CityResponse get(@PathVariable Long id) {
		return cityService.get(id);
	}
}

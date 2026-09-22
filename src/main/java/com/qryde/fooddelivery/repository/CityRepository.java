package com.qryde.fooddelivery.repository;

import com.qryde.fooddelivery.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
	Optional<City> findByNameIgnoreCase(String name);
	List<City> findByActiveTrue();
}

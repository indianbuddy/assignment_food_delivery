package com.qryde.fooddelivery.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "restaurant_id", nullable = false)
	private Restaurant restaurant;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private Integer stockQuantity;

	@Column(nullable = false)
	private boolean available;

	/**
	 * General-purpose optimistic lock for owner-driven edits (price/description changes).
	 * Stock decrements during order placement use an explicit conditional UPDATE instead
	 * (see MenuItemRepository#decrementStock) so concurrent orders never retry/collide on it.
	 */
	@Version
	private Long version;
}

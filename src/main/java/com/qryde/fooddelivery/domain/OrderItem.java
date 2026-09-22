package com.qryde.fooddelivery.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "menu_item_id", nullable = false)
	private MenuItem menuItem;

	/** Snapshot of the item name at order time, so later menu edits don't rewrite history. */
	@Column(nullable = false)
	private String menuItemName;

	@Column(nullable = false)
	private Integer quantity;

	/** Snapshot of unit price at order time. */
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal priceAtOrder;
}

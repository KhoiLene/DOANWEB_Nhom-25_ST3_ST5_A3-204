package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(name = "reserved_quantity")
    @Builder.Default
    private Integer reservedQuantity = 0;

    public int availableQuantity() {
        return Math.max(0, (stockQuantity == null ? 0 : stockQuantity)
                - (reservedQuantity == null ? 0 : reservedQuantity));
    }

    public int getAvailableQuantity() { return availableQuantity(); }
}

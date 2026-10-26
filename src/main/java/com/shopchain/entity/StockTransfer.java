package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(optional = false)
    @JoinColumn(name = "destination_store_id", nullable = false)
    private Store destinationStore;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime receivedAt;
}

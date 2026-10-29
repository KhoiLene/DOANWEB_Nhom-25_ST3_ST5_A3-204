package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sales_invoice_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

    private Long productId;
    private String sku;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "list_unit_price")
    private BigDecimal listUnitPrice;

    @Column(nullable = false)
    private BigDecimal lineTotal;
}

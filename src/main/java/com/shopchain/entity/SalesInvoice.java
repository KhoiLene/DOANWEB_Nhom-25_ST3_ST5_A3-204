package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    private String customerName;
    private String customerEmail;
    private String customerPhone;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "shipping_amount")
    private BigDecimal shippingAmount;

    @Column(nullable = false)
    private BigDecimal taxAmount;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesInvoiceLine> lines = new ArrayList<>();
}

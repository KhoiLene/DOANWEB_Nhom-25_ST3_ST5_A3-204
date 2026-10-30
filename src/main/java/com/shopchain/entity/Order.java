package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private LocalDateTime orderDate = LocalDateTime.now();

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "subtotal_amount")
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "shipping_amount")
    private BigDecimal shippingAmount;

    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    @Column(name = "transfer_reported_at")
    private LocalDateTime transferReportedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "estimated_pickup_at")
    private LocalDateTime estimatedPickupAt;

    @Column(nullable = false)
    private String status; // PENDING, CONFIRMED, PROCESSING, SHIPPING, READY_FOR_PICKUP, DELIVERED, CANCELLED, RETURNED

    private String paymentMethod; // COD, VNPAY, MOMO
    private String paymentStatus; // UNPAID, PAID

    private String fulfillmentType; // PICKUP, STORE_SHIP, WAREHOUSE_SHIP
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private String deliveryCity;

    @Builder.Default
    @Column(name = "loyalty_points_awarded")
    private Boolean loyaltyPointsAwarded = false;

    @Builder.Default
    @Column(name = "loyalty_points_redeemed", nullable = false)
    private Integer loyaltyPointsRedeemed = 0;

    @Builder.Default
    @Column(name = "loyalty_points_restored", nullable = false)
    private Boolean loyaltyPointsRestored = false;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse fulfillmentWarehouse;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();
}

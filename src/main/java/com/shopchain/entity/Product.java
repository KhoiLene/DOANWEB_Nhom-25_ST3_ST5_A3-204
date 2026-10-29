package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    @Column(nullable = false)
    private Double price;

    private Boolean isActive = true;

    private Boolean isFeatured = false;

    private Boolean isFlashSale = false;

    @Column(name = "discount_percent")
    private Double discountPercent;

    @Column(name = "campaign_name", length = 120)
    private String campaignName;

    @Column(name = "campaign_audience", length = 24)
    @Builder.Default
    private String campaignAudience = "ALL";

    @Column(name = "campaign_start_at")
    private LocalDateTime campaignStartAt;

    @Column(name = "campaign_end_at")
    private LocalDateTime campaignEndAt;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "suitable_for", length = 300)
    private String suitableFor;

    @Column(name = "usage_instructions", columnDefinition = "TEXT")
    private String usageInstructions;

    @Column(name = "ingredients", columnDefinition = "TEXT")
    private String ingredients;

    @Column(name = "product_cautions", columnDefinition = "TEXT")
    private String cautions;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductInventory> inventories = new ArrayList<>();

    public boolean hasDiscountCampaign() {
        LocalDateTime now = LocalDateTime.now();
        return discountPercent != null && discountPercent > 0 && discountPercent < 100
                && (campaignStartAt == null || !now.isBefore(campaignStartAt))
                && (campaignEndAt == null || !now.isAfter(campaignEndAt));
    }

    public boolean campaignEligibleFor(Integer loyaltyPoints) {
        if (!hasDiscountCampaign()) return false;
        if ("VIP".equalsIgnoreCase(campaignAudience)) return loyaltyPoints != null && loyaltyPoints >= 500;
        return campaignAudience == null || campaignAudience.isBlank() || "ALL".equalsIgnoreCase(campaignAudience);
    }

    public double effectivePrice(Integer loyaltyPoints) {
        return effectivePrice(price == null ? 0d : price, loyaltyPoints);
    }

    public double effectivePrice(Double basePrice, Integer loyaltyPoints) {
        java.math.BigDecimal safeBase = java.math.BigDecimal.valueOf(basePrice == null ? 0d : basePrice)
                .setScale(0, java.math.RoundingMode.HALF_UP);
        if (!campaignEligibleFor(loyaltyPoints)) return safeBase.doubleValue();
        java.math.BigDecimal multiplier = java.math.BigDecimal.ONE
                .subtract(java.math.BigDecimal.valueOf(discountPercent).movePointLeft(2));
        return safeBase.multiply(multiplier).setScale(0, java.math.RoundingMode.HALF_UP)
                .max(java.math.BigDecimal.ZERO).doubleValue();
    }
}

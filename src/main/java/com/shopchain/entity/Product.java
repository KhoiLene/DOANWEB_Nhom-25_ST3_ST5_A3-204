package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

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

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductInventory> inventories = new ArrayList<>();

    public boolean hasDiscountCampaign() {
        return discountPercent != null && discountPercent > 0 && discountPercent < 100;
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
        double safeBase = basePrice == null ? 0d : basePrice;
        if (!campaignEligibleFor(loyaltyPoints)) return safeBase;
        return Math.max(0d, Math.round(safeBase * (1d - discountPercent / 100d)));
    }
}

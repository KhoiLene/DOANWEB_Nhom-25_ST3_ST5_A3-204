package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "homepage_promo_banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomepagePromoBanner {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String position;

    private String tag;

    @Column(nullable = false)
    private String title;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "cta_text")
    private String ctaText;

    @Column(name = "cta_link")
    private String ctaLink;

    @Column(name = "sort_order")
    private Integer sortOrder;

    private Boolean enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}

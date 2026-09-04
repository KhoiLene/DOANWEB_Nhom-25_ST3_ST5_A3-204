package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "homepage_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomepageValue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String icon;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder;

    private Boolean enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}

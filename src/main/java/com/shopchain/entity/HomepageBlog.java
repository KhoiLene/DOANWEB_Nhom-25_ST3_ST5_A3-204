package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "homepage_blog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomepageBlog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    private String author;

    @Column(name = "sort_order")
    private Integer sortOrder;

    private Boolean enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}

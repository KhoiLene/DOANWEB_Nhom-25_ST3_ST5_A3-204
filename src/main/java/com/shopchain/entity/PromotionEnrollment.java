package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_enrollments", uniqueConstraints =
        @UniqueConstraint(columnNames = {"user_id", "promotion_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private HomepagePromoBanner promotion;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
}

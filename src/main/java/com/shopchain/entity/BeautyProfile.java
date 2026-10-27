package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "beauty_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeautyProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "skin_type", length = 40)
    private String skinType;

    @Column(name = "hair_type", length = 60)
    private String hairType;

    @Column(name = "skin_concerns", length = 300)
    private String skinConcerns;

    @Column(name = "hair_concerns", length = 300)
    private String hairConcerns;

    @Column(length = 300)
    private String sensitivities;

    @Column(name = "max_budget")
    private Double maxBudget;

    @Builder.Default
    @Column(name = "assistant_consent", nullable = false)
    private boolean assistantConsent = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}

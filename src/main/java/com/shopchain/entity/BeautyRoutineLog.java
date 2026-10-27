package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "beauty_routine_logs", uniqueConstraints =
        @UniqueConstraint(name = "uk_beauty_routine_user_date_step", columnNames = {"user_id", "care_date", "step_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeautyRoutineLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "care_date", nullable = false)
    private LocalDate careDate;

    @Column(name = "step_key", nullable = false, length = 40)
    private String stepKey;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}

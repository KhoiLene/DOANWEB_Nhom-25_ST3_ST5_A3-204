package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "internal_messages", indexes = {
        @Index(name = "idx_internal_message_created", columnList = "created_at"),
        @Index(name = "idx_internal_message_recipient", columnList = "recipient_role")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "recipient_role", nullable = false, length = 40)
    private String recipientRole;

    @Column(nullable = false, length = 1500)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

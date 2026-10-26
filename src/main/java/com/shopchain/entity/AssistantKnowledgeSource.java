package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_knowledge_sources", indexes = {
        @Index(name = "idx_assistant_source_role_active", columnList = "role_key,is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantKnowledgeSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_key", nullable = false, length = 32)
    private String roleKey;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "index_status", length = 20)
    @Builder.Default
    private String indexStatus = "PENDING";

    @Column(name = "indexed_chunk_count")
    @Builder.Default
    private Integer indexedChunkCount = 0;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @Column(name = "index_error", length = 500)
    private String indexError;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "embedding_model", length = 80)
    private String embeddingModel;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (indexStatus == null) indexStatus = "PENDING";
        if (indexedChunkCount == null) indexedChunkCount = 0;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}

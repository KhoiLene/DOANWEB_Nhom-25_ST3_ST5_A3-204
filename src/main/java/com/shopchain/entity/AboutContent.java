package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "about_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AboutContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", unique = true)
    private UUID groupId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

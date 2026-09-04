package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String thumbnail;

    private String status;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Admin author;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String sourceUrl;

    @Column(name = "site_name")
    private String siteName;

    private String summary;

    @Column(name = "excerpt_html", columnDefinition = "TEXT")
    private String excerptHtml;

    private String thumbnailSource;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private NewsCategory category;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "post_type")
    private String postType;
}

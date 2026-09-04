package com.shopchain.repository;

import com.shopchain.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByStatusOrderByPublishedAtDesc(String status);
    Optional<Post> findBySlug(String slug);
    List<Post> findByCategory_IdAndStatus(Long categoryId, String status);
}

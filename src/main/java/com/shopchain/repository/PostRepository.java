package com.shopchain.repository;

import com.shopchain.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByStatusOrderByPublishedAtDesc(String status);
    Optional<Post> findBySlug(String slug);
    List<Post> findByCategory_IdAndStatus(Long categoryId, String status);
    @Query("select p.slug from Post p where p.slug in :slugs")
    Set<String> findSlugsIn(@Param("slugs") Collection<String> slugs);
}

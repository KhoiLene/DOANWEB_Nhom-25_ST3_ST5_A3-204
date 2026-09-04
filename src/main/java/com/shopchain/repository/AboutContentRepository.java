package com.shopchain.repository;

import com.shopchain.entity.AboutContent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AboutContentRepository extends JpaRepository<AboutContent, Long> {
    Optional<AboutContent> findFirstByOrderByUpdatedAtDesc();
}

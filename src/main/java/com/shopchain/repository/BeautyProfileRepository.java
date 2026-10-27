package com.shopchain.repository;

import com.shopchain.entity.BeautyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BeautyProfileRepository extends JpaRepository<BeautyProfile, Long> {
    Optional<BeautyProfile> findByUserId(Long userId);
}

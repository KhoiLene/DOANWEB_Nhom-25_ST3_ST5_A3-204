package com.shopchain.repository;

import com.shopchain.entity.PromotionEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionEnrollmentRepository extends JpaRepository<PromotionEnrollment, Long> {
    List<PromotionEnrollment> findByUserId(Long userId);
    Optional<PromotionEnrollment> findByUserIdAndPromotionId(Long userId, UUID promotionId);
}

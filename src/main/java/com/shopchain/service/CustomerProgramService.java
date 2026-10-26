package com.shopchain.service;

import com.shopchain.entity.HomepagePromoBanner;
import com.shopchain.entity.PromotionEnrollment;
import com.shopchain.entity.User;
import com.shopchain.repository.HomepagePromoBannerRepository;
import com.shopchain.repository.PromotionEnrollmentRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerProgramService {
    private final HomepagePromoBannerRepository promotionRepository;
    private final PromotionEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    public CustomerProgramService(HomepagePromoBannerRepository promotionRepository,
                                  PromotionEnrollmentRepository enrollmentRepository,
                                  UserRepository userRepository) {
        this.promotionRepository = promotionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ProgramCard> listFor(Long userId) {
        return promotionRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(promotion -> new ProgramCard(promotion,
                        enrollmentRepository.findByUserIdAndPromotionId(userId, promotion.getId()).isPresent()))
                .toList();
    }

    @Transactional
    public ProgramCard join(Long userId, UUID promotionId) {
        HomepagePromoBanner promotion = promotionRepository.findById(promotionId)
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .orElseThrow(() -> new IllegalArgumentException("Chương trình này hiện không còn mở."));
        if (enrollmentRepository.findByUserIdAndPromotionId(userId, promotionId).isEmpty()) {
            User user = userRepository.findById(userId).orElseThrow();
            enrollmentRepository.save(PromotionEnrollment.builder().user(user).promotion(promotion)
                    .joinedAt(LocalDateTime.now()).build());
        }
        return new ProgramCard(promotion, true);
    }

    public record ProgramCard(HomepagePromoBanner promotion, boolean joined) { }
}

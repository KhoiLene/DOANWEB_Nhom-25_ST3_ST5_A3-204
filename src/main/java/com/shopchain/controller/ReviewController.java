package com.shopchain.controller;

import com.shopchain.entity.Review;
import com.shopchain.entity.User;
import com.shopchain.repository.ProductRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewController(ReviewService reviewService, ProductRepository productRepository, UserRepository userRepository) {
        this.reviewService = reviewService;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitReview(@RequestBody ReviewRequest reviewRequest, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated() || reviewRequest == null
                    || reviewRequest.productId() == null
                    || reviewRequest.rating() == null || reviewRequest.rating() < 1 || reviewRequest.rating() > 5
                    || (reviewRequest.comment() != null && reviewRequest.comment().length() > 2000)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Thông tin đánh giá chưa hợp lệ."));
            }

            User reviewer = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (reviewer == null || reviewer.getRole() == null || !"ROLE_USER".equals(reviewer.getRole().getName())) {
                return ResponseEntity.status(403).body(Map.of("error", "Chỉ khách hàng đã đăng nhập mới có thể đánh giá."));
            }
            var product = productRepository.findById(reviewRequest.productId()).orElse(null);
            if (product == null) return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy sản phẩm."));

            Review review = Review.builder()
                    .product(product)
                    .user(reviewer)
                    .rating(reviewRequest.rating())
                    .comment(reviewRequest.comment() == null ? null : reviewRequest.comment().trim())
                    .reviewerName(reviewer.getFullName() == null || reviewer.getFullName().isBlank()
                            ? reviewer.getUsername() : reviewer.getFullName())
                    .reviewerPhone(reviewer.getPhone())
                    .createdAt(LocalDateTime.now())
                    .build();

            reviewService.saveReview(review);
            return ResponseEntity.ok(Map.of("message", "Review submitted successfully!"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thông tin đánh giá chưa hợp lệ."));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Chưa thể gửi đánh giá. Vui lòng thử lại."));
        }
    }

    public record ReviewRequest(Long productId, Integer rating, String comment) { }
}

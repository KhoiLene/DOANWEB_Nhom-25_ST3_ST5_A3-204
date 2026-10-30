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
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST Controller xử lý gửi và tra cứu đánh giá sản phẩm.
 * Hỗ trợ đánh giá từ khách hàng đã đăng nhập hoặc khách mua hàng với tên hiển thị.
 */
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

    /**
     * Gửi đánh giá sản phẩm (hỗ trợ cả thành viên và khách hàng).
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitReview(@RequestBody ReviewRequest reviewRequest, Authentication authentication) {
        try {
            if (reviewRequest == null || reviewRequest.productId() == null
                    || reviewRequest.rating() == null || reviewRequest.rating() < 1 || reviewRequest.rating() > 5
                    || (reviewRequest.comment() != null && reviewRequest.comment().length() > 2000)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Thông tin đánh giá chưa hợp lệ (điểm từ 1 đến 5 sao)."));
            }

            var product = productRepository.findById(reviewRequest.productId()).orElse(null);
            if (product == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy sản phẩm."));
            }

            User reviewer = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getName())) {
                reviewer = userRepository.findByUsername(authentication.getName()).orElse(null);
            }

            String reviewerName = reviewRequest.reviewerName();
            if (reviewer != null) {
                if (reviewerName == null || reviewerName.isBlank()) {
                    reviewerName = reviewer.getFullName() != null && !reviewer.getFullName().isBlank()
                            ? reviewer.getFullName() : reviewer.getUsername();
                }
            } else {
                if (reviewerName == null || reviewerName.isBlank()) {
                    reviewerName = "Khách hàng";
                }
            }

            Review review = Review.builder()
                    .product(product)
                    .user(reviewer)
                    .rating(reviewRequest.rating())
                    .comment(reviewRequest.comment() == null ? null : reviewRequest.comment().trim())
                    .reviewerName(reviewerName.trim())
                    .reviewerPhone(reviewer != null ? reviewer.getPhone() : reviewRequest.reviewerPhone())
                    .createdAt(LocalDateTime.now())
                    .build();

            reviewService.saveReview(review);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return ResponseEntity.ok(Map.of(
                    "message", "Cảm ơn bạn! Đánh giá đã được gửi thành công.",
                    "review", Map.of(
                            "rating", review.getRating(),
                            "comment", review.getComment() != null ? review.getComment() : "",
                            "reviewerName", review.getReviewerName(),
                            "createdAt", review.getCreatedAt().format(formatter)
                    )
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Chưa thể gửi đánh giá. Vui lòng thử lại sau."));
        }
    }

    public record ReviewRequest(Long productId, Integer rating, String comment, String reviewerName, String reviewerPhone) { }
}

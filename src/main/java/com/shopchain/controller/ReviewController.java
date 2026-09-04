package com.shopchain.controller;

import com.shopchain.entity.Product;
import com.shopchain.entity.Review;
import com.shopchain.entity.User;
import com.shopchain.repository.ProductRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.ReviewService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> submitReview(@RequestBody Review reviewRequest) {
        try {
            // In a real app, we'd get the current authenticated user from SecurityContext
            // For now, we handle guest or provided user
            if (reviewRequest.getUser() != null) {
                User user = userRepository.findById(reviewRequest.getUser().getId())
                        .orElseThrow(() -> new RuntimeException("User not found"));
                reviewRequest.setUser(user);
            }

            Review review = Review.builder()
                    .product(reviewRequest.getProduct())
                    .user(reviewRequest.getUser())
                    .rating(reviewRequest.getRating())
                    .comment(reviewRequest.getComment())
                    .reviewerName(reviewRequest.getReviewerName())
                    .reviewerPhone(reviewRequest.getReviewerPhone())
                    .createdAt(LocalDateTime.now())
                    .build();

            reviewService.saveReview(review);
            return ResponseEntity.ok(Map.of("message", "Review submitted successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

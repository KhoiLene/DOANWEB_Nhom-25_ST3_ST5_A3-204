package com.shopchain.service;

import com.shopchain.entity.Product;
import com.shopchain.entity.Review;
import java.util.List;

public interface ReviewService {
    Review saveReview(Review review);
    List<Review> getReviewsByProduct(Long productId);
    Double getAverageRating(Long productId);
    long countReviews(Long productId);
}

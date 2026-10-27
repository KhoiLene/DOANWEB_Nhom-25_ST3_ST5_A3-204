package com.shopchain.controller;

import com.shopchain.entity.Product;
import com.shopchain.entity.Category;
import com.shopchain.service.ProductService;
import com.shopchain.service.StoreReservationService;
import com.shopchain.repository.UserRepository;
import com.shopchain.repository.ProductRepository;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StoreReservationService reservationService;

    public ProductApiController(ProductService productService, ProductRepository productRepository, UserRepository userRepository,
                                StoreReservationService reservationService) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.reservationService = reservationService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {

        // Lấy trang đầu tiên với size 50 để trả về cho JS filter
        Page<Product> productPage = productService.searchProducts(keyword, categoryId, PageRequest.of(0, 50));
        return ResponseEntity.ok(productPage.getContent().stream().map(this::toPublicResponse).toList());
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<List<ProductSuggestion>> suggestions(@RequestParam(required = false) String keyword,
                                                               Authentication authentication) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.length() < 2 || query.length() > 100) return ResponseEntity.ok(List.of());
        Page<Product> page = productService.searchProducts(query, null, PageRequest.of(0, 6));
        List<Product> matches = page.getContent();
        if (matches.isEmpty()) {
            String normalizedQuery = normalize(query);
            matches = productService.searchProducts(null, null, PageRequest.of(0, 500)).getContent().stream()
                    .filter(product -> normalize(product.getName()).contains(normalizedQuery)
                            || normalize(product.getDescription()).contains(normalizedQuery)
                            || normalize(product.getSku()).contains(normalizedQuery))
                    .limit(6)
                    .toList();
        }
        int points = authentication == null || !authentication.isAuthenticated() ? 0
                : userRepository.findByUsername(authentication.getName())
                    .map(user -> user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()).orElse(0);
        List<ProductSuggestion> suggestions = matches.stream()
                .filter(Objects::nonNull)
                .map(product -> {
                    boolean eligible = product.campaignEligibleFor(points);
                    return new ProductSuggestion(product.getId(), product.getName(), product.getImageUrl(),
                            product.effectivePrice(points), eligible ? product.getPrice() : product.effectivePrice(points),
                            product.getDiscountPercent(), product.getCampaignName(), product.getCampaignAudience());
                })
                .toList();
        return ResponseEntity.ok(suggestions);
    }

    private String normalize(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd');
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getDetail(@PathVariable Long id) {
        return productRepository.findById(id)
                .filter(product -> !Boolean.FALSE.equals(product.getIsActive()))
                .map(this::toPublicResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<?> availability(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reservationService.availability(id));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
        }
    }

    public record ProductSuggestion(Long id, String name, String imageUrl, Double price, Double originalPrice,
                                    Double discountPercent, String campaignName, String campaignAudience) { }

    private ProductResponse toPublicResponse(Product product) {
        Category category = product.getCategory();
        CategorySummary categorySummary = category == null ? null : new CategorySummary(category.getId(), category.getName());
        return new ProductResponse(product.getId(), product.getName(), product.getSku(), product.getDescription(),
                product.getImageUrl(), product.getPrice(), product.getIsActive(), product.getIsFeatured(),
                product.getIsFlashSale(), product.getDiscountPercent(), product.getCampaignName(),
                product.getCampaignAudience(), product.getCampaignStartAt(), product.getCampaignEndAt(),
                product.getBrandName(), product.getSuitableFor(), product.getUsageInstructions(),
                product.getIngredients(), product.getCautions(), categorySummary);
    }

    public record CategorySummary(Long id, String name) { }

    /** Public catalog payload deliberately excludes branch and warehouse inventory entities. */
    public record ProductResponse(Long id, String name, String sku, String description, String imageUrl,
                                  Double price, Boolean isActive, Boolean isFeatured, Boolean isFlashSale,
                                  Double discountPercent, String campaignName, String campaignAudience,
                                  java.time.LocalDateTime campaignStartAt, java.time.LocalDateTime campaignEndAt,
                                  String brandName, String suitableFor, String usageInstructions,
                                  String ingredients, String cautions, CategorySummary category) { }
}

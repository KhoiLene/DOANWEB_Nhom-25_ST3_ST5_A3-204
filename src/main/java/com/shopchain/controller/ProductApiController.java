package com.shopchain.controller;

import com.shopchain.entity.Product;
import com.shopchain.service.ProductService;
import com.shopchain.repository.UserRepository;
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
    private final UserRepository userRepository;

    public ProductApiController(ProductService productService, UserRepository userRepository) {
        this.productService = productService;
        this.userRepository = userRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {

        // Lấy trang đầu tiên với size 50 để trả về cho JS filter
        Page<Product> productPage = productService.searchProducts(keyword, categoryId, PageRequest.of(0, 50));
        return ResponseEntity.ok(productPage.getContent());
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
    public ResponseEntity<Product> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductDetail(id));
    }

    public record ProductSuggestion(Long id, String name, String imageUrl, Double price, Double originalPrice,
                                    Double discountPercent, String campaignName, String campaignAudience) { }
}

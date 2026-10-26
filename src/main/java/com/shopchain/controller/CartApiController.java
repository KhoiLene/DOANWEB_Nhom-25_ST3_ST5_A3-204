package com.shopchain.controller;

import com.shopchain.entity.Cart;
import com.shopchain.service.CartService;
import com.shopchain.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final CartService cartService;
    private final UserRepository userRepository;

    public CartApiController(CartService cartService, UserRepository userRepository) {
        this.cartService = cartService;
        this.userRepository = userRepository;
    }

    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());

            Cart cart = cartService.addToCart(currentUserId(authentication), productId, quantity);
            return ResponseEntity.ok(Map.of("message", "Product added to cart", "cartId", cart.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateQuantity(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());

            cartService.updateQuantity(currentUserId(authentication), productId, quantity);
            return ResponseEntity.ok(Map.of("message", "Quantity updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> removeFromCart(@RequestParam Long productId, Authentication authentication) {
        try {
            cartService.removeFromCart(currentUserId(authentication), productId);
            return ResponseEntity.ok(Map.of("message", "Product removed from cart"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."))
                .getId();
    }
}

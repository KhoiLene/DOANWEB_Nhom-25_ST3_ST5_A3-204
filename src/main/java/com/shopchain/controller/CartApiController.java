package com.shopchain.controller;

import com.shopchain.entity.Cart;
import com.shopchain.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final CartService cartService;

    public CartApiController(CartService cartService) {
        this.cartService = cartService;
    }

    // Using a mock user ID for now as in WebController
    private final Long MOCK_USER_ID = 1L;

    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> request) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());

            Cart cart = cartService.addToCart(MOCK_USER_ID, productId, quantity);
            return ResponseEntity.ok(Map.of("message", "Product added to cart", "cartId", cart.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateQuantity(@RequestBody Map<String, Object> request) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());

            cartService.updateQuantity(MOCK_USER_ID, productId, quantity);
            return ResponseEntity.ok(Map.of("message", "Quantity updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> removeFromCart(@RequestParam Long productId) {
        try {
            cartService.removeFromCart(MOCK_USER_ID, productId);
            return ResponseEntity.ok(Map.of("message", "Product removed from cart"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

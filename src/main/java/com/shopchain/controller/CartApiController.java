package com.shopchain.controller;

import com.shopchain.entity.Cart;
import com.shopchain.entity.CartItem;
import com.shopchain.service.CartService;
import com.shopchain.service.OrderPricingService;
import com.shopchain.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final CartService cartService;
    private final UserRepository userRepository;
    private final OrderPricingService pricingService;

    public CartApiController(CartService cartService, UserRepository userRepository, OrderPricingService pricingService) {
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
    }

    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());

            Cart cart = cartService.addToCart(currentUserId(authentication), productId, quantity);
            return ResponseEntity.ok(summary(cart, "Đã thêm sản phẩm vào giỏ hàng."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateQuantity(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());

            cartService.updateQuantity(currentUserId(authentication), productId, quantity);
            Cart cart = cartService.getCart(currentUserId(authentication));
            return ResponseEntity.ok(summary(cart, "Đã cập nhật số lượng."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> removeFromCart(@RequestParam Long productId, Authentication authentication) {
        try {
            cartService.removeFromCart(currentUserId(authentication), productId);
            Cart cart = cartService.getCart(currentUserId(authentication));
            return ResponseEntity.ok(summary(cart, "Đã xoá sản phẩm khỏi giỏ."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(Authentication authentication) {
        try {
            return ResponseEntity.ok(summary(cartService.getCart(currentUserId(authentication)), ""));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    private Map<String, Object> summary(Cart cart, String message) {
        List<CartItem> items = cart.getItems() == null ? List.of() : cart.getItems();
        int count = items.stream().mapToInt(item -> item.getQuantity()).sum();
        int loyaltyPoints = cart.getUser() == null || cart.getUser().getLoyaltyPoints() == null
                ? 0 : cart.getUser().getLoyaltyPoints();
        OrderPricingService.Quote quote = pricingService.quote(items, loyaltyPoints, "DELIVERY");
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("subtotal", quote.merchandiseTotal().doubleValue());
        result.put("listSubtotal", quote.subtotal().doubleValue());
        result.put("discount", quote.discount().doubleValue());
        result.put("shipping", quote.shipping().doubleValue());
        result.put("total", quote.total().doubleValue());
        if (cart.getId() != null) result.put("cartId", cart.getId());
        if (message != null && !message.isBlank()) result.put("message", message);
        return result;
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."))
                .getId();
    }

    private String errorMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Không thể cập nhật giỏ hàng. Vui lòng thử lại." : exception.getMessage();
    }
}

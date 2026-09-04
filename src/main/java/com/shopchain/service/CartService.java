package com.shopchain.service;

import com.shopchain.entity.Cart;
import com.shopchain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CartService {
    Cart addToCart(Long userId, Long productId, Integer quantity);
    Cart updateQuantity(Long userId, Long productId, Integer quantity);
    void removeFromCart(Long userId, Long productId);
    Cart getCart(Long userId);
    void clearCart(Long userId);
}

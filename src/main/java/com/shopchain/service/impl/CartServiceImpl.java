package com.shopchain.service.impl;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartServiceImpl(CartRepository cartRepository, UserRepository userRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Cart addToCart(Long userId, Long productId, Integer quantity) {
        requirePositive(quantity);
        if (quantity > 99) throw new IllegalArgumentException("Mỗi sản phẩm chỉ có thể thêm tối đa 99 món một lần.");
        User user = userRepository.findById(userId).orElseThrow();
        Cart cart = cartRepository.findByUser(user).orElseGet(() -> {
            Cart newCart = Cart.builder().user(user).build();
            return cartRepository.save(newCart);
        });

        Product product = productRepository.findById(productId).orElseThrow();
        if (Boolean.FALSE.equals(product.getIsActive())) throw new IllegalArgumentException("Sản phẩm hiện không còn được bán.");

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst().orElse(null);

        if (existingItem != null) {
            if (existingItem.getQuantity() + quantity > 99) throw new IllegalArgumentException("Giỏ hàng giới hạn 99 món cho mỗi sản phẩm.");
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();
            cart.getItems().add(newItem);
        }
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart updateQuantity(Long userId, Long productId, Integer quantity) {
        requirePositive(quantity);
        if (quantity > 99) throw new IllegalArgumentException("Số lượng tối đa là 99.");
        User user = userRepository.findById(userId).orElseThrow();
        Cart cart = cartRepository.findByUser(user).orElseThrow();

        CartItem item = cart.getItems().stream()
                .filter(cartItem -> cartItem.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không có trong giỏ hàng."));
        item.setQuantity(quantity);

        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void removeFromCart(Long userId, Long productId) {
        User user = userRepository.findById(userId).orElseThrow();
        Cart cart = cartRepository.findByUser(user).orElse(null);
        if (cart == null) return;
        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        cartRepository.save(cart);
    }

    @Override
    public Cart getCart(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return cartRepository.findByUser(user).orElse(Cart.builder().user(user).build());
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        Cart cart = cartRepository.findByUser(user).orElse(null);
        if (cart == null) return;
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private void requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0.");
        }
    }
}

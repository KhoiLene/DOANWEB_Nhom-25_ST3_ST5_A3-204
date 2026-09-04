package com.shopchain.repository;

import com.shopchain.entity.Cart;
import com.shopchain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(User user);
}

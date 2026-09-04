package com.shopchain.repository;

import com.shopchain.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {
    Optional<ProductInventory> findByProductIdAndStoreId(Long productId, Long storeId);
}

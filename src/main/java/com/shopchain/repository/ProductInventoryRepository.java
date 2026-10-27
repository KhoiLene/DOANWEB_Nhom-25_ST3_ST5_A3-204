package com.shopchain.repository;

import com.shopchain.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {
    Optional<ProductInventory> findByProductIdAndStoreId(Long productId, Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductInventory> findForUpdateByProductIdAndStoreId(Long productId, Long storeId);
    Optional<ProductInventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductInventory> findForUpdateByProductIdAndWarehouseId(Long productId, Long warehouseId);
    java.util.List<ProductInventory> findByStoreId(Long storeId);
    java.util.List<ProductInventory> findByWarehouseId(Long warehouseId);
    java.util.List<ProductInventory> findByProductId(Long productId);
}

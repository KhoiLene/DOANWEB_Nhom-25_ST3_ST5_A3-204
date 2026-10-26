package com.shopchain.repository;

import com.shopchain.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findTop100ByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
    List<InventoryMovement> findTop100ByStoreIdOrderByCreatedAtDesc(Long storeId);
}

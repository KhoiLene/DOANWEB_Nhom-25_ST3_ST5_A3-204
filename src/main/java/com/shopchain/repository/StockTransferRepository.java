package com.shopchain.repository;

import com.shopchain.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    List<StockTransfer> findBySourceWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
    List<StockTransfer> findByDestinationStoreIdAndStatusOrderByCreatedAtAsc(Long storeId, String status);
}

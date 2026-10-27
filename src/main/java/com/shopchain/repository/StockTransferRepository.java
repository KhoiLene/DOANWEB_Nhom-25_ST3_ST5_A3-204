package com.shopchain.repository;

import com.shopchain.entity.StockTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from StockTransfer transfer where transfer.id = :id")
    Optional<StockTransfer> findForUpdateById(@Param("id") Long id);

    List<StockTransfer> findBySourceWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
    List<StockTransfer> findByDestinationStoreIdAndStatusOrderByCreatedAtAsc(Long storeId, String status);
}

package com.shopchain.repository;

import com.shopchain.entity.Order;
import com.shopchain.entity.User;
import com.shopchain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orderEntity from Order orderEntity where orderEntity.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    List<Order> findByUserOrderByOrderDateDesc(User user);
    List<Order> findByStoreId(Long storeId);
    List<Order> findByStoreIdAndFulfillmentWarehouseIsNullOrderByOrderDateDesc(Long storeId);
    List<Order> findByStatus(String status);
    List<Order> findByFulfillmentWarehouseIdAndStatusInOrderByOrderDateAsc(Long warehouseId, List<String> statuses);
}

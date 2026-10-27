package com.shopchain.repository;

import com.shopchain.entity.Order;
import com.shopchain.entity.User;
import com.shopchain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
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
    @EntityGraph(attributePaths = {"store", "orderItems", "orderItems.product"})
    List<Order> findByUserIdAndFulfillmentTypeOrderByOrderDateDesc(Long userId, String fulfillmentType, Pageable pageable);

    @Query("select coalesce(sum(o.totalAmount), 0.0) from Order o "
            + "where o.user.id = :userId and ("
            + "(upper(coalesce(o.paymentStatus, '')) = 'PAID' and upper(coalesce(o.status, '')) not in ('CANCELLED', 'RETURNED')) "
            + "or (upper(coalesce(o.paymentMethod, '')) = 'COD' and upper(coalesce(o.status, '')) = 'DELIVERED'))")
    Double sumCompletedPaidSpendForUser(@Param("userId") Long userId);

    List<Order> findByStoreId(Long storeId);
    List<Order> findByStoreIdAndFulfillmentWarehouseIsNullOrderByOrderDateDesc(Long storeId);
    List<Order> findByStatus(String status);
    List<Order> findByFulfillmentWarehouseIdAndStatusInOrderByOrderDateAsc(Long warehouseId, List<String> statuses);
}

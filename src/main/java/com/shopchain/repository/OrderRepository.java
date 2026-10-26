package com.shopchain.repository;

import com.shopchain.entity.Order;
import com.shopchain.entity.User;
import com.shopchain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserOrderByOrderDateDesc(User user);
    List<Order> findByStoreId(Long storeId);
    List<Order> findByStoreIdAndFulfillmentWarehouseIsNullOrderByOrderDateDesc(Long storeId);
    List<Order> findByStatus(String status);
    List<Order> findByFulfillmentWarehouseIdAndStatusInOrderByOrderDateAsc(Long warehouseId, List<String> statuses);
}

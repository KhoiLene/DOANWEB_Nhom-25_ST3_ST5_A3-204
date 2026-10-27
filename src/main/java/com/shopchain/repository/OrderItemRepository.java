package com.shopchain.repository;

import com.shopchain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("select item.product.id as productId, item.order.store.id as storeId, sum(item.quantity) as unitsSold " +
            "from OrderItem item where item.order.orderDate >= :from " +
            "and item.order.status not in ('CANCELLED', 'RETURNED') " +
            "group by item.product.id, item.order.store.id")
    List<StoreProductSales> findStoreProductSalesSince(@Param("from") LocalDateTime from);

    interface StoreProductSales {
        Long getProductId();
        Long getStoreId();
        Long getUnitsSold();
    }
}

package com.shopchain.service;

import com.shopchain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    Order placeOrder(Long userId, Long storeId, Long addressId, String paymentMethod);
    Order updateOrderStatus(Long orderId, String status);
    Page<Order> getUserOrders(Long userId, Pageable pageable);
    Page<Order> getStoreOrders(Long storeId, Pageable pageable);
    Order getOrderDetail(Long orderId);
}

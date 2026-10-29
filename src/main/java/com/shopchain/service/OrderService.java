package com.shopchain.service;

import com.shopchain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface OrderService {
    Order placeOrder(Long userId, Long storeId, Long addressId, String paymentMethod);
    Order placeOnlineOrder(Long userId, Long storeId, String name, String phone, String address,
                           String deliveryCity, String fulfillmentType, String paymentMethod,
                           Double deliveryLatitude, Double deliveryLongitude);
    Order placeOnlineOrder(Long userId, Long storeId, String name, String phone, String address,
                           String deliveryCity, String fulfillmentType, String paymentMethod,
                           Double deliveryLatitude, Double deliveryLongitude, Integer redeemPoints);
    Order updateOrderStatus(Long orderId, String status);
    Order confirmVnPayPayment(Long orderId);
    Order prepareVnPayPayment(Long orderId);
    boolean expireUnpaidVnPayOrder(Long orderId, java.time.LocalDateTime now);
    List<String> allowedStatusTargets(Order order);
    Page<Order> getUserOrders(Long userId, Pageable pageable);
    Page<Order> getStoreOrders(Long storeId, Pageable pageable);
    Order getOrderDetail(Long orderId);
}

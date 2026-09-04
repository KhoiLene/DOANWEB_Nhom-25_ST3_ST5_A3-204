package com.shopchain.service.impl;

import com.shopchain.dto.NotificationRequest;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.OrderService;
import com.shopchain.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final NotificationService notificationService;

    public OrderServiceImpl(OrderRepository orderRepository, CartRepository cartRepository,
                            UserRepository userRepository, StoreRepository storeRepository,
                            ProductInventoryRepository inventoryRepository, NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.inventoryRepository = inventoryRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public Order placeOrder(Long userId, Long storeId, Long addressId, String paymentMethod) {
        User user = userRepository.findById(userId).orElseThrow();
        Store store = storeRepository.findById(storeId).orElseThrow();
        Cart cart = cartRepository.findByUserId(user).orElseThrow(() -> new RuntimeException("Cart is empty"));

        for (CartItem item : cart.getItems()) {
            ProductInventory inv = inventoryRepository.findByProductIdAndStoreId(item.getProduct().getId(), storeId)
                    .orElseThrow(() -> new RuntimeException("Product " + item.getProduct().getName() + " not available in this store"));
            if (inv.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + item.getProduct().getName());
            }
        }

        double total = 0;
        Order order = Order.builder()
                .user(user)
                .store(store)
                .status("PENDING")
                .paymentMethod(paymentMethod)
                .paymentStatus("UNPAID")
                .build();

        for (CartItem item : cart.getItems()) {
            ProductInventory inv = inventoryRepository.findByProductIdAndStoreId(item.getProduct().getId(), storeId).get();

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(item.getProduct())
                    .quantity(item.getQuantity())
                    .price(inv.getPrice())
                    .build();

            order.getOrderItems().add(orderItem);

            total += inv.getPrice() * item.getQuantity();
            inv.setStockQuantity(inv.getStockQuantity() - item.getQuantity());
            inventoryRepository.save(inv);
            // Note: orderItem saving logic would normally be handled by JPA cascade
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);

        // --- INTEGRATE WEBSOCKET NOTIFICATION ---
        notificationService.sendNotificationToStore(storeId, NotificationRequest.builder()
                .message("New Order #" + savedOrder.getId() + " received!")
                .type("ORDER_NEW")
                .orderId(savedOrder.getId())
                .build());

        cart.getItems().clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    @Override
    @Transactional
    public Order updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        Order savedOrder = orderRepository.save(order);

        // Thông báo cho User khi đơn hàng thay đổi trạng thái
        notificationService.sendNotificationToUser(order.getUser().getId(), NotificationRequest.builder()
                .message("Order #" + orderId + " status updated to: " + status)
                .type("ORDER_STATUS_UPDATE")
                .orderId(orderId)
                .build());

        return savedOrder;
    }

    @Override
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Order> orders = orderRepository.findByUserOrderByOrderDateDesc(user);
        return new PageImpl<>(orders, pageable, orders.size());
    }

    @Override
    public Page<Order> getStoreOrders(Long storeId, Pageable pageable) {
        List<Order> orders = orderRepository.findByStoreId(storeId);
        return new PageImpl<>(orders, pageable, orders.size());
    }

    @Override
    public Order getOrderDetail(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }
}

package com.shopchain.service.impl;

import com.shopchain.dto.NotificationRequest;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.OrderService;
import com.shopchain.service.NotificationService;
import com.shopchain.service.LoyaltyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

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
    private final WarehouseRepository warehouseRepository;
    private final LoyaltyService loyaltyService;
    @Value("${shipping.store-fulfillment-radius-km:50}")
    private double storeFulfillmentRadiusKm;

    public OrderServiceImpl(OrderRepository orderRepository, CartRepository cartRepository,
                            UserRepository userRepository, StoreRepository storeRepository,
                            ProductInventoryRepository inventoryRepository, NotificationService notificationService,
                            WarehouseRepository warehouseRepository, LoyaltyService loyaltyService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.inventoryRepository = inventoryRepository;
        this.notificationService = notificationService;
        this.warehouseRepository = warehouseRepository;
        this.loyaltyService = loyaltyService;
    }

    @Override
    @Transactional
    public Order placeOnlineOrder(Long userId, Long storeId, String name, String phone, String address,
                                  String deliveryCity, String fulfillmentType, String paymentMethod,
                                  Double deliveryLatitude, Double deliveryLongitude) {
        User user = userRepository.findById(userId).orElseThrow();
        Store store = storeRepository.findById(storeId).orElseThrow();
        if (!store.isActive() || !store.isPickupEnabled()) throw new IllegalArgumentException("Chi nhánh này không nhận đơn.");
        Cart cart = cartRepository.findByUser(user).orElseThrow(() -> new IllegalArgumentException("Giỏ hàng đang trống."));
        if (cart.getItems().isEmpty()) throw new IllegalArgumentException("Giỏ hàng đang trống.");
        boolean pickup = "PICKUP".equals(fulfillmentType);
        if (!pickup && !"DELIVERY".equals(fulfillmentType)) throw new IllegalArgumentException("Vui lòng chọn cách nhận hàng.");
        if (!List.of("COD", "vnpay", "VNPAY").contains(paymentMethod)) throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        boolean otherCity = deliveryCity != null && store.getCity() != null
                && !normalize(deliveryCity).equals(normalize(store.getCity()));
        boolean outsideStoreRadius = hasValidCoordinates(deliveryLatitude, deliveryLongitude)
                && hasValidCoordinates(store.getLatitude(), store.getLongitude())
                && distanceKm(deliveryLatitude, deliveryLongitude, store.getLatitude(), store.getLongitude()) > storeFulfillmentRadiusKm;
        boolean remote = !pickup && (otherCity || outsideStoreRadius);
        Warehouse warehouse = null;
        if (remote) warehouse = chooseWarehouse(deliveryLatitude, deliveryLongitude)
                .orElseThrow(() -> new IllegalArgumentException("Đơn giao xa cần có kho đang hoạt động. Vui lòng liên hệ cửa hàng."));

        Order order = Order.builder().user(user).store(store).orderDate(java.time.LocalDateTime.now())
                .status("PENDING").paymentMethod(paymentMethod).paymentStatus("UNPAID")
                .customerName(name.trim()).customerPhone(phone.trim()).deliveryAddress(address == null ? "" : address.trim())
                .deliveryCity(deliveryCity == null ? "" : deliveryCity.trim())
                .fulfillmentType(pickup ? "PICKUP" : (remote ? "WAREHOUSE_SHIP" : "STORE_SHIP"))
                .fulfillmentWarehouse(warehouse).totalAmount(0d).build();
        // Initialize explicitly even when Lombok builder defaults differ between build outputs.
        order.setOrderItems(new java.util.ArrayList<>());
        double total = 0;
        for (CartItem item : cart.getItems()) {
            ProductInventory inventory = warehouse == null
                    ? inventoryRepository.findForUpdateByProductIdAndStoreId(item.getProduct().getId(), storeId)
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm " + item.getProduct().getName() + " chưa có tại chi nhánh."))
                    : inventoryRepository.findForUpdateByProductIdAndWarehouseId(item.getProduct().getId(), warehouse.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm " + item.getProduct().getName() + " chưa có tại kho."));
            if (inventory.getStockQuantity() < item.getQuantity())
                throw new IllegalArgumentException("Tồn kho không đủ cho sản phẩm " + item.getProduct().getName());
            inventory.setStockQuantity(inventory.getStockQuantity() - item.getQuantity());
            double unitPrice = item.getProduct().effectivePrice(inventory.getPrice(), user.getLoyaltyPoints());
            total += unitPrice * item.getQuantity();
            order.getOrderItems().add(OrderItem.builder().order(order).product(item.getProduct())
                    .quantity(item.getQuantity()).price(unitPrice).build());
        }
        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        notificationService.sendNotificationToStore(storeId, NotificationRequest.builder()
                .message("Đơn trực tuyến #" + saved.getId() + " mới được tạo")
                .type("ORDER_NEW").orderId(saved.getId()).build());
        cart.getItems().clear();
        cartRepository.save(cart);
        return saved;
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    private java.util.Optional<Warehouse> chooseWarehouse(Double latitude, Double longitude) {
        List<Warehouse> activeWarehouses = warehouseRepository.findByActiveTrue();
        if (!hasValidCoordinates(latitude, longitude)) return activeWarehouses.stream().findFirst();
        return activeWarehouses.stream()
                .filter(warehouse -> hasValidCoordinates(warehouse.getLatitude(), warehouse.getLongitude()))
                .min(java.util.Comparator.comparingDouble(warehouse -> distanceKm(latitude, longitude,
                        warehouse.getLatitude(), warehouse.getLongitude())))
                .or(() -> activeWarehouses.stream().findFirst());
    }

    private boolean hasValidCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double dLat = Math.toRadians(latitude2 - latitude1);
        double dLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    @Transactional
    public Order placeOrder(Long userId, Long storeId, Long addressId, String paymentMethod) {
        User user = userRepository.findById(userId).orElseThrow();
        Store store = storeRepository.findById(storeId).orElseThrow();
        Cart cart = cartRepository.findByUser(user).orElseThrow(() -> new RuntimeException("Cart is empty"));

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

            double unitPrice = item.getProduct().effectivePrice(inv.getPrice(), user.getLoyaltyPoints());
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(item.getProduct())
                    .quantity(item.getQuantity())
                    .price(unitPrice)
                    .build();

            order.getOrderItems().add(orderItem);

            total += unitPrice * item.getQuantity();
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
        if ("CANCELLED".equals(status) && !"CANCELLED".equals(order.getStatus())) {
            for (OrderItem item : order.getOrderItems()) {
                ProductInventory inventory = order.getFulfillmentWarehouse() == null
                        ? inventoryRepository.findForUpdateByProductIdAndStoreId(item.getProduct().getId(), order.getStore().getId()).orElse(null)
                        : inventoryRepository.findForUpdateByProductIdAndWarehouseId(item.getProduct().getId(), order.getFulfillmentWarehouse().getId()).orElse(null);
                if (inventory != null) {
                    inventory.setStockQuantity(inventory.getStockQuantity() + item.getQuantity());
                    inventoryRepository.save(inventory);
                }
            }
        }
        order.setStatus(status);
        if ("DELIVERED".equals(status) && "COD".equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus("PAID");
        }
        Order savedOrder = orderRepository.save(order);
        if ("CANCELLED".equals(status)) loyaltyService.revokeForCancelledOrder(savedOrder.getId());
        else loyaltyService.awardForPaidOrder(savedOrder.getId());

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

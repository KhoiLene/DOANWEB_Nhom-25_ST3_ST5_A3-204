package com.shopchain.service.impl;

import com.shopchain.dto.NotificationRequest;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.OrderService;
import com.shopchain.service.NotificationService;
import com.shopchain.service.LoyaltyService;
import com.shopchain.service.OrderPricingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final NotificationService notificationService;
    private final WarehouseRepository warehouseRepository;
    private final LoyaltyService loyaltyService;
    private final ProductRepository productRepository;
    private final OrderPricingService pricingService;
    @Value("${shipping.store-fulfillment-radius-km:50}")
    private double storeFulfillmentRadiusKm;
    @Value("${orders.pickup.ready-minutes:30}")
    private int pickupReadyMinutes = 30;

    public OrderServiceImpl(OrderRepository orderRepository, CartRepository cartRepository,
                            UserRepository userRepository, StoreRepository storeRepository,
                            ProductInventoryRepository inventoryRepository, InventoryMovementRepository movementRepository,
                            NotificationService notificationService,
                            WarehouseRepository warehouseRepository, LoyaltyService loyaltyService,
                            ProductRepository productRepository, OrderPricingService pricingService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.notificationService = notificationService;
        this.warehouseRepository = warehouseRepository;
        this.loyaltyService = loyaltyService;
        this.productRepository = productRepository;
        this.pricingService = pricingService;
    }

    @Override
    @Transactional
    public Order placeOnlineOrder(Long userId, Long storeId, String name, String phone, String address,
                                  String deliveryCity, String fulfillmentType, String paymentMethod,
                                  Double deliveryLatitude, Double deliveryLongitude) {
        return placeOnlineOrder(userId, storeId, name, phone, address, deliveryCity, fulfillmentType,
                paymentMethod, deliveryLatitude, deliveryLongitude, 0);
    }

    @Override
    @Transactional
    public Order placeOnlineOrder(Long userId, Long storeId, String name, String phone, String address,
                                  String deliveryCity, String fulfillmentType, String paymentMethod,
                                  Double deliveryLatitude, Double deliveryLongitude, Integer redeemPoints) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        Store store = storeRepository.findForUpdateById(storeId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
        if (!store.isActive()) throw new IllegalArgumentException("Chi nhánh này hiện không hoạt động.");
        Cart cart = cartRepository.findByUser(user).orElseThrow(() -> new IllegalArgumentException("Giỏ hàng đang trống."));
        if (cart.getItems().isEmpty()) throw new IllegalArgumentException("Giỏ hàng đang trống.");
        boolean pickup = "PICKUP".equals(fulfillmentType);
        if (!pickup && !"DELIVERY".equals(fulfillmentType)) throw new IllegalArgumentException("Vui lòng chọn cách nhận hàng.");
        if (pickup && !store.isPickupEnabled()) throw new IllegalArgumentException("Chi nhánh này không nhận đơn lấy tại cửa hàng.");
        if (paymentMethod == null || !("COD".equalsIgnoreCase(paymentMethod)
                || "BANK_TRANSFER".equalsIgnoreCase(paymentMethod)))
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        boolean otherCity = deliveryCity != null && store.getCity() != null
                && !normalize(deliveryCity).equals(normalize(store.getCity()));
        boolean outsideStoreRadius = hasValidCoordinates(deliveryLatitude, deliveryLongitude)
                && hasValidCoordinates(store.getLatitude(), store.getLongitude())
                && distanceKm(deliveryLatitude, deliveryLongitude, store.getLatitude(), store.getLongitude()) > storeFulfillmentRadiusKm;
        boolean remote = !pickup && (otherCity || outsideStoreRadius);
        Warehouse warehouse = null;
        if (remote) {
            Warehouse candidate = chooseWarehouse(deliveryLatitude, deliveryLongitude)
                    .orElseThrow(() -> new IllegalArgumentException("Đơn giao xa cần có kho đang hoạt động. Vui lòng liên hệ cửa hàng."));
            warehouse = warehouseRepository.findForUpdateById(candidate.getId())
                    .filter(Warehouse::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Kho xử lý đơn hiện không hoạt động."));
        }

        List<CartItem> orderedItems = cart.getItems().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getProduct().getId())).toList();
        List<Product> lockedProducts = new java.util.ArrayList<>();
        for (CartItem item : orderedItems) {
            Product product = productRepository.findForUpdateById(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không còn tồn tại."));
            if (Boolean.FALSE.equals(product.getIsActive()))
                throw new IllegalArgumentException("Sản phẩm " + product.getName() + " hiện không còn được phép bán.");
            lockedProducts.add(product);
        }
        List<CartItem> currentItems = new java.util.ArrayList<>();
        for (int i = 0; i < orderedItems.size(); i++) {
            currentItems.add(CartItem.builder().product(lockedProducts.get(i)).quantity(orderedItems.get(i).getQuantity()).build());
        }
        OrderPricingService.Quote quote = pricingService.quote(currentItems, user.getLoyaltyPoints(),
                pickup ? "PICKUP" : "DELIVERY", redeemPoints);
        user.setLoyaltyPoints(Math.max(0, user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints())
                - quote.redeemedPoints());
        if (quote.redeemedPoints() > 0) userRepository.save(user);

        Order order = Order.builder().user(user).store(store).orderDate(java.time.LocalDateTime.now())
                .status("PENDING").paymentMethod(paymentMethod).paymentStatus("UNPAID")
                .customerName(name.trim()).customerPhone(phone.trim()).deliveryAddress(address == null ? "" : address.trim())
                .deliveryCity(deliveryCity == null ? "" : deliveryCity.trim())
                .fulfillmentType(pickup ? "PICKUP" : (remote ? "WAREHOUSE_SHIP" : "STORE_SHIP"))
                .fulfillmentWarehouse(warehouse).totalAmount(quote.total())
                .subtotalAmount(quote.subtotal()).discountAmount(quote.discount()).shippingAmount(quote.shipping())
                .loyaltyPointsRedeemed(quote.redeemedPoints()).loyaltyPointsRestored(false)
                .paymentExpiresAt("BANK_TRANSFER".equalsIgnoreCase(paymentMethod) ? LocalDateTime.now().plusMinutes(30) : null).build();
        // Initialize explicitly even when Lombok builder defaults differ between build outputs.
        order.setOrderItems(new java.util.ArrayList<>());
        List<InventoryMovement> movements = new java.util.ArrayList<>();
        for (int i = 0; i < orderedItems.size(); i++) {
            CartItem item = orderedItems.get(i);
            Product product = lockedProducts.get(i);
            ProductInventory inventory = warehouse == null
                    ? inventoryRepository.findForUpdateByProductIdAndStoreId(product.getId(), storeId)
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm " + product.getName() + " chưa có tại chi nhánh."))
                    : inventoryRepository.findForUpdateByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm " + product.getName() + " chưa có tại kho."));
            if (inventory.availableQuantity() < item.getQuantity())
                throw new IllegalArgumentException("Tồn kho không đủ cho sản phẩm " + product.getName());
            inventory.setStockQuantity(inventory.getStockQuantity() - item.getQuantity());
            inventoryRepository.save(inventory);
            InventoryMovement.InventoryMovementBuilder movement = InventoryMovement.builder()
                    .product(product).performedBy(user).movementType("ONLINE_SALE")
                    .quantity(item.getQuantity());
            if (warehouse == null) movement.store(store);
            else movement.warehouse(warehouse);
            movements.add(movement.build());
            java.math.BigDecimal unitPrice = pricingService.saleUnitPrice(product, user.getLoyaltyPoints());
            order.getOrderItems().add(OrderItem.builder().order(order).product(product)
                    .quantity(item.getQuantity()).price(unitPrice)
                    .listUnitPrice(pricingService.listUnitPrice(product)).build());
        }
        Order saved = orderRepository.save(order);
        movements.forEach(movement -> movement.setNote("Đơn trực tuyến #" + saved.getId()));
        movementRepository.saveAll(movements);
        NotificationRequest newOrderNotice = NotificationRequest.builder()
                .message("Đơn trực tuyến #" + saved.getId() + ("BANK_TRANSFER".equalsIgnoreCase(paymentMethod)
                        ? " đang chờ xác nhận chuyển khoản" : " mới được tạo"))
                .type("ORDER_NEW").orderId(saved.getId()).build();
        if (warehouse == null) {
            notificationService.sendNotificationToStore(storeId, newOrderNotice);
        } else {
            // Đơn giao xa được xử lý tại kho; gửi đúng người phụ trách thay vì báo nhầm cho chi nhánh nhận đơn.
            userRepository.findByWarehouse_Id(warehouse.getId()).stream()
                    .filter(warehouseStaff -> warehouseStaff.getId() != null)
                    .forEach(warehouseStaff -> notificationService.sendNotificationToUser(warehouseStaff.getId(), newOrderNotice));
        }
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
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
        return placeOnlineOrder(userId, storeId, user.getFullName(), user.getPhone(), "", store.getCity(),
                "DELIVERY", paymentMethod, null, null);
    }

    @Override
    @Transactional
    public Order updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        String target = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if ("CANCELLED".equals(target) && "PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("Đơn đã thanh toán không thể hủy khi chưa xử lý hoàn tiền.");
        }
        if (!OrderStatus.canTransition(order, target)) {
            throw new IllegalStateException("Không thể chuyển đơn từ " + order.getStatus() + " sang " + target + ".");
        }
        if ("CANCELLED".equals(target)) {
            releaseOrderStock(order, "ORDER_CANCEL_RETURN", "Hoàn tồn đơn hủy #" + order.getId());
            restoreRedeemedPoints(order);
            if ("VNPAY".equalsIgnoreCase(order.getPaymentMethod())
                    || "BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod())) order.setPaymentStatus("CANCELLED");
        }
        order.setStatus(target);
        if ("DELIVERED".equals(target) && "COD".equalsIgnoreCase(order.getPaymentMethod())) order.setPaymentStatus("PAID");
        if ("DELIVERED".equals(target) && "PAID".equalsIgnoreCase(order.getPaymentStatus()) && order.getPaidAt() == null)
            order.setPaidAt(LocalDateTime.now());
        if ("PROCESSING".equals(target) && "PICKUP".equalsIgnoreCase(order.getFulfillmentType())
                && order.getEstimatedPickupAt() == null)
            order.setEstimatedPickupAt(LocalDateTime.now().plusMinutes(Math.max(10, pickupReadyMinutes)));
        Order savedOrder = orderRepository.save(order);
        if ("PAID".equalsIgnoreCase(savedOrder.getPaymentStatus())) loyaltyService.awardForPaidOrder(savedOrder.getId());
        if (savedOrder.getUser() != null) {
            notificationService.sendNotificationToUser(savedOrder.getUser().getId(), NotificationRequest.builder()
                    .message("Đơn hàng #" + orderId + " đã cập nhật: " + orderStatusLabel(target))
                    .type("ORDER_STATUS_UPDATE").orderId(orderId).build());
        }
        return savedOrder;
    }

    @Override
    @Transactional
    public Order prepareVnPayPayment(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (!"VNPAY".equalsIgnoreCase(order.getPaymentMethod()) || !"PENDING".equalsIgnoreCase(order.getStatus())
                || !"UNPAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("Đơn hàng không còn ở trạng thái chờ thanh toán.");
        }
        if (order.getPaymentExpiresAt() == null) {
            LocalDateTime orderExpiry = order.getOrderDate() == null
                    ? LocalDateTime.now() : order.getOrderDate().plusMinutes(15);
            order.setPaymentExpiresAt(orderExpiry);
        }
        if (!order.getPaymentExpiresAt().isAfter(LocalDateTime.now())) {
            expireLockedOrder(order, LocalDateTime.now());
            return order;
        }
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order confirmVnPayPayment(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "RETURNED".equalsIgnoreCase(order.getStatus()))
                throw new IllegalStateException("Đơn thanh toán đã kết thúc ở trạng thái không thể xử lý.");
            return order;
        }
        if (!"VNPAY".equalsIgnoreCase(order.getPaymentMethod()) || !"PENDING".equalsIgnoreCase(order.getStatus())
                || !"UNPAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("Đơn hàng không còn ở trạng thái chờ thanh toán.");
        }
        if (order.getPaymentExpiresAt() == null || !order.getPaymentExpiresAt().isAfter(LocalDateTime.now())) {
            expireLockedOrder(order, LocalDateTime.now());
            return order;
        }
        order.setPaymentStatus("PAID");
        order.setStatus("CONFIRMED");
        markPaidAndEstimatePickup(order);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order confirmBankTransferPayment(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (!"BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod()))
            throw new IllegalStateException("Đơn hàng này không dùng chuyển khoản VietQR.");
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) return order;
        if (!"PENDING".equalsIgnoreCase(order.getStatus())
                || !"UNPAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("Đơn hàng không còn chờ xác nhận chuyển khoản.");
        }
        if (order.getPaymentExpiresAt() != null && !order.getPaymentExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Đơn hàng đã hết thời gian giữ. Vui lòng kiểm tra giao dịch và liên hệ hỗ trợ nếu khách đã chuyển tiền.");
        }
        order.setPaymentStatus("PAID");
        order.setStatus("CONFIRMED");
        markPaidAndEstimatePickup(order);
        Order saved = orderRepository.save(order);
        loyaltyService.awardForPaidOrder(saved.getId());
        if (saved.getUser() != null) {
            notificationService.sendNotificationToUser(saved.getUser().getId(), NotificationRequest.builder()
                    .message("Đã xác nhận thanh toán cho đơn hàng #" + saved.getId() + ". Đơn đang chờ chi nhánh chuẩn bị.")
                    .type("ORDER_STATUS_UPDATE").orderId(saved.getId()).build());
        }
        return saved;
    }

    private void markPaidAndEstimatePickup(Order order) {
        LocalDateTime now = LocalDateTime.now();
        order.setPaidAt(now);
        if ("PICKUP".equalsIgnoreCase(order.getFulfillmentType()))
            order.setEstimatedPickupAt(now.plusMinutes(Math.max(10, pickupReadyMinutes)));
    }

    private String orderStatusLabel(String status) {
        return switch (status) {
            case "CONFIRMED" -> "đã xác nhận thanh toán";
            case "PROCESSING" -> "đang được chuẩn bị";
            case "SHIPPING" -> "đã đóng gói và bàn giao cho đơn vị giao hàng";
            case "READY_FOR_PICKUP" -> "đã sẵn sàng để nhận tại chi nhánh";
            case "DELIVERED" -> "đã giao thành công";
            case "CANCELLED" -> "đã hủy";
            case "RETURNED" -> "đã hoàn trả";
            default -> status.toLowerCase(java.util.Locale.ROOT);
        };
    }

    @Override
    @Transactional
    public boolean expireUnpaidVnPayOrder(Long orderId, LocalDateTime now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !isExpirableVnPay(order) || order.getPaymentExpiresAt().isAfter(now)) return false;
        expireLockedOrder(order, now);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> allowedStatusTargets(Order order) {
        return OrderStatus.allowedTargets(order);
    }

    private boolean isExpirableVnPay(Order order) {
        return "PENDING".equalsIgnoreCase(order.getStatus())
                && ("VNPAY".equalsIgnoreCase(order.getPaymentMethod())
                    || "BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod()))
                && "UNPAID".equalsIgnoreCase(order.getPaymentStatus())
                && order.getPaymentExpiresAt() != null;
    }

    private void expireLockedOrder(Order order, LocalDateTime now) {
        if (!isExpirableVnPay(order) || order.getPaymentExpiresAt().isAfter(now)) return;
        releaseOrderStock(order, "ORDER_PAYMENT_EXPIRED_RETURN", "Hoàn tồn đơn thanh toán hết hạn #" + order.getId());
        restoreRedeemedPoints(order);
        order.setStatus("CANCELLED");
        order.setPaymentStatus("EXPIRED");
        orderRepository.save(order);
    }

    private void restoreRedeemedPoints(Order order) {
        int redeemed = order.getLoyaltyPointsRedeemed() == null ? 0 : order.getLoyaltyPointsRedeemed();
        if (redeemed <= 0 || Boolean.TRUE.equals(order.getLoyaltyPointsRestored()) || order.getUser() == null) return;
        User user = userRepository.findByIdForUpdate(order.getUser().getId()).orElseThrow(
                () -> new IllegalStateException("Không tìm thấy khách để hoàn điểm đơn hàng."));
        user.setLoyaltyPoints(Math.max(0, user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()) + redeemed);
        userRepository.save(user);
        order.setLoyaltyPointsRestored(true);
    }

    private void releaseOrderStock(Order order, String movementType, String note) {
        List<InventoryMovement> movements = new java.util.ArrayList<>();
        for (OrderItem item : order.getOrderItems().stream()
                .sorted(java.util.Comparator.comparing(line -> line.getProduct().getId())).toList()) {
            ProductInventory inventory = order.getFulfillmentWarehouse() == null
                    ? inventoryRepository.findForUpdateByProductIdAndStoreId(item.getProduct().getId(), order.getStore().getId())
                        .orElseThrow(() -> new IllegalStateException("Thiếu tồn chi nhánh để hoàn đơn #" + order.getId()))
                    : inventoryRepository.findForUpdateByProductIdAndWarehouseId(item.getProduct().getId(), order.getFulfillmentWarehouse().getId())
                        .orElseThrow(() -> new IllegalStateException("Thiếu tồn kho để hoàn đơn #" + order.getId()));
            inventory.setStockQuantity(inventory.getStockQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);
            InventoryMovement.InventoryMovementBuilder movement = InventoryMovement.builder()
                    .product(item.getProduct()).movementType(movementType).quantity(item.getQuantity()).note(note);
            if (order.getFulfillmentWarehouse() == null) movement.store(order.getStore());
            else movement.warehouse(order.getFulfillmentWarehouse());
            movements.add(movement.build());
        }
        movementRepository.saveAll(movements);
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

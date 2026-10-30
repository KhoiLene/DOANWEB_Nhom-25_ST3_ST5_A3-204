package com.shopchain.controller;

import com.shopchain.entity.Order;
import com.shopchain.dto.NotificationRequest;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.SalesInvoiceRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer/orders")
public class CustomerOrderController {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final NotificationService notificationService;

    public CustomerOrderController(UserRepository userRepository, OrderRepository orderRepository,
                                   SalesInvoiceRepository invoiceRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/tracking")
    @Transactional(readOnly = true)
    public List<OrderTracking> tracking(Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        return orderRepository.findByUserIdOrderByOrderDateDesc(user.getId(), PageRequest.of(0, 20)).stream()
                .map(order -> toTracking(order))
                .toList();
    }

    @org.springframework.web.bind.annotation.PostMapping("/{orderId}/report-transfer")
    @Transactional
    public ResponseEntity<?> reportTransfer(@org.springframework.web.bind.annotation.PathVariable Long orderId,
                                            Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Không tìm thấy đơn hàng."));
        if (!"BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod()))
            return ResponseEntity.badRequest().body(Map.of("error", "Đơn hàng này không thanh toán bằng chuyển khoản."));
        if (order.getTransferReportedAt() != null)
            return ResponseEntity.ok(Map.of("reported", true, "reportedAt", order.getTransferReportedAt().toString()));
        if (!"PENDING".equalsIgnoreCase(order.getStatus()) || !"UNPAID".equalsIgnoreCase(order.getPaymentStatus()))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Đơn không còn chờ nhân viên đối chiếu chuyển khoản."));
        LocalDateTime now = LocalDateTime.now();
        if (order.getPaymentExpiresAt() != null && !order.getPaymentExpiresAt().isAfter(now))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Thời gian thanh toán của đơn đã hết hạn. Vui lòng liên hệ cửa hàng nếu bạn đã chuyển tiền."));

        order.setTransferReportedAt(now);
        orderRepository.save(order);
        NotificationRequest notice = NotificationRequest.builder()
                .type("TRANSFER_REPORTED").orderId(order.getId())
                .message("Khách " + (order.getCustomerName() == null ? user.getUsername() : order.getCustomerName())
                        + " báo đã chuyển khoản đơn #" + order.getId() + ". Nhân viên cần đối chiếu giao dịch thực tế trước khi xác nhận.")
                .build();
        if (order.getFulfillmentWarehouse() != null) {
            userRepository.findByWarehouse_Id(order.getFulfillmentWarehouse().getId()).stream()
                    .filter(warehouseStaff -> warehouseStaff.getId() != null)
                    .forEach(warehouseStaff -> notificationService.sendNotificationToUser(warehouseStaff.getId(), notice));
        } else if (order.getStore() != null) {
            notificationService.sendNotificationToStore(order.getStore().getId(), notice);
        }
        return ResponseEntity.ok(Map.of("reported", true, "reportedAt", now.toString()));
    }

    private OrderTracking toTracking(Order order) {
        var invoice = invoiceRepository.findByOrderId(order.getId()).orElse(null);
        return new OrderTracking(order.getId(), order.getStatus(), order.getPaymentStatus(), order.getPaymentMethod(),
                order.getFulfillmentType(), order.getStore() == null ? "Chi nhánh" : order.getStore().getName(),
                order.getStore() == null ? "" : order.getStore().getAddress(),
                order.getDeliveryAddress(), order.getDeliveryCity(),
                order.getFulfillmentWarehouse() == null ? null : order.getFulfillmentWarehouse().getName(),
                order.getOrderDate(), order.getPaidAt(), order.getEstimatedPickupAt(), order.getPaymentExpiresAt(),
                order.getTransferReportedAt(),
                order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount(),
                order.getOrderItems() == null ? List.of() : order.getOrderItems().stream()
                        .map(item -> new TrackingItem(item.getProduct() == null ? "Sản phẩm" : item.getProduct().getName(), item.getQuantity()))
                        .toList(),
                invoice == null ? null : invoice.getId(), invoice == null ? null : invoice.getInvoiceNumber());
    }

    public record TrackingItem(String productName, Integer quantity) { }

    public record OrderTracking(Long id, String status, String paymentStatus, String paymentMethod,
                                String fulfillmentType, String storeName, String storeAddress,
                                String deliveryAddress, String deliveryCity, String warehouseName,
                                LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime estimatedPickupAt,
                                LocalDateTime paymentExpiresAt, LocalDateTime transferReportedAt,
                                BigDecimal totalAmount, List<TrackingItem> items,
                                Long invoiceId, String invoiceNumber) { }
}

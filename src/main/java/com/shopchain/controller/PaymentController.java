package com.shopchain.controller;

import com.shopchain.dto.PaymentResponse;
import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.PaymentService;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.LoyaltyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final InvoiceService invoiceService;
    private final LoyaltyService loyaltyService;
    private final UserRepository userRepository;

    public PaymentController(PaymentService paymentService, OrderRepository orderRepository,
                             InvoiceService invoiceService, LoyaltyService loyaltyService,
                             UserRepository userRepository) {
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.invoiceService = invoiceService;
        this.loyaltyService = loyaltyService;
        this.userRepository = userRepository;
    }

    @GetMapping("/vnpay")
    public ResponseEntity<?> createVnPayPayment(@RequestParam Long orderId, Authentication authentication) {
        if (orderId == null || orderId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "Mã đơn hàng không hợp lệ."));
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        boolean administrator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!administrator) {
            var currentUser = authentication == null ? null : userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser == null || order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.notFound().build();
            }
        }
        if (!"VNPAY".equalsIgnoreCase(order.getPaymentMethod())
                || "PAID".equalsIgnoreCase(order.getPaymentStatus())
                || order.getTotalAmount() == null || order.getTotalAmount() <= 0
                || "CANCELLED".equalsIgnoreCase(order.getStatus())
                || "RETURNED".equalsIgnoreCase(order.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Đơn hàng không thể thanh toán qua VNPay."));
        }

        String paymentUrl = paymentService.createVnPayPaymentUrl(order.getId(), order.getTotalAmount());

        return ResponseEntity.ok(PaymentResponse.builder()
                .status("SUCCESS")
                .message("Payment URL generated")
                .paymentUrl(paymentUrl)
                .build());
    }

    @GetMapping("/vnpay-return")
    @Transactional
    public ResponseEntity<String> vnpayReturn(@RequestParam Map<String, String> params) {
        if (params == null || !paymentService.verifyVnPayPayment(params)) {
            return ResponseEntity.badRequest().body("Payment Failed!");
        }

        try {
            String transactionReference = params.get("vnp_TxnRef");
            if (transactionReference == null || !transactionReference.matches("[1-9][0-9]{0,17}")) {
                return ResponseEntity.badRequest().body("Payment Failed!");
            }
            Order order = orderRepository.findByIdForUpdate(Long.parseLong(transactionReference)).orElse(null);
            if (order == null || order.getTotalAmount() == null
                    || !"VNPAY".equalsIgnoreCase(order.getPaymentMethod())
                    || !matchesAmount(params.get("vnp_Amount"), order.getTotalAmount())) {
                return ResponseEntity.badRequest().body("Payment Failed!");
            }

            if (!"00".equals(params.get("vnp_ResponseCode"))) {
                return ResponseEntity.badRequest().body("Payment Failed!");
            }
            if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                return ResponseEntity.ok("Payment Successful! Your order is being processed.");
            }
            if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "RETURNED".equalsIgnoreCase(order.getStatus())) {
                return ResponseEntity.badRequest().body("Payment Failed!");
            }

            order.setPaymentStatus("PAID");
            if ("PENDING".equalsIgnoreCase(order.getStatus())) order.setStatus("CONFIRMED");
            orderRepository.save(order);
            loyaltyService.awardForPaidOrder(order.getId());
            invoiceService.issueForPaidOrder(order.getId());
            return ResponseEntity.ok("Payment Successful! Your order is being processed.");
        } catch (NumberFormatException | ArithmeticException exception) {
            return ResponseEntity.badRequest().body("Payment Failed!");
        }
    }

    private boolean matchesAmount(String callbackAmount, Double orderAmount) {
        if (callbackAmount == null || !callbackAmount.matches("[0-9]{1,18}")) return false;
        long expected = BigDecimal.valueOf(orderAmount).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Long.parseLong(callbackAmount) == expected;
    }
}

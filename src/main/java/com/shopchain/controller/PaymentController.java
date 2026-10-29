package com.shopchain.controller;

import com.shopchain.dto.PaymentResponse;
import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.PaymentService;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.LoyaltyService;
import com.shopchain.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

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
    private final OrderService orderService;

    public PaymentController(PaymentService paymentService, OrderRepository orderRepository,
                             InvoiceService invoiceService, LoyaltyService loyaltyService,
                             UserRepository userRepository, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.invoiceService = invoiceService;
        this.loyaltyService = loyaltyService;
        this.userRepository = userRepository;
        this.orderService = orderService;
    }

    @GetMapping("/vnpay")
    public ResponseEntity<?> createVnPayPayment(@RequestParam Long orderId, Authentication authentication,
                                                 HttpServletRequest request) {
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
        if (!"VNPAY".equalsIgnoreCase(order.getPaymentMethod())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Đơn hàng không thể thanh toán qua VNPay."));
        }

        String paymentUrl;
        try {
            order = orderService.prepareVnPayPayment(orderId);
            if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Đơn hàng không thể thanh toán qua VNPay."));
            }
            paymentUrl = paymentService.createVnPayPaymentUrl(order.getId(), order.getTotalAmount(), request.getRemoteAddr(), order.getPaymentExpiresAt());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }

        return ResponseEntity.ok(PaymentResponse.builder()
                .status("SUCCESS")
                .message("Đang mở thanh toán QR VNPay")
                .paymentUrl(paymentUrl)
                .build());
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<?> vnpayReturn(@RequestParam Map<String, String> params) {
        if (params == null || !paymentService.verifyVnPayPayment(params)) {
            return ResponseEntity.badRequest().body("Payment Failed!");
        }

        try {
            String transactionReference = params.get("vnp_TxnRef");
            if (transactionReference == null || !transactionReference.matches("[1-9][0-9]{0,17}")) {
                return ResponseEntity.badRequest().body("Payment Failed!");
            }
            Order order = orderRepository.findById(Long.parseLong(transactionReference)).orElse(null);
            if (order == null || order.getTotalAmount() == null
                    || !"VNPAY".equalsIgnoreCase(order.getPaymentMethod())
                    || !matchesAmount(params.get("vnp_Amount"), order.getTotalAmount())) {
                return ResponseEntity.badRequest().body("Payment Failed!");
            }

            if (!"00".equals(params.get("vnp_ResponseCode"))
                    || !"00".equals(params.get("vnp_TransactionStatus"))) {
                return orderResultRedirect(order.getId(), "failed");
            }
            Order confirmed = orderService.confirmVnPayPayment(order.getId());
            if (!"PAID".equalsIgnoreCase(confirmed.getPaymentStatus())) return orderResultRedirect(order.getId(), "failed");
            loyaltyService.awardForPaidOrder(confirmed.getId());
            invoiceService.issueForPaidOrder(confirmed.getId());
            return orderResultRedirect(order.getId(), "success");
        } catch (NumberFormatException | ArithmeticException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body("Payment Failed!");
        }
    }

    private ResponseEntity<?> orderResultRedirect(Long orderId, String result) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/order-success?orderId=" + orderId + "&payment=" + result)
                .build();
    }

    private boolean matchesAmount(String callbackAmount, BigDecimal orderAmount) {
        if (callbackAmount == null || !callbackAmount.matches("[0-9]{1,18}")) return false;
        long expected = orderAmount.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Long.parseLong(callbackAmount) == expected;
    }
}

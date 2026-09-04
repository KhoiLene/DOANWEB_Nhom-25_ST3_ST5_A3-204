package com.shopchain.controller;

import com.shopchain.dto.PaymentResponse;
import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    public PaymentController(PaymentService paymentService, OrderRepository orderRepository) {
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/vnpay")
    public ResponseEntity<PaymentResponse> createVnPayPayment(@RequestParam Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String paymentUrl = paymentService.createVnPayPaymentUrl(order.getId(), order.getTotalAmount());

        return ResponseEntity.ok(PaymentResponse.builder()
                .status("SUCCESS")
                .message("Payment URL generated")
                .paymentUrl(paymentUrl)
                .build());
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<String> vnpayReturn(@RequestParam Map<String, String> params) {
        boolean isValid = paymentService.verifyVnPayPayment(params);

        if (isValid) {
            String orderId = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");

            if ("00".equals(responseCode)) {
                // Cập nhật đơn hàng thành đã thanh toán
                Order order = orderRepository.findById(Long.parseLong(orderId)).orElseThrow();
                order.setPaymentStatus("PAID");
                order.setStatus("CONFIRMED"); // Chuyển sang xác nhận sau khi trả tiền
                orderRepository.save(order);
                return ResponseEntity.ok("Payment Successful! Your order is being processed.");
            }
        }
        return ResponseEntity.badRequest().body("Payment Failed!");
    }
}

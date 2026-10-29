package com.shopchain.service;

import com.shopchain.dto.PaymentResponse;
import java.util.Map;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public interface PaymentService {
    String createVnPayPaymentUrl(Long orderId, BigDecimal amount, String clientIp, LocalDateTime expiresAt);
    boolean verifyVnPayPayment(Map<String, String> params);
}

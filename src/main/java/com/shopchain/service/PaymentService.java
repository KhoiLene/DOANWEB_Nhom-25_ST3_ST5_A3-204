package com.shopchain.service;

import com.shopchain.dto.PaymentResponse;
import java.util.Map;

public interface PaymentService {
    String createVnPayPaymentUrl(Long orderId, Double amount, String clientIp);
    boolean verifyVnPayPayment(Map<String, String> params);
}

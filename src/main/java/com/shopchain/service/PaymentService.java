package com.shopchain.service;

import com.shopchain.dto.PaymentResponse;
import java.util.Map;

public interface PaymentService {
    String createVnPayPaymentUrl(Long orderId, Double amount);
    boolean verifyVnPayPayment(Map<String, String> params);
}

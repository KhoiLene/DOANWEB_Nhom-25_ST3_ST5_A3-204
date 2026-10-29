package com.shopchain.service;

import com.shopchain.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UnpaidVnPayOrderExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(UnpaidVnPayOrderExpiryJob.class);
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public UnpaidVnPayOrderExpiryJob(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${orders.vnpay.expiry-scan-ms:30000}")
    public void expirePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        for (Long orderId : orderRepository.findExpiredUnpaidVnPayOrderIds(now, PageRequest.of(0, 100))) {
            try {
                orderService.expireUnpaidVnPayOrder(orderId, now);
            } catch (RuntimeException exception) {
                log.warn("Could not expire unpaid VNPay order {} in this scan", orderId, exception);
            }
        }
    }
}

package com.shopchain.service;

import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyService {
    private static final java.math.BigDecimal AMOUNT_PER_POINT = new java.math.BigDecimal("10000");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public LoyaltyService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void awardForPaidOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (Boolean.TRUE.equals(order.getLoyaltyPointsAwarded())
                || !"PAID".equalsIgnoreCase(order.getPaymentStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return;
        }

        java.math.BigDecimal eligibleSpend = order.getSubtotalAmount() == null
                ? (order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount())
                : order.getSubtotalAmount().subtract(order.getDiscountAmount() == null
                        ? java.math.BigDecimal.ZERO : order.getDiscountAmount()).max(java.math.BigDecimal.ZERO);
        int points = eligibleSpend.divideToIntegralValue(AMOUNT_PER_POINT).intValue();
        var user = userRepository.findByIdForUpdate(order.getUser().getId()).orElseThrow();
        user.setLoyaltyPoints(Math.max(0, user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()) + points);
        order.setLoyaltyPointsAwarded(true);
        userRepository.save(user);
        orderRepository.save(order);
    }
}

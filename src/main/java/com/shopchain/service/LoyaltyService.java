package com.shopchain.service;

import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyService {
    private static final double AMOUNT_PER_POINT = 10_000d;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public LoyaltyService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void awardForPaidOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (Boolean.TRUE.equals(order.getLoyaltyPointsAwarded())
                || !"PAID".equalsIgnoreCase(order.getPaymentStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return;
        }

        int points = (int) Math.floor(Math.max(0, order.getTotalAmount()) / AMOUNT_PER_POINT);
        var user = order.getUser();
        user.setLoyaltyPoints(Math.max(0, user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()) + points);
        order.setLoyaltyPointsAwarded(true);
        userRepository.save(user);
        orderRepository.save(order);
    }

    @Transactional
    public void revokeForCancelledOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!Boolean.TRUE.equals(order.getLoyaltyPointsAwarded())) return;
        int points = (int) Math.floor(Math.max(0, order.getTotalAmount()) / AMOUNT_PER_POINT);
        var user = order.getUser();
        user.setLoyaltyPoints(Math.max(0, (user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()) - points));
        order.setLoyaltyPointsAwarded(false);
        userRepository.save(user);
        orderRepository.save(order);
    }
}

package com.shopchain.service;

import com.shopchain.entity.Order;
import com.shopchain.entity.User;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoyaltyServiceTest {
    @Test
    void duplicateAwardIsIdempotentAndExcludesShippingAndDiscountFromEligibleSpend() {
        OrderRepository orders = mock(OrderRepository.class);
        UserRepository users = mock(UserRepository.class);
        User buyer = User.builder().id(8L).loyaltyPoints(2).build();
        Order order = Order.builder().id(15L).user(buyer).status("CONFIRMED").paymentStatus("PAID")
                .totalAmount(new BigDecimal("530000")).subtotalAmount(new BigDecimal("520000"))
                .discountAmount(new BigDecimal("40000")).shippingAmount(new BigDecimal("50000"))
                .loyaltyPointsAwarded(false).build();
        when(orders.findByIdForUpdate(15L)).thenReturn(Optional.of(order));
        when(users.findByIdForUpdate(8L)).thenReturn(Optional.of(buyer));
        LoyaltyService service = new LoyaltyService(orders, users);

        service.awardForPaidOrder(15L);
        service.awardForPaidOrder(15L);

        assertThat(buyer.getLoyaltyPoints()).isEqualTo(50);
        assertThat(order.getLoyaltyPointsAwarded()).isTrue();
        verify(users, times(1)).save(buyer);
    }
}

package com.shopchain.controller;

import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.LoyaltyService;
import com.shopchain.service.OrderService;
import com.shopchain.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentControllerTest {
    @Test
    void repeatedValidCallbackUsesIdempotentOrderLoyaltyAndInvoiceServices() {
        PaymentService payment = mock(PaymentService.class);
        OrderRepository orders = mock(OrderRepository.class);
        InvoiceService invoices = mock(InvoiceService.class);
        LoyaltyService loyalty = mock(LoyaltyService.class);
        UserRepository users = mock(UserRepository.class);
        OrderService orderService = mock(OrderService.class);
        Order pending = Order.builder().id(22L).totalAmount(new java.math.BigDecimal("100")).paymentMethod("VNPAY")
                .paymentStatus("UNPAID").status("PENDING").build();
        Order paid = Order.builder().id(22L).totalAmount(new java.math.BigDecimal("100")).paymentMethod("VNPAY")
                .paymentStatus("PAID").status("CONFIRMED").build();
        Map<String, String> callback = Map.of("vnp_TxnRef", "22", "vnp_Amount", "10000",
                "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00");
        when(payment.verifyVnPayPayment(callback)).thenReturn(true);
        when(orders.findById(22L)).thenReturn(Optional.of(pending));
        when(orderService.confirmVnPayPayment(22L)).thenReturn(paid);
        PaymentController controller = new PaymentController(payment, orders, invoices, loyalty, users, orderService);

        var first = controller.vnpayReturn(callback);
        var repeated = controller.vnpayReturn(callback);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        verify(orderService, times(2)).confirmVnPayPayment(22L);
        verify(loyalty, times(2)).awardForPaidOrder(22L);
        verify(invoices, times(2)).issueForPaidOrder(22L);
    }
}

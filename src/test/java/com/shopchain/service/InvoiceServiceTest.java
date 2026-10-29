package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InvoiceServiceTest {
    @Test
    void receiptStoresPriceBreakdownAndRepeatedIssueCreatesOnlyOneInvoice() {
        OrderRepository orders = mock(OrderRepository.class);
        SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
        Product product = Product.builder().id(3L).name("Serum").sku("S-1").build();
        Order order = Order.builder().id(28L).paymentMethod("VNPAY").paymentStatus("PAID").totalAmount(new BigDecimal("130000"))
                .subtotalAmount(new BigDecimal("100000")).discountAmount(new BigDecimal("20000"))
                .shippingAmount(new BigDecimal("50000"))
                .orderItems(new ArrayList<>()).build();
        order.getOrderItems().add(OrderItem.builder().order(order).product(product).quantity(1).price(new BigDecimal("80000"))
                .listUnitPrice(new BigDecimal("100000")).build());
        AtomicReference<SalesInvoice> created = new AtomicReference<>();
        when(orders.findByIdForUpdate(28L)).thenReturn(Optional.of(order));
        when(invoices.findByOrderId(28L)).thenAnswer(call -> Optional.ofNullable(created.get()));
        when(invoices.save(any(SalesInvoice.class))).thenAnswer(call -> {
            SalesInvoice invoice = call.getArgument(0);
            created.set(invoice);
            return invoice;
        });
        InvoiceService service = new InvoiceService(orders, invoices);

        SalesInvoice first = service.issueForPaidOrder(28L);
        SalesInvoice duplicate = service.issueForPaidOrder(28L);

        assertThat(first.getSubtotal()).isEqualByComparingTo("100000");
        assertThat(first.getDiscountAmount()).isEqualByComparingTo("20000");
        assertThat(first.getShippingAmount()).isEqualByComparingTo("50000");
        assertThat(first.getTotalAmount()).isEqualByComparingTo("130000");
        assertThat(first.getLines().get(0).getListUnitPrice()).isEqualByComparingTo("100000");
        assertThat(first.getLines().get(0).getUnitPrice()).isEqualByComparingTo("80000");
        assertThat(duplicate).isSameAs(first);
        verify(invoices, times(1)).save(any(SalesInvoice.class));
    }
}

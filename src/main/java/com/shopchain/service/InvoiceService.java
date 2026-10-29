package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.SalesInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
public class InvoiceService {
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository invoiceRepository;

    public InvoiceService(OrderRepository orderRepository, SalesInvoiceRepository invoiceRepository) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public SalesInvoice issueForPaidOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        var existing = invoiceRepository.findByOrderId(orderId);
        if (existing.isPresent()) return existing.get();
        if (!"PAID".equals(order.getPaymentStatus())) throw new IllegalStateException("Chỉ phát hành biên nhận khi đơn đã thanh toán.");
        var lines = new ArrayList<SalesInvoiceLine>();
        for (OrderItem item : order.getOrderItems()) {
            java.math.BigDecimal lineTotal = item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity()));
            lines.add(SalesInvoiceLine.builder().productId(item.getProduct().getId())
                    .sku(item.getProduct().getSku()).productName(item.getProduct().getName())
                    .quantity(item.getQuantity()).unitPrice(item.getPrice()).lineTotal(lineTotal)
                    .listUnitPrice(item.getListUnitPrice() == null ? item.getPrice() : item.getListUnitPrice()).build());
        }
        var now = LocalDateTime.now();
        java.math.BigDecimal merchandiseSubtotal = order.getSubtotalAmount() == null ? order.getTotalAmount()
                : order.getSubtotalAmount();
        var invoice = SalesInvoice.builder().invoiceNumber("WEB-" + order.getId()).order(order)
                .customerName(order.getCustomerName()).customerPhone(order.getCustomerPhone())
                .subtotal(merchandiseSubtotal).discountAmount(order.getDiscountAmount() == null ? java.math.BigDecimal.ZERO : order.getDiscountAmount())
                .shippingAmount(order.getShippingAmount() == null ? java.math.BigDecimal.ZERO : order.getShippingAmount())
                .taxAmount(java.math.BigDecimal.ZERO).totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod()).status("ISSUED").issuedAt(now).lines(lines).build();
        lines.forEach(line -> line.setInvoice(invoice));
        return invoiceRepository.save(invoice);
    }
}

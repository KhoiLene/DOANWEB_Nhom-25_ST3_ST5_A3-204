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
        var existing = invoiceRepository.findByOrderId(orderId);
        if (existing.isPresent()) return existing.get();
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!"PAID".equals(order.getPaymentStatus())) throw new IllegalStateException("Chỉ phát hành biên nhận khi đơn đã thanh toán.");
        var lines = new ArrayList<SalesInvoiceLine>();
        for (OrderItem item : order.getOrderItems()) {
            double lineTotal = item.getPrice() * item.getQuantity();
            lines.add(SalesInvoiceLine.builder().productId(item.getProduct().getId())
                    .sku(item.getProduct().getSku()).productName(item.getProduct().getName())
                    .quantity(item.getQuantity()).unitPrice(item.getPrice()).lineTotal(lineTotal).build());
        }
        var now = LocalDateTime.now();
        var invoice = SalesInvoice.builder().invoiceNumber("WEB-" + order.getId()).order(order)
                .customerName(order.getCustomerName()).customerPhone(order.getCustomerPhone())
                .subtotal(order.getTotalAmount()).taxAmount(0d).totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod()).status("ISSUED").issuedAt(now).lines(lines).build();
        lines.forEach(line -> line.setInvoice(invoice));
        return invoiceRepository.save(invoice);
    }
}

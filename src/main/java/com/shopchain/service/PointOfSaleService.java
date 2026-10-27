package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PointOfSaleService {
    private final UserRepository userRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final InventoryMovementRepository movementRepository;

    public PointOfSaleService(UserRepository userRepository, ProductInventoryRepository inventoryRepository,
                              OrderRepository orderRepository, SalesInvoiceRepository invoiceRepository,
                              InventoryMovementRepository movementRepository) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public SalesInvoice sell(String username, String customerName, String customerPhone,
                             String paymentMethod, List<SaleLine> requestedLines) {
        User cashier = userRepository.findByUsername(username).orElseThrow();
        if (cashier.getStore() == null) throw new IllegalStateException("Tài khoản chưa được gán chi nhánh.");
        if (requestedLines == null || requestedLines.isEmpty() || requestedLines.size() > 100)
            throw new IllegalArgumentException("Giỏ hàng cần có từ 1 đến 100 dòng sản phẩm.");
        if (customerName != null && customerName.length() > 100 || customerPhone != null && customerPhone.length() > 30)
            throw new IllegalArgumentException("Thông tin khách hàng quá dài.");
        if (paymentMethod == null || !List.of("CASH", "CARD", "BANK_TRANSFER", "OTHER").contains(paymentMethod)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        }

        Order order = Order.builder().user(cashier).store(cashier.getStore()).orderDate(LocalDateTime.now())
                .status("COMPLETED").paymentMethod(paymentMethod).paymentStatus("PAID").totalAmount(0d).build();
        List<OrderItem> items = new ArrayList<>();
        List<SalesInvoiceLine> invoiceLines = new ArrayList<>();
        List<InventoryMovement> movements = new ArrayList<>();
        String phoneForLookup = blankToNull(customerPhone);
        java.util.Map<Long, User> customerMatches = new java.util.LinkedHashMap<>();
        if (phoneForLookup != null) {
            userRepository.findByPhone(phoneForLookup).forEach(user -> customerMatches.put(user.getId(), user));
            String normalizedPhone = phoneForLookup.replaceAll("[\\s().-]", "");
            if (!normalizedPhone.equals(phoneForLookup))
                userRepository.findByPhone(normalizedPhone).forEach(user -> customerMatches.put(user.getId(), user));
        }
        List<User> matchingCustomers = customerMatches.values().stream()
                .filter(User::isEnabled)
                .filter(user -> user.getRole() != null && "ROLE_USER".equals(user.getRole().getName()))
                .toList();
        int customerPoints = matchingCustomers.size() == 1 && matchingCustomers.get(0).getLoyaltyPoints() != null
                ? matchingCustomers.get(0).getLoyaltyPoints() : 0;
        double subtotal = 0;
        for (SaleLine requested : requestedLines) {
            if (requested.productId() == null || requested.quantity() == null || requested.quantity() <= 0)
                throw new IllegalArgumentException("Sản phẩm và số lượng phải hợp lệ.");
            ProductInventory stock = inventoryRepository.findForUpdateByProductIdAndStoreId(
                    requested.productId(), cashier.getStore().getId()).orElseThrow(
                    () -> new IllegalArgumentException("Sản phẩm không có trong tồn kho chi nhánh."));
            if (stock.availableQuantity() < requested.quantity())
                throw new IllegalArgumentException("Tồn kho không đủ cho sản phẩm: " + stock.getProduct().getName());
            stock.setStockQuantity(stock.getStockQuantity() - requested.quantity());
            movements.add(InventoryMovement.builder().product(stock.getProduct()).store(cashier.getStore())
                    .performedBy(cashier).movementType("POS_SALE").quantity(requested.quantity()).build());
            double unitPrice = stock.getProduct().effectivePrice(stock.getPrice(), customerPoints);
            double lineTotal = unitPrice * requested.quantity();
            subtotal += lineTotal;
            items.add(OrderItem.builder().order(order).product(stock.getProduct())
                    .quantity(requested.quantity()).price(unitPrice).build());
            invoiceLines.add(SalesInvoiceLine.builder().productId(stock.getProduct().getId())
                    .sku(stock.getProduct().getSku()).productName(stock.getProduct().getName())
                    .quantity(requested.quantity()).unitPrice(unitPrice).lineTotal(lineTotal).build());
        }

        order.setTotalAmount(subtotal);
        order.setOrderItems(items);
        Order savedOrder = orderRepository.save(order);
        movements.forEach(movement -> movement.setNote("Bán tại quầy #" + savedOrder.getId()));
        movementRepository.saveAll(movements);
        LocalDateTime now = LocalDateTime.now();
        SalesInvoice invoice = SalesInvoice.builder().order(savedOrder).issuedBy(cashier)
                .customerName(blankToNull(customerName)).customerPhone(blankToNull(customerPhone))
                .subtotal(subtotal).taxAmount(0d).totalAmount(subtotal).paymentMethod(paymentMethod)
                .status("ISSUED").issuedAt(now).invoiceNumber("POS-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + savedOrder.getId())
                .build();
        invoiceLines.forEach(line -> line.setInvoice(invoice));
        invoice.setLines(invoiceLines);
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public SalesInvoice getInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow();
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record SaleLine(Long productId, Integer quantity) {}
}

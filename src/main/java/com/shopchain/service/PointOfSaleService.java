package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dịch vụ xử lý nghiệp vụ Bán hàng tại quầy (POS) và Giao hàng giữ tại chi nhánh (Store Pickup).
 * <p>
 * Tính năng chính:
 * - Bán hàng trực tiếp tại quầy cho khách lẻ hoặc thành viên hệ thống.
 * - Hỗ trợ đổi điểm thưởng thành viên (1 điểm = 100 đ) để giảm trừ trực tiếp trên hóa đơn.
 * - Khách lẻ không áp dụng đổi điểm.
 * - Giao hàng cho các phiếu giữ hàng trực tuyến (Click & Collect) tại chi nhánh.
 * - Tự động cập nhật tồn kho chi nhánh, ghi nhận lịch sử di biến động tồn kho và xuất biên nhận.
 */
@Service
public class PointOfSaleService {
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final InventoryMovementRepository movementRepository;
    private final LoyaltyService loyaltyService;
    private final OrderPricingService pricingService;

    public PointOfSaleService(UserRepository userRepository, ProductRepository productRepository,
                              StoreRepository storeRepository, ProductInventoryRepository inventoryRepository,
                              OrderRepository orderRepository, SalesInvoiceRepository invoiceRepository,
                              InventoryMovementRepository movementRepository, LoyaltyService loyaltyService,
                              OrderPricingService pricingService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.movementRepository = movementRepository;
        this.loyaltyService = loyaltyService;
        this.pricingService = pricingService;
    }

    /**
     * Bán hàng tại quầy (POS) mặc định không đổi điểm thưởng.
     */
    @Transactional
    public SalesInvoice sell(String username, Long customerUserId, String customerName, String customerPhone,
                             String paymentMethod, String paymentReference, boolean bankTransferConfirmed,
                             List<SaleLine> requestedLines) {
        return sell(username, customerUserId, customerName, customerPhone, paymentMethod, paymentReference,
                bankTransferConfirmed, requestedLines, 0);
    }

    /**
     * Bán hàng tại quầy (POS) có tùy chọn đổi điểm thưởng thành viên (1 điểm = 100 đ).
     * Khách lẻ (không đăng nhập / không tra cứu được thành viên) không được phép đổi điểm.
     */
    @Transactional
    public SalesInvoice sell(String username, Long customerUserId, String customerName, String customerPhone,
                             String paymentMethod, String paymentReference, boolean bankTransferConfirmed,
                             List<SaleLine> requestedLines, Integer redeemPoints) {
        validateSaleRequest(customerName, customerPhone, paymentMethod, paymentReference,
                bankTransferConfirmed, requestedLines);
        if (redeemPoints != null && redeemPoints < 0) throw new IllegalArgumentException("Số điểm đổi không hợp lệ.");
        int pointsToRedeem = redeemPoints == null ? 0 : redeemPoints;
        User cashier = cashier(username);
        User customer = resolveCustomer(customerUserId, customerPhone);
        if (customer == null && pointsToRedeem > 0)
            throw new IllegalArgumentException("Khách lẻ không thể đổi điểm.");
        Store store = activeStore(cashier);
        List<SaleItem> items = new ArrayList<>();
        Map<Long, Integer> quantities = new TreeMap<>();
        for (SaleLine requested : requestedLines) quantities.merge(requested.productId(), requested.quantity(), Integer::sum);
        for (Map.Entry<Long, Integer> requested : quantities.entrySet()) {
            Product product = productRepository.findForUpdateById(requested.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
            ensureActiveProduct(product);
            ProductInventory inventory = inventoryRepository.findForUpdateByProductIdAndStoreId(product.getId(), store.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không có trong tồn kho chi nhánh."));
            if (inventory.availableQuantity() < requested.getValue())
                throw new IllegalArgumentException("Tồn kho không đủ cho sản phẩm: " + product.getName());
            items.add(new SaleItem(product, inventory, requested.getValue(),
                    pricingService.listUnitPrice(product), pricingService.saleUnitPrice(product, loyaltyPoints(customer))));
        }
        return completeSale(cashier, store, customer, customerName, customerPhone, paymentMethod,
                paymentReference, items, "POS_SALE", "POS", "STORE_SHIP", pointsToRedeem);
    }

    /**
     * Hoàn tất giao hàng cho phiếu giữ hàng tại quầy chi nhánh (Store Pickup).
     * Tiêu thụ số lượng đã giữ trước đó (reservedQuantity) và trừ tồn kho thực tế mà không cần kiểm tra lại tồn khả dụng.
     */
    @Transactional
    public SalesInvoice sellReserved(String username, StoreReservation reservation,
                                     String paymentMethod, String paymentReference,
                                     boolean bankTransferConfirmed) {
        validatePayment(paymentMethod, paymentReference, bankTransferConfirmed);
        User cashier = cashier(username);
        if (reservation == null || reservation.getStore() == null)
            throw new IllegalArgumentException("Không tìm thấy chi nhánh của phiếu giữ.");
        User customer = userRepository.findByIdForUpdate(reservation.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng của phiếu giữ."));
        ensureCustomer(customer);
        Store store = activeStore(cashier);
        if (!store.getId().equals(reservation.getStore().getId()) || !store.isPickupEnabled())
            throw new IllegalArgumentException("Phiếu giữ hàng không thuộc chi nhánh đang hoạt động của nhân viên.");

        List<SaleItem> items = new ArrayList<>();
        List<StoreReservationItem> sorted = reservation.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId())).toList();
        for (StoreReservationItem reservedItem : sorted) {
            Product product = productRepository.findForUpdateById(reservedItem.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm trong phiếu giữ không còn tồn tại."));
            ensureActiveProduct(product);
            ProductInventory inventory = inventoryRepository.findForUpdateByProductIdAndStoreId(product.getId(), store.getId())
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy tồn kho của sản phẩm đã giữ."));
            int reservedQuantity = zero(inventory.getReservedQuantity());
            if (inventory.getStockQuantity() == null || inventory.getStockQuantity() < reservedItem.getQuantity()
                    || reservedQuantity < reservedItem.getQuantity()) {
                throw new IllegalStateException("Tồn thực tế không khớp phiếu giữ. Chưa tạo giao dịch; cần kiểm tra tại quầy.");
            }
            BigDecimal listPrice = reservedItem.getListUnitPrice() == null
                    ? pricingService.listUnitPrice(product) : reservedItem.getListUnitPrice();
            BigDecimal salePrice = reservedItem.getUnitPrice() == null
                    ? pricingService.saleUnitPrice(product, loyaltyPoints(customer)) : reservedItem.getUnitPrice();
            items.add(new SaleItem(product, inventory, reservedItem.getQuantity(), listPrice, salePrice));
        }
        return completeSale(cashier, store, customer, customer.getFullName(), customer.getPhone(),
                paymentMethod, paymentReference, items, "PICKUP_SALE", "PICKUP", "PICKUP", 0);
    }

    /**
     * Xử lý tạo đơn hàng, trừ tồn kho, tính toán giảm trừ điểm tích lũy và phát hành hóa đơn bán lẻ.
     */
    private SalesInvoice completeSale(User cashier, Store store, User customer, String customerName,
                                      String customerPhone, String paymentMethod, String paymentReference,
                                      List<SaleItem> saleItems, String movementType, String invoicePrefix,
                                      String fulfillmentType, int requestedRedeemPoints) {
        Order order = Order.builder().user(customer).store(store).orderDate(LocalDateTime.now())
                .status("COMPLETED").paymentMethod(paymentMethod).paymentStatus("PAID")
                .fulfillmentType(fulfillmentType)
                .customerName(defaultCustomerName(customerName, customer)).customerPhone(blankToNull(customerPhone))
                .totalAmount(BigDecimal.ZERO).build();
        List<OrderItem> items = new ArrayList<>();
        List<SalesInvoiceLine> invoiceLines = new ArrayList<>();
        List<InventoryMovement> movements = new ArrayList<>();
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;

        for (SaleItem saleItem : saleItems) {
            ProductInventory inventory = saleItem.inventory();
            if ("PICKUP_SALE".equals(movementType)) {
                inventory.setReservedQuantity(zero(inventory.getReservedQuantity()) - saleItem.quantity());
            }
            inventory.setStockQuantity(inventory.getStockQuantity() - saleItem.quantity());
            movements.add(InventoryMovement.builder().product(saleItem.product()).store(store)
                    .performedBy(cashier).movementType(movementType).quantity(saleItem.quantity()).build());
            BigDecimal lineTotal = saleItem.salePrice().multiply(BigDecimal.valueOf(saleItem.quantity()));
            payable = payable.add(lineTotal);
            discount = discount.add(saleItem.listPrice().subtract(saleItem.salePrice()).max(BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(saleItem.quantity())));
            items.add(OrderItem.builder().order(order).product(saleItem.product()).quantity(saleItem.quantity())
                    .price(saleItem.salePrice()).listUnitPrice(saleItem.listPrice()).build());
            invoiceLines.add(SalesInvoiceLine.builder().productId(saleItem.product().getId())
                    .sku(saleItem.product().getSku()).productName(saleItem.product().getName())
                    .quantity(saleItem.quantity()).unitPrice(saleItem.salePrice()).lineTotal(lineTotal)
                    .listUnitPrice(saleItem.listPrice()).build());
        }

        int availablePoints = customer == null ? 0 : loyaltyPoints(customer);
        int maxRedeemablePoints = payable.divideToIntegralValue(OrderPricingService.VND_PER_LOYALTY_POINT).intValue();
        if (requestedRedeemPoints > availablePoints)
            throw new IllegalArgumentException("Thành viên không có đủ điểm để đổi.");
        if (requestedRedeemPoints > maxRedeemablePoints)
            throw new IllegalArgumentException("Số điểm đổi vượt quá số tiền hàng có thể giảm.");
        BigDecimal pointsDiscount = OrderPricingService.VND_PER_LOYALTY_POINT
                .multiply(BigDecimal.valueOf(requestedRedeemPoints));
        payable = payable.subtract(pointsDiscount);
        discount = discount.add(pointsDiscount);
        if (customer != null && requestedRedeemPoints > 0) {
            customer.setLoyaltyPoints(availablePoints - requestedRedeemPoints);
            userRepository.save(customer);
        }

        BigDecimal listSubtotal = payable.add(discount);
        order.setSubtotalAmount(listSubtotal);
        order.setDiscountAmount(discount);
        order.setLoyaltyPointsRedeemed(requestedRedeemPoints);
        order.setLoyaltyPointsRestored(false);
        order.setShippingAmount(BigDecimal.ZERO);
        order.setTotalAmount(payable);
        order.setOrderItems(items);
        Order savedOrder = orderRepository.save(order);
        movements.forEach(movement -> movement.setNote("Bán tại quầy #" + savedOrder.getId()));
        movementRepository.saveAll(movements);

        LocalDateTime now = LocalDateTime.now();
        SalesInvoice invoice = SalesInvoice.builder().order(savedOrder).issuedBy(cashier)
                .customerName(order.getCustomerName()).customerEmail(customer == null ? null : customer.getEmail())
                .customerPhone(order.getCustomerPhone())
                .paymentReference("BANK_TRANSFER".equals(paymentMethod) ? paymentReference : null)
                .subtotal(listSubtotal).discountAmount(discount).shippingAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO).totalAmount(payable).paymentMethod(paymentMethod)
                .status("ISSUED").issuedAt(now)
                .invoiceNumber(invoicePrefix + "-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                        + "-" + savedOrder.getId()).build();
        invoiceLines.forEach(line -> line.setInvoice(invoice));
        invoice.setLines(invoiceLines);
        SalesInvoice savedInvoice = invoiceRepository.save(invoice);
        if (customer != null) loyaltyService.awardForPaidOrder(savedOrder.getId());
        return savedInvoice;
    }

    @Transactional(readOnly = true)
    public SalesInvoice getInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public SalesInvoice getInvoiceForOrder(Long orderId) {
        return invoiceRepository.findByOrderId(orderId).orElseThrow(
                () -> new IllegalStateException("Phiếu đã nhận nhưng chưa có biên nhận; cần đối soát trước khi xử lý tiếp."));
    }

    private User cashier(String username) {
        return userRepository.findByUsername(username).orElseThrow(
                () -> new IllegalArgumentException("Không tìm thấy tài khoản nhân viên."));
    }

    private Store activeStore(User cashier) {
        if (cashier.getStore() == null) throw new IllegalStateException("Tài khoản chưa được gán chi nhánh.");
        Store store = storeRepository.findForUpdateById(cashier.getStore().getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy chi nhánh của nhân viên."));
        if (!store.isActive()) throw new IllegalStateException("Chi nhánh hiện không hoạt động, chưa thể chốt giao dịch.");
        return store;
    }

    private User resolveCustomer(Long customerUserId, String phone) {
        if (customerUserId == null) return null;
        User customer = userRepository.findByIdForUpdate(customerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản thành viên đã xác nhận."));
        ensureCustomer(customer);
        if (phone == null || phone.isBlank())
            throw new IllegalArgumentException("Cần số điện thoại đã tra cứu để xác nhận thành viên.");
        String normalizedEntered = normalizePhone(phone);
        String normalizedStored = normalizePhone(customer.getPhone());
        if (!normalizedEntered.equals(normalizedStored))
            throw new IllegalArgumentException("Số điện thoại không khớp thành viên đã tra cứu. Hãy tra cứu lại hoặc bán như khách lẻ.");
        Map<Long, User> matchesById = new LinkedHashMap<>();
        userRepository.findByPhone(phone.trim()).forEach(match -> matchesById.put(match.getId(), match));
        userRepository.findByPhone(normalizedEntered).forEach(match -> matchesById.put(match.getId(), match));
        List<User> matches = matchesById.values().stream().filter(User::isEnabled)
                .filter(match -> match.getRole() != null && "ROLE_USER".equals(match.getRole().getName())).toList();
        if (matches.size() != 1 || !matches.get(0).getId().equals(customerUserId))
            throw new IllegalArgumentException("Số điện thoại trùng nhiều tài khoản hoặc chưa xác định duy nhất. Hãy bán như khách lẻ.");
        return customer;
    }

    private void ensureCustomer(User customer) {
        if (!customer.isEnabled() || customer.getRole() == null || !"ROLE_USER".equals(customer.getRole().getName()))
            throw new IllegalArgumentException("Tài khoản được chọn không phải thành viên khách hàng đang hoạt động.");
    }

    private void ensureActiveProduct(Product product) {
        if (!Boolean.TRUE.equals(product.getIsActive()))
            throw new IllegalArgumentException("Sản phẩm " + product.getName() + " hiện không còn được phép bán.");
    }

    private int loyaltyPoints(User customer) {
        return customer == null || customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints();
    }

    private void validateSaleRequest(String customerName, String customerPhone, String paymentMethod,
                                     String paymentReference, boolean bankTransferConfirmed,
                                     List<SaleLine> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty() || requestedLines.size() > 100
                || requestedLines.stream().anyMatch(line -> line == null || line.productId() == null
                || line.quantity() == null || line.quantity() <= 0))
            throw new IllegalArgumentException("Giỏ hàng cần có từ 1 đến 100 dòng sản phẩm hợp lệ.");
        if (customerName != null && customerName.length() > 100 || customerPhone != null && customerPhone.length() > 30)
            throw new IllegalArgumentException("Thông tin khách hàng quá dài.");
        validatePayment(paymentMethod, paymentReference, bankTransferConfirmed);
    }

    private void validatePayment(String paymentMethod, String paymentReference, boolean bankTransferConfirmed) {
        if (paymentMethod == null || !List.of("CASH", "CARD", "BANK_TRANSFER", "OTHER").contains(paymentMethod))
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        if ("BANK_TRANSFER".equals(paymentMethod)
                && (paymentReference == null || !paymentReference.matches("[A-Z0-9-]{4,64}")))
            throw new IllegalArgumentException("Mã nội dung chuyển khoản chưa hợp lệ.");
        if ("BANK_TRANSFER".equals(paymentMethod) && !bankTransferConfirmed)
            throw new IllegalArgumentException("Nhân viên cần xác nhận đã tự kiểm tra tiền vào tài khoản trước khi hoàn tất.");
    }

    private String defaultCustomerName(String name, User customer) {
        if (name != null && !name.isBlank()) return name.trim();
        return customer == null ? "Khách lẻ" : customer.getFullName();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        return phone.trim().replaceAll("[\\s().-]", "");
    }

    private int zero(Integer value) { return value == null ? 0 : value; }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record SaleLine(Long productId, Integer quantity) { }
    private record SaleItem(Product product, ProductInventory inventory, Integer quantity,
                            BigDecimal listPrice, BigDecimal salePrice) { }
}

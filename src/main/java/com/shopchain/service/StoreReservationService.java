package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Dịch vụ quản lý nghiệp vụ Giữ hàng tại chi nhánh (Store Reservation / Click & Collect).
 * <p>
 * Chức năng:
 * - Tra cứu tồn kho khả dụng tại các chi nhánh đang hoạt động.
 * - Khách hàng đặt giữ hàng trực tuyến với thời gian giữ cấu hình được (mặc định 30 phút).
 * - Tăng lượng giữ (reservedQuantity) tại chi nhánh, ghi nhận lịch sử di biến động tồn kho.
 * - Tự động quét và giải phóng các phiếu giữ quá hạn (expireReservations) qua Spring Scheduler.
 * - Hỗ trợ nhân viên chi nhánh xác nhận giao hàng (pickup) và thanh toán bằng Tiền mặt hoặc Chuyển khoản (VietQR).
 */
@Service
public class StoreReservationService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final StoreReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final InventoryMovementRepository movementRepository;
    private final PointOfSaleService pointOfSaleService;
    private final OrderPricingService pricingService;
    private final int holdMinutes;

    public StoreReservationService(StoreRepository storeRepository, UserRepository userRepository,
                                   ProductRepository productRepository, ProductInventoryRepository inventoryRepository,
                                   StoreReservationRepository reservationRepository,
                                   OrderRepository orderRepository,
                                   InventoryMovementRepository movementRepository,
                                   PointOfSaleService pointOfSaleService,
                                   OrderPricingService pricingService,
                                   @Value("${reservation.hold-minutes:30}") int holdMinutes) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.orderRepository = orderRepository;
        this.movementRepository = movementRepository;
        this.pointOfSaleService = pointOfSaleService;
        this.pricingService = pricingService;
        this.holdMinutes = Math.max(10, Math.min(holdMinutes, 120));
    }

    /**
     * Tra cứu số lượng khả dụng của sản phẩm tại tất cả các chi nhánh có hỗ trợ nhận tại quầy.
     */
    @Transactional(readOnly = true)
    public List<BranchAvailability> availability(Long productId) {
        Product product = activeProduct(productId);
        Map<Long, ProductInventory> inventoryByStore = new HashMap<>();
        inventoryRepository.findByProductId(product.getId()).stream()
                .filter(inventory -> inventory.getStore() != null)
                .forEach(inventory -> inventoryByStore.put(inventory.getStore().getId(), inventory));
        return storeRepository.findByActiveTrueAndPickupEnabledTrue().stream()
                .map(store -> new BranchAvailability(store.getId(), store.getName(), store.getAddress(),
                        store.getDistrict(), store.getCity(), inventoryByStore.containsKey(store.getId())
                        ? inventoryByStore.get(store.getId()).availableQuantity() : 0))
                .sorted(Comparator.comparing(BranchAvailability::city, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(BranchAvailability::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Đặt phiếu giữ hàng tại chi nhánh cho người dùng.
     * Cập nhật tăng số lượng đang giữ (reservedQuantity) tại chi nhánh để giữ chỗ cho khách.
     */
    @Transactional
    public ReservationView reserve(String username, CreateReservationRequest request) {
        expireReservations();
        if (request == null || request.storeId() == null || request.lines() == null || request.lines().isEmpty() || request.lines().size() > 10)
            throw new IllegalArgumentException("Chọn chi nhánh và ít nhất một sản phẩm để giữ hàng.");
        User userIdentity = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        User user = userRepository.findByIdForUpdate(userIdentity.getId()).orElseThrow(
                () -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        Store store = storeRepository.findForUpdateById(request.storeId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
        if (!store.isActive() || !store.isPickupEnabled()) throw new IllegalArgumentException("Chi nhánh hiện chưa nhận giữ hàng.");

        Map<Long, Integer> quantities = new TreeMap<>();
        for (ReservationLine line : request.lines()) {
            if (line == null || line.productId() == null || line.quantity() == null || line.quantity() < 1 || line.quantity() > 20)
                throw new IllegalArgumentException("Số lượng mỗi sản phẩm phải từ 1 đến 20.");
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        if (quantities.size() > 10 || quantities.values().stream().anyMatch(quantity -> quantity > 20))
            throw new IllegalArgumentException("Mỗi phiếu giữ tối đa 10 sản phẩm, mỗi loại tối đa 20 món.");

        LocalDateTime now = LocalDateTime.now();
        StoreReservation reservation = StoreReservation.builder()
                .reservationCode("TH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT))
                .user(user).store(store).status("HELD").createdAt(now).expiresAt(now.plusMinutes(holdMinutes)).build();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findForUpdateById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
            requireActive(product);
            ProductInventory inventory = inventoryRepository.findForUpdateByProductIdAndStoreId(product.getId(), store.getId())
                    .orElseThrow(() -> new IllegalArgumentException(product.getName() + " hiện chưa có tại chi nhánh này."));
            if (inventory.availableQuantity() < entry.getValue())
                throw new IllegalArgumentException("Số lượng còn lại của " + product.getName() + " không đủ.");
            inventory.setReservedQuantity(zero(inventory.getReservedQuantity()) + entry.getValue());
            inventoryRepository.save(inventory);
            movementRepository.save(InventoryMovement.builder().product(product).store(store).performedBy(user)
                    .movementType("RESERVE").quantity(entry.getValue())
                    .note("Giữ hàng " + reservation.getReservationCode()).build());
            reservation.getItems().add(StoreReservationItem.builder().reservation(reservation).product(product)
                    .quantity(entry.getValue()).unitPrice(pricingService.saleUnitPrice(product, user.getLoyaltyPoints()))
                    .listUnitPrice(pricingService.listUnitPrice(product)).build());
        }
        return toView(reservationRepository.save(reservation));
    }

    /**
     * Lấy danh sách phiếu giữ hàng của khách hàng đang đăng nhập.
     */
    @Transactional
    public List<ReservationView> customerReservations(String username) {
        expireReservations();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toView).toList();
    }

    /**
     * Lấy lịch sử các đơn nhận tại chi nhánh (PICKUP) của khách hàng.
     */
    @Transactional(readOnly = true)
    public List<PickupOrderView> customerPickupOrders(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        return orderRepository.findByUserIdAndFulfillmentTypeOrderByOrderDateDesc(
                        user.getId(), "PICKUP", PageRequest.of(0, 20))
                .stream()
                .map(order -> new PickupOrderView(order.getId(), order.getStatus(), order.getPaymentStatus(),
                        order.getStore() == null ? "Chi nhánh" : order.getStore().getName(),
                        order.getStore() == null ? "" : order.getStore().getAddress(), order.getOrderDate(),
                        (order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount()).doubleValue(), order.getOrderItems() == null ? List.of() : order.getOrderItems().stream()
                        .map(item -> new PickupOrderItemView(item.getProduct() == null
                                ? "Sản phẩm đã ngừng kinh doanh" : item.getProduct().getName(), item.getQuantity()))
                        .toList()))
                .toList();
    }

    /**
     * Khách hàng tự hủy phiếu giữ hàng của mình; hoàn trả số lượng đang giữ về tồn khả dụng.
     */
    @Transactional
    public ReservationView cancelForCustomer(Long reservationId, String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        StoreReservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu giữ hàng."));
        if (!reservation.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Phiếu giữ hàng không thuộc tài khoản của bạn.");
        return cancel(reservation, "CANCELLED");
    }

    /**
     * Nhân viên lấy danh sách các phiếu giữ hàng đang ở trạng thái HELD tại chi nhánh mình quản lý.
     */
    @Transactional
    public List<ReservationView> staffReservations(Long storeId) {
        expireReservations();
        return reservationRepository.findByStoreIdAndStatusOrderByCreatedAtAsc(storeId, "HELD").stream().map(this::toView).toList();
    }

    /**
     * Nhân viên cửa hàng hủy phiếu giữ hàng khi khách không đến hoặc có yêu cầu hủy.
     */
    @Transactional
    public ReservationView cancelForStaff(Long reservationId, Long storeId) {
        StoreReservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu giữ hàng."));
        ensureStore(reservation, storeId);
        return cancel(reservation, "CANCELLED");
    }

    /**
     * Xác nhận giao hàng cho khách từ phiếu giữ (thanh toán mặc định CASH).
     */
    @Transactional
    public PickupResult pickup(Long reservationId, Long storeId, String username) {
        return pickup(reservationId, storeId, username, "CASH", null, false);
    }

    /**
     * Xác nhận giao hàng cho khách từ phiếu giữ hàng với phương thức thanh toán cụ thể (CASH hoặc BANK_TRANSFER).
     */
    @Transactional
    public PickupResult pickup(Long reservationId, Long storeId, String username,
                               String paymentMethod, String paymentReference, boolean bankTransferConfirmed) {
        StoreReservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu giữ hàng."));
        ensureStore(reservation, storeId);
        if ("PICKED_UP".equals(reservation.getStatus())) {
            if (reservation.getOrder() == null)
                throw new IllegalStateException("Phiếu cũ đã đánh dấu đã nhận nhưng chưa có giao dịch/biên nhận. Cần đối soát; không thể trừ tồn lần nữa.");
            SalesInvoice invoice = pointOfSaleService.getInvoiceForOrder(reservation.getOrder().getId());
            return pickupResult(reservation, invoice);
        }
        if (!"HELD".equals(reservation.getStatus()))
            throw new IllegalArgumentException("Phiếu giữ hàng đã " + statusLabel(reservation.getStatus()) + "; không thể giao lại.");
        if (!reservation.getExpiresAt().isAfter(LocalDateTime.now()))
            return new PickupResult(cancel(reservation, "EXPIRED"), null, null);
        if (!reservation.getStore().isActive() || !reservation.getStore().isPickupEnabled())
            throw new IllegalStateException("Chi nhánh hiện không hoạt động hoặc đã tắt nhận tại quầy.");

        SalesInvoice invoice = pointOfSaleService.sellReserved(username, reservation,
                paymentMethod, paymentReference, bankTransferConfirmed);
        reservation.setOrder(invoice.getOrder());
        reservation.setStatus("PICKED_UP");
        reservation.setPickedUpAt(LocalDateTime.now());
        return pickupResult(reservationRepository.save(reservation), invoice);
    }

    /**
     * Quét định kỳ (mặc định mỗi 60 giây) để tự động hủy các phiếu giữ hàng đã hết hạn giữ.
     */
    @Scheduled(fixedDelayString = "${reservation.expiry-scan-ms:60000}")
    @Transactional
    public void expireReservations() {
        for (StoreReservation candidate : reservationRepository.findExpired(LocalDateTime.now())) {
            StoreReservation locked = reservationRepository.findForUpdateById(candidate.getId()).orElse(null);
            if (locked != null && "HELD".equals(locked.getStatus()) && !locked.getExpiresAt().isAfter(LocalDateTime.now()))
                cancel(locked, "EXPIRED");
        }
    }

    /**
     * Hủy phiếu giữ hàng và hoàn trả số lượng đã giữ về tồn kho chi nhánh.
     */
    private ReservationView cancel(StoreReservation reservation, String status) {
        if (!"HELD".equals(reservation.getStatus())) {
            if (reservation.getStatus().equals(status)) return toView(reservation);
            throw new IllegalArgumentException("Phiếu giữ hàng đã được xử lý.");
        }
        for (StoreReservationItem item : reservation.getItems()) {
            inventoryRepository.findForUpdateByProductIdAndStoreId(item.getProduct().getId(), reservation.getStore().getId())
                    .ifPresentOrElse(inventory -> {
                        int reserved = zero(inventory.getReservedQuantity());
                        if (reserved < item.getQuantity())
                            throw new IllegalStateException("Số lượng đang giữ không khớp phiếu; cần kiểm tra tồn trước khi hủy.");
                        inventory.setReservedQuantity(reserved - item.getQuantity());
                        inventoryRepository.save(inventory);
                        movementRepository.save(InventoryMovement.builder().product(item.getProduct())
                                .store(reservation.getStore()).movementType("RESERVATION_RELEASE")
                                .quantity(item.getQuantity()).note("Hủy giữ hàng " + reservation.getReservationCode()).build());
                    }, () -> { throw new IllegalStateException("Không tìm thấy tồn kho để giải phóng phiếu giữ."); });
        }
        reservation.setStatus(status);
        return toView(reservationRepository.save(reservation));
    }

    private void ensureStore(StoreReservation reservation, Long storeId) {
        if (storeId == null || !reservation.getStore().getId().equals(storeId))
            throw new IllegalArgumentException("Phiếu giữ hàng không thuộc chi nhánh được phân công.");
    }

    private Product activeProduct(Long productId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        requireActive(product);
        return product;
    }

    private void requireActive(Product product) {
        if (!Boolean.TRUE.equals(product.getIsActive())) throw new IllegalArgumentException("Sản phẩm hiện không được bán.");
    }

    private PickupResult pickupResult(StoreReservation reservation, SalesInvoice invoice) {
        return new PickupResult(toView(reservation), invoice.getId(), invoice.getInvoiceNumber());
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "CANCELLED" -> "hủy";
            case "EXPIRED" -> "hết hạn";
            case "PICKED_UP" -> "đã nhận";
            default -> "được xử lý";
        };
    }

    private int zero(Integer value) { return value == null ? 0 : value; }

    private ReservationView toView(StoreReservation reservation) {
        List<ReservationItemView> items = reservation.getItems().stream()
                .map(item -> new ReservationItemView(item.getProduct().getId(), item.getProduct().getName(),
                        item.getProduct().getImageUrl(), item.getQuantity(), item.getUnitPrice(), item.getListUnitPrice()))
                .toList();
        return new ReservationView(reservation.getId(), reservation.getReservationCode(), reservation.getStatus(),
                reservation.getStore().getId(), reservation.getStore().getName(), reservation.getStore().getAddress(),
                reservation.getUser().getFullName(), reservation.getUser().getPhone(), reservation.getCreatedAt(),
                reservation.getExpiresAt(), reservation.getPickedUpAt(), items);
    }

    public record ReservationLine(Long productId, Integer quantity) { }
    public record CreateReservationRequest(Long storeId, List<ReservationLine> lines) { }
    public record BranchAvailability(Long storeId, String name, String address, String district, String city, int available) { }
    public record ReservationItemView(Long productId, String productName, String imageUrl, Integer quantity,
                                      java.math.BigDecimal unitPrice, java.math.BigDecimal listUnitPrice) { }
    public record ReservationView(Long id, String code, String status, Long storeId, String storeName, String storeAddress,
                                  String customerName, String customerPhone, LocalDateTime createdAt, LocalDateTime expiresAt,
                                  LocalDateTime pickedUpAt, List<ReservationItemView> items) {
        /**
         * Tính tổng số tiền cần thanh toán cho phiếu giữ hàng để hiển thị và tạo mã VietQR.
         */
        public java.math.BigDecimal totalAmount() {
            if (items == null || items.isEmpty()) return java.math.BigDecimal.ZERO;
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (ReservationItemView item : items) {
                java.math.BigDecimal price = item.unitPrice() != null ? item.unitPrice() : java.math.BigDecimal.ZERO;
                int qty = item.quantity() != null ? item.quantity() : 0;
                total = total.add(price.multiply(java.math.BigDecimal.valueOf(qty)));
            }
            return total;
        }

        public java.math.BigDecimal getTotalAmount() {
            return totalAmount();
        }
    }
    public record PickupOrderItemView(String productName, Integer quantity) { }
    public record PickupOrderView(Long id, String status, String paymentStatus, String storeName, String storeAddress,
                                  LocalDateTime createdAt, Double totalAmount, List<PickupOrderItemView> items) { }
    public record PickupResult(ReservationView reservation, Long invoiceId, String invoiceNumber) { }
}

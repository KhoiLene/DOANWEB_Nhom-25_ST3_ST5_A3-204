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

@Service
public class StoreReservationService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final StoreReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final int holdMinutes;

    public StoreReservationService(StoreRepository storeRepository, UserRepository userRepository,
                                   ProductRepository productRepository, ProductInventoryRepository inventoryRepository,
                                   StoreReservationRepository reservationRepository,
                                   OrderRepository orderRepository,
                                   @Value("${reservation.hold-minutes:30}") int holdMinutes) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.orderRepository = orderRepository;
        this.holdMinutes = Math.max(10, Math.min(holdMinutes, 120));
    }

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

    @Transactional
    public ReservationView reserve(String username, CreateReservationRequest request) {
        expireReservations();
        if (request == null || request.storeId() == null || request.lines() == null || request.lines().isEmpty() || request.lines().size() > 10)
            throw new IllegalArgumentException("Chọn chi nhánh và ít nhất một sản phẩm để giữ hàng.");
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        Store store = storeRepository.findById(request.storeId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
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
            Product product = activeProduct(entry.getKey());
            ProductInventory inventory = inventoryRepository.findForUpdateByProductIdAndStoreId(product.getId(), store.getId())
                    .orElseThrow(() -> new IllegalArgumentException(product.getName() + " hiện chưa có tại chi nhánh này."));
            if (inventory.availableQuantity() < entry.getValue())
                throw new IllegalArgumentException("Số lượng còn lại của " + product.getName() + " không đủ.");
            inventory.setReservedQuantity(zero(inventory.getReservedQuantity()) + entry.getValue());
            inventoryRepository.save(inventory);
            reservation.getItems().add(StoreReservationItem.builder().reservation(reservation).product(product)
                    .quantity(entry.getValue()).unitPrice(inventory.getPrice()).build());
        }
        return toView(reservationRepository.save(reservation));
    }

    @Transactional
    public List<ReservationView> customerReservations(String username) {
        expireReservations();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toView).toList();
    }

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
                        order.getTotalAmount(), order.getOrderItems() == null ? List.of() : order.getOrderItems().stream()
                        .map(item -> new PickupOrderItemView(item.getProduct() == null
                                ? "Sản phẩm đã ngừng kinh doanh" : item.getProduct().getName(), item.getQuantity()))
                        .toList()))
                .toList();
    }

    @Transactional
    public ReservationView cancelForCustomer(Long reservationId, String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        StoreReservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu giữ hàng."));
        if (!reservation.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Phiếu giữ hàng không thuộc tài khoản của bạn.");
        return cancel(reservation, "CANCELLED");
    }

    @Transactional
    public List<ReservationView> staffReservations(Long storeId) {
        expireReservations();
        return reservationRepository.findByStoreIdAndStatusOrderByCreatedAtAsc(storeId, "HELD").stream().map(this::toView).toList();
    }

    @Transactional
    public ReservationView cancelForStaff(Long reservationId, Long storeId) {
        StoreReservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu giữ hàng."));
        ensureStore(reservation, storeId);
        return cancel(reservation, "CANCELLED");
    }

    @Transactional
    public ReservationView pickup(Long reservationId, Long storeId) {
        expireReservations();
        StoreReservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu giữ hàng."));
        ensureStore(reservation, storeId);
        if (!"HELD".equals(reservation.getStatus())) throw new IllegalArgumentException("Phiếu này không còn hiệu lực.");
        if (!reservation.getExpiresAt().isAfter(LocalDateTime.now())) return cancel(reservation, "EXPIRED");
        for (StoreReservationItem item : reservation.getItems()) {
            ProductInventory inventory = inventoryRepository.findForUpdateByProductIdAndStoreId(item.getProduct().getId(), storeId)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lượng hàng đã giữ."));
            int reserved = zero(inventory.getReservedQuantity());
            if (inventory.getStockQuantity() == null || inventory.getStockQuantity() < item.getQuantity() || reserved < item.getQuantity())
                throw new IllegalStateException("Tồn kho không khớp với phiếu giữ hàng; cần kiểm tra tại quầy.");
            inventory.setStockQuantity(inventory.getStockQuantity() - item.getQuantity());
            inventory.setReservedQuantity(reserved - item.getQuantity());
            inventoryRepository.save(inventory);
        }
        reservation.setStatus("PICKED_UP");
        reservation.setPickedUpAt(LocalDateTime.now());
        return toView(reservationRepository.save(reservation));
    }

    @Scheduled(fixedDelayString = "${reservation.expiry-scan-ms:60000}")
    @Transactional
    public void expireReservations() {
        for (StoreReservation candidate : reservationRepository.findExpired(LocalDateTime.now())) {
            StoreReservation locked = reservationRepository.findForUpdateById(candidate.getId()).orElse(null);
            if (locked != null && "HELD".equals(locked.getStatus()) && !locked.getExpiresAt().isAfter(LocalDateTime.now()))
                cancel(locked, "EXPIRED");
        }
    }

    private ReservationView cancel(StoreReservation reservation, String status) {
        if (!"HELD".equals(reservation.getStatus())) {
            if (reservation.getStatus().equals(status)) return toView(reservation);
            throw new IllegalArgumentException("Phiếu giữ hàng đã được xử lý.");
        }
        for (StoreReservationItem item : reservation.getItems()) {
            inventoryRepository.findForUpdateByProductIdAndStoreId(item.getProduct().getId(), reservation.getStore().getId())
                    .ifPresent(inventory -> {
                        inventory.setReservedQuantity(Math.max(0, zero(inventory.getReservedQuantity()) - item.getQuantity()));
                        inventoryRepository.save(inventory);
                    });
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
        if (Boolean.FALSE.equals(product.getIsActive())) throw new IllegalArgumentException("Sản phẩm hiện không được bán.");
        return product;
    }

    private int zero(Integer value) { return value == null ? 0 : value; }

    private ReservationView toView(StoreReservation reservation) {
        List<ReservationItemView> items = reservation.getItems().stream()
                .map(item -> new ReservationItemView(item.getProduct().getId(), item.getProduct().getName(),
                        item.getProduct().getImageUrl(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new ReservationView(reservation.getId(), reservation.getReservationCode(), reservation.getStatus(),
                reservation.getStore().getId(), reservation.getStore().getName(), reservation.getStore().getAddress(),
                reservation.getUser().getFullName(), reservation.getUser().getPhone(), reservation.getCreatedAt(),
                reservation.getExpiresAt(), reservation.getPickedUpAt(), items);
    }

    public record ReservationLine(Long productId, Integer quantity) { }
    public record CreateReservationRequest(Long storeId, List<ReservationLine> lines) { }
    public record BranchAvailability(Long storeId, String name, String address, String district, String city, int available) { }
    public record ReservationItemView(Long productId, String productName, String imageUrl, Integer quantity, Double unitPrice) { }
    public record ReservationView(Long id, String code, String status, Long storeId, String storeName, String storeAddress,
                                  String customerName, String customerPhone, LocalDateTime createdAt, LocalDateTime expiresAt,
                                  LocalDateTime pickedUpAt, List<ReservationItemView> items) { }
    public record PickupOrderItemView(String productName, Integer quantity) { }
    public record PickupOrderView(Long id, String status, String paymentStatus, String storeName, String storeAddress,
                                  LocalDateTime createdAt, Double totalAmount, List<PickupOrderItemView> items) { }
}

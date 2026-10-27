package com.shopchain.service;

import com.shopchain.entity.Order;
import com.shopchain.entity.ProductInventory;
import com.shopchain.entity.StockTransfer;
import com.shopchain.entity.Store;
import com.shopchain.entity.Warehouse;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.OrderItemRepository;
import com.shopchain.repository.ProductInventoryRepository;
import com.shopchain.repository.StockTransferRepository;
import com.shopchain.repository.StoreRepository;
import com.shopchain.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OperationsControlTowerService {
    private static final int BASE_REORDER_POINT = 3;
    private static final int LEAD_TIME_DAYS = 7;
    private static final int TARGET_COVER_DAYS = 14;
    private static final int SAFETY_STOCK_UNITS = 2;
    private static final int SALES_LOOKBACK_DAYS = 30;
    private static final Set<String> ACTION_ORDER_STATUSES = Set.of("PENDING", "CONFIRMED");
    private static final Set<String> OPEN_ORDER_STATUSES = Set.of("PENDING", "CONFIRMED", "PROCESSING", "SHIPPING");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockTransferRepository transferRepository;

    public OperationsControlTowerService(OrderRepository orderRepository,
                                         OrderItemRepository orderItemRepository,
                                         ProductInventoryRepository inventoryRepository,
                                         StoreRepository storeRepository,
                                         WarehouseRepository warehouseRepository,
                                         StockTransferRepository transferRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.storeRepository = storeRepository;
        this.warehouseRepository = warehouseRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<Store> activeStores = storeRepository.findAll().stream().filter(Store::isActive).toList();
        List<Warehouse> activeWarehouses = warehouseRepository.findByActiveTrue();
        List<Order> orders = orderRepository.findAll();
        List<ProductInventory> inventories = inventoryRepository.findAll();
        List<StockTransfer> transfers = transferRepository.findAll();
        Map<String, Long> salesByProductStore = orderItemRepository
                .findStoreProductSalesSince(LocalDateTime.now().minusDays(SALES_LOOKBACK_DAYS)).stream()
                .collect(Collectors.toMap(sales -> key(sales.getProductId(), sales.getStoreId()),
                        sales -> sales.getUnitsSold() == null ? 0L : sales.getUnitsSold()));

        Map<Long, Store> storesById = activeStores.stream().collect(Collectors.toMap(Store::getId, store -> store));
        Set<Long> activeWarehouseIds = activeWarehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());
        Map<Long, List<ProductInventory>> warehouseStocksByProduct = inventories.stream()
                .filter(row -> row.getWarehouse() != null && row.getWarehouse().getId() != null)
                .filter(row -> activeWarehouseIds.contains(row.getWarehouse().getId()))
                .filter(row -> isActive(row))
                .collect(Collectors.groupingBy(row -> row.getProduct().getId()));

        List<StockAlert> stockAlerts = inventories.stream()
                .filter(row -> row.getStore() != null && storesById.containsKey(row.getStore().getId()))
                .filter(OperationsControlTowerService::isActive)
                .filter(row -> row.availableQuantity() <= reorderPoint(salesByProductStore.getOrDefault(
                        key(row.getProduct().getId(), row.getStore().getId()), 0L)))
                .map(row -> toAlert(row, warehouseStocksByProduct.getOrDefault(row.getProduct().getId(), List.of()),
                        salesByProductStore.getOrDefault(key(row.getProduct().getId(), row.getStore().getId()), 0L)))
                .sorted(Comparator.comparingInt((StockAlert alert) -> alert.availableQuantity() == 0 ? 0 : 1)
                        .thenComparingInt(StockAlert::availableQuantity)
                        .thenComparing(StockAlert::storeName)
                        .thenComparing(StockAlert::productName))
                .limit(12)
                .toList();

        List<Order> actionOrders = orders.stream().filter(order -> ACTION_ORDER_STATUSES.contains(status(order)))
                .sorted(Comparator.comparing(Order::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8).toList();
        long ordersNeedingAction = orders.stream().filter(order -> ACTION_ORDER_STATUSES.contains(status(order))).count();
        long openOrders = orders.stream().filter(order -> OPEN_ORDER_STATUSES.contains(status(order))).count();
        long transfersWaiting = transfers.stream().filter(transfer -> "IN_TRANSIT".equalsIgnoreCase(transfer.getStatus())).count();
        long lowStockCount = inventories.stream()
                .filter(row -> row.getStore() != null && storesById.containsKey(row.getStore().getId()))
                .filter(OperationsControlTowerService::isActive)
                .filter(row -> row.availableQuantity() <= reorderPoint(salesByProductStore.getOrDefault(
                        key(row.getProduct().getId(), row.getStore().getId()), 0L))).count();
        long outOfStockCount = inventories.stream()
                .filter(row -> row.getStore() != null && storesById.containsKey(row.getStore().getId()))
                .filter(OperationsControlTowerService::isActive)
                .filter(row -> row.availableQuantity() == 0).count();

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        double monthRevenue = orders.stream()
                .filter(order -> "DELIVERED".equalsIgnoreCase(status(order)))
                .filter(order -> "PAID".equalsIgnoreCase(order.getPaymentStatus()))
                .filter(order -> order.getOrderDate() != null && !order.getOrderDate().isBefore(monthStart))
                .map(Order::getTotalAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();

        Map<Long, List<Order>> ordersByStore = orders.stream().filter(order -> order.getStore() != null)
                .collect(Collectors.groupingBy(order -> order.getStore().getId()));
        Map<Long, Long> stockIssuesByStore = inventories.stream()
                .filter(row -> row.getStore() != null && storesById.containsKey(row.getStore().getId()))
                .filter(OperationsControlTowerService::isActive)
                .filter(row -> row.availableQuantity() <= reorderPoint(salesByProductStore.getOrDefault(
                        key(row.getProduct().getId(), row.getStore().getId()), 0L)))
                .collect(Collectors.groupingBy(row -> row.getStore().getId(), Collectors.counting()));
        Map<Long, Long> transfersByStore = transfers.stream()
                .filter(transfer -> "IN_TRANSIT".equalsIgnoreCase(transfer.getStatus()))
                .filter(transfer -> transfer.getDestinationStore() != null)
                .collect(Collectors.groupingBy(transfer -> transfer.getDestinationStore().getId(), Collectors.counting()));

        List<BranchPulse> branches = activeStores.stream().map(store -> {
                    List<Order> branchOrders = ordersByStore.getOrDefault(store.getId(), List.of());
                    long awaiting = branchOrders.stream().filter(order -> ACTION_ORDER_STATUSES.contains(status(order))).count();
                    long active = branchOrders.stream().filter(order -> OPEN_ORDER_STATUSES.contains(status(order))).count();
                    return new BranchPulse(store.getId(), store.getName(), store.getCity(), awaiting, active,
                            stockIssuesByStore.getOrDefault(store.getId(), 0L),
                            transfersByStore.getOrDefault(store.getId(), 0L));
                }).sorted(Comparator.comparingLong(BranchPulse::stockAlerts).reversed()
                        .thenComparing(Comparator.comparingLong(BranchPulse::ordersNeedingAction).reversed())
                        .thenComparing(BranchPulse::name))
                .limit(10).toList();

        List<TransferPulse> pendingTransfers = transfers.stream()
                .filter(transfer -> "IN_TRANSIT".equalsIgnoreCase(transfer.getStatus()))
                .sorted(Comparator.comparing(StockTransfer::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(8)
                .map(transfer -> new TransferPulse(transfer.getId(),
                        transfer.getProduct() == null ? "Sản phẩm" : transfer.getProduct().getName(),
                        transfer.getSourceWarehouse() == null ? "Kho" : transfer.getSourceWarehouse().getName(),
                        transfer.getDestinationStore() == null ? "Chi nhánh" : transfer.getDestinationStore().getName(),
                        transfer.getQuantity(), transfer.getCreatedAt()))
                .toList();

        return new Overview(activeStores.size(), activeWarehouses.size(), ordersNeedingAction, openOrders,
                lowStockCount, outOfStockCount, transfersWaiting, monthRevenue, BASE_REORDER_POINT,
                LEAD_TIME_DAYS, TARGET_COVER_DAYS, actionOrders.stream().map(order -> new OrderPulse(order.getId(),
                        order.getCustomerName(), order.getStore() == null ? "Chưa gán điểm bán" : order.getStore().getName(),
                        order.getStatus(), order.getTotalAmount(), order.getOrderDate())).toList(),
                stockAlerts, branches, pendingTransfers, LocalDateTime.now());
    }

    private StockAlert toAlert(ProductInventory row, List<ProductInventory> warehouseRows, long unitsSoldLast30Days) {
        ProductInventory source = warehouseRows.stream().filter(stock -> stock.availableQuantity() > 0)
                .max(Comparator.comparingInt(ProductInventory::availableQuantity)).orElse(null);
        int availableAtStore = row.availableQuantity();
        int reorderPoint = reorderPoint(unitsSoldLast30Days);
        int targetStock = targetStock(unitsSoldLast30Days);
        int proposed = source == null ? 0 : Math.min(Math.max(1, targetStock - availableAtStore), source.availableQuantity());
        double averageDailySales = unitsSoldLast30Days / (double) SALES_LOOKBACK_DAYS;
        Integer daysOfCover = averageDailySales <= 0 ? null : (int) Math.floor(availableAtStore / averageDailySales);
        return new StockAlert(row.getProduct().getId(), row.getProduct().getName(), row.getStore().getId(),
                row.getStore().getName(), availableAtStore, source == null ? null : source.getWarehouse().getId(),
                source == null ? null : source.getWarehouse().getName(), source == null ? 0 : source.availableQuantity(),
                proposed, unitsSoldLast30Days, averageDailySales, daysOfCover, reorderPoint, targetStock);
    }

    private int reorderPoint(long unitsSoldLast30Days) {
        double dailyDemand = Math.max(0, unitsSoldLast30Days) / (double) SALES_LOOKBACK_DAYS;
        return Math.max(BASE_REORDER_POINT,
                BigDecimal.valueOf(dailyDemand * LEAD_TIME_DAYS + SAFETY_STOCK_UNITS)
                        .setScale(0, RoundingMode.CEILING).intValue());
    }

    private int targetStock(long unitsSoldLast30Days) {
        double dailyDemand = Math.max(0, unitsSoldLast30Days) / (double) SALES_LOOKBACK_DAYS;
        return Math.max(reorderPoint(unitsSoldLast30Days) + 1,
                BigDecimal.valueOf(dailyDemand * TARGET_COVER_DAYS + SAFETY_STOCK_UNITS)
                        .setScale(0, RoundingMode.CEILING).intValue());
    }

    private static String key(Long productId, Long storeId) {
        return productId + ":" + storeId;
    }

    private static boolean isActive(ProductInventory row) {
        return row.getProduct() != null && (row.getProduct().getIsActive() == null || row.getProduct().getIsActive());
    }

    private static String status(Order order) {
        return order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
    }

    public record Overview(int activeStores, int activeWarehouses, long ordersNeedingAction, long openOrders,
                           long lowStockCount, long outOfStockCount, long transfersWaiting, double monthRevenue,
                           int baseReorderPoint, int replenishmentLeadTimeDays, int targetCoverDays,
                           List<OrderPulse> actionOrders,
                           List<StockAlert> stockAlerts, List<BranchPulse> branches,
                           List<TransferPulse> pendingTransfers, LocalDateTime refreshedAt) { }
    public record OrderPulse(Long id, String customerName, String storeName, String status,
                             Double totalAmount, LocalDateTime orderDate) { }
    public record StockAlert(Long productId, String productName, Long storeId, String storeName,
                             int availableQuantity, Long sourceWarehouseId, String sourceWarehouseName,
                             int sourceWarehouseAvailable, int proposedQuantity, long unitsSoldLast30Days,
                             double averageDailySales, Integer daysOfCover, int reorderPoint, int targetStock) { }
    public record BranchPulse(Long id, String name, String city, long ordersNeedingAction,
                              long openOrders, long stockAlerts, long transfersWaiting) { }
    public record TransferPulse(Long id, String productName, String sourceWarehouseName,
                                String destinationStoreName, Integer quantity, LocalDateTime createdAt) { }
}

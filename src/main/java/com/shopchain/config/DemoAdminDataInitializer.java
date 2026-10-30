package com.shopchain.config;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.OrderPricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Creates labelled, idempotent records for practicing admin workflows on a local database. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.demo-admin-data.enabled", havingValue = "true")
@Order(30)
public class DemoAdminDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAdminDataInitializer.class);
    private static final String TRAINING_STORE_NAME = "Chi nhánh demo Admin - Thực hành";
    private static final String BRANCH_STAFF_USERNAME = "demo.branch.staff";
    private static final String WAREHOUSE_STAFF_USERNAME = "demo.warehouse.staff";

    private final StoreRepository storeRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final InvoiceService invoiceService;
    private final OrderPricingService pricingService;
    private final PasswordEncoder passwordEncoder;
    private final String demoAccountPassword;

    public DemoAdminDataInitializer(StoreRepository storeRepository,
                                    WarehouseRepository warehouseRepository,
                                    ProductRepository productRepository,
                                    ProductInventoryRepository inventoryRepository,
                                    InventoryMovementRepository movementRepository,
                                    RoleRepository roleRepository,
                                    UserRepository userRepository,
                                    OrderRepository orderRepository,
                                    InvoiceService invoiceService,
                                    OrderPricingService pricingService,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${app.demo-admin-data.account-password:}") String demoAccountPassword) {
        this.storeRepository = storeRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.invoiceService = invoiceService;
        this.pricingService = pricingService;
        this.passwordEncoder = passwordEncoder;
        this.demoAccountPassword = demoAccountPassword == null ? "" : demoAccountPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Store trainingStore = ensureTrainingStore();
        Warehouse warehouse = warehouseRepository.findByNameIn(List.of("Kho demo Techtra - Thủ Đức"))
                .stream().findFirst().orElse(null);
        List<Product> products = productRepository.findBySkuStartingWith("TECH-").stream()
                .filter(product -> !Boolean.FALSE.equals(product.getIsActive()))
                .sorted(Comparator.comparing(Product::getSku, Comparator.nullsLast(String::compareTo)))
                .limit(4).toList();

        if (trainingStore == null || warehouse == null || products.size() < 4) {
            log.warn("Admin demo fixtures were skipped because demo store, warehouse, or products are missing.");
            return;
        }

        seedTrainingInventory(trainingStore, products);
        Role storeStaffRole = ensureRole("ROLE_STORE_STAFF");
        Role warehouseStaffRole = ensureRole("ROLE_WAREHOUSE");
        ensureEmployee(BRANCH_STAFF_USERNAME, "demo.branch.staff@example.test",
                "Demo thực hành - Nhân viên chi nhánh", storeStaffRole, trainingStore, null);
        ensureEmployee(WAREHOUSE_STAFF_USERNAME, "demo.warehouse.staff@example.test",
                "Demo thực hành - Nhân viên kho", warehouseStaffRole, null, warehouse);

        seedOrder("0900999101", "Demo Admin - Chờ xác nhận chuyển khoản", products.get(0), trainingStore,
                null, "PENDING", "BANK_TRANSFER", "UNPAID", "PICKUP", LocalDateTime.now().minusMinutes(5));
        seedOrder("0900999102", "Demo Admin - Đã xác nhận thanh toán", products.get(1), trainingStore,
                null, "CONFIRMED", "BANK_TRANSFER", "PAID", "PICKUP", LocalDateTime.now().minusMinutes(30));
        seedOrder("0900999103", "Demo Admin - Đang chuẩn bị giao", products.get(2), trainingStore,
                warehouse, "PROCESSING", "BANK_TRANSFER", "PAID", "WAREHOUSE_SHIP", LocalDateTime.now().minusHours(2));
        seedOrder("0900999104", "Demo Admin - Đã giao COD", products.get(3), trainingStore,
                null, "DELIVERED", "COD", "PAID", "STORE_SHIP", LocalDateTime.now().minusDays(1));

        log.info("Local admin practice data is ready (training branch, sample orders, inventory and staff records).");
    }

    private Store ensureTrainingStore() {
        return storeRepository.findByNameIn(List.of(TRAINING_STORE_NAME)).stream().findFirst()
                .orElseGet(() -> storeRepository.save(Store.builder().name(TRAINING_STORE_NAME)
                        .address("Địa chỉ mô phỏng để luyện thao tác, không phải điểm bán thật")
                        .city("Thành phố Hồ Chí Minh").district("Thành phố Thủ Đức")
                        .latitude(10.8505).longitude(106.7717).active(true).pickupEnabled(true).build()));
    }

    private void seedTrainingInventory(Store store, List<Product> products) {
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            if (inventoryRepository.findByProductIdAndStoreId(product.getId(), store.getId()).isPresent()) continue;
            int openingQuantity = i == 0 ? 1 : 5;
            inventoryRepository.save(ProductInventory.builder().product(product).store(store)
                    .price(product.getPrice()).stockQuantity(openingQuantity).reservedQuantity(0).build());
        }
    }

    private Role ensureRole(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).build()));
    }

    private User ensureEmployee(String username, String email, String fullName, Role role,
                                Store store, Warehouse warehouse) {
        boolean canLogin = demoPasswordIsValid();
        User existing = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (existing != null) {
            boolean isExpectedFixture = email.equalsIgnoreCase(existing.getEmail())
                    && fullName.equals(existing.getFullName());
            if (isExpectedFixture) {
                existing.setEnabled(canLogin);
                String configuredOrRandomPassword = canLogin
                        ? demoAccountPassword : UUID.randomUUID() + UUID.randomUUID().toString();
                existing.setPassword(passwordEncoder.encode(configuredOrRandomPassword));
                return userRepository.save(existing);
            }
            return null;
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) return null;

        String initialPassword = canLogin ? demoAccountPassword : UUID.randomUUID() + UUID.randomUUID().toString();
        User employee = User.builder().username(username).email(email).fullName(fullName)
                .phone("0900999100").password(passwordEncoder.encode(initialPassword))
                .enabled(canLogin).role(role).store(store).warehouse(warehouse)
                .loyaltyPoints(0).authTokenVersion(0).build();
        return userRepository.save(employee);
    }

    private boolean demoPasswordIsValid() {
        int bytes = demoAccountPassword.getBytes(StandardCharsets.UTF_8).length;
        return demoAccountPassword.length() >= 12 && bytes <= 72;
    }

    private void seedOrder(String phone, String customerName, Product product, Store store, Warehouse warehouse,
                           String status, String paymentMethod, String paymentStatus, String fulfillmentType,
                           LocalDateTime orderDate) {
        if (orderRepository.existsByCustomerPhone(phone)) return;

        ProductInventory inventory = warehouse == null
                ? inventoryRepository.findForUpdateByProductIdAndStoreId(product.getId(), store.getId()).orElse(null)
                : inventoryRepository.findForUpdateByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElse(null);
        if (inventory == null || inventory.availableQuantity() < 1) {
            log.warn("Skipped local sample order {} because its demo stock is unavailable.", phone);
            return;
        }

        String pricingFulfillment = "PICKUP".equals(fulfillmentType) ? "PICKUP" : "DELIVERY";
        CartItem item = CartItem.builder().product(product).quantity(1).build();
        OrderPricingService.Quote quote = pricingService.quote(List.of(item), 0, pricingFulfillment, 0);
        com.shopchain.entity.Order order = com.shopchain.entity.Order.builder().user(null).store(store).orderDate(orderDate)
                .subtotalAmount(quote.subtotal()).discountAmount(quote.discount()).shippingAmount(quote.shipping())
                .totalAmount(quote.total()).status(status).paymentMethod(paymentMethod).paymentStatus(paymentStatus)
                .fulfillmentType(fulfillmentType).fulfillmentWarehouse(warehouse)
                .customerName(customerName).customerPhone(phone)
                .deliveryAddress("Địa chỉ mô phỏng - không giao hàng thật")
                .deliveryCity(store.getCity()).paymentExpiresAt("UNPAID".equals(paymentStatus)
                        ? LocalDateTime.now().plusDays(7) : null)
                .paidAt("PAID".equals(paymentStatus) ? orderDate : null)
                .estimatedPickupAt("PICKUP".equals(fulfillmentType) && "PAID".equals(paymentStatus)
                        ? orderDate.plusMinutes(30) : null)
                .loyaltyPointsRedeemed(0).loyaltyPointsRestored(false)
                .orderItems(new ArrayList<>()).build();
        OrderItem line = OrderItem.builder().order(order).product(product).quantity(1)
                .price(pricingService.saleUnitPrice(product, 0))
                .listUnitPrice(pricingService.listUnitPrice(product)).build();
        order.getOrderItems().add(line);
        com.shopchain.entity.Order saved = orderRepository.save(order);

        inventory.setStockQuantity(inventory.getStockQuantity() - 1);
        inventoryRepository.save(inventory);
        InventoryMovement movement = InventoryMovement.builder().product(product).performedBy(null)
                .movementType("ONLINE_SALE").quantity(1).note("Đơn dữ liệu mẫu admin #" + saved.getId()).build();
        if (warehouse == null) movement.setStore(store); else movement.setWarehouse(warehouse);
        movementRepository.save(movement);

        if ("PAID".equals(paymentStatus)) invoiceService.issueForPaidOrder(saved.getId());
    }

}

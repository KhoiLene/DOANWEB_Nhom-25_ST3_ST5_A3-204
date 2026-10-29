package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {
    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private UserRepository userRepository;
    private StoreRepository storeRepository;
    private ProductInventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private NotificationService notificationService;
    private WarehouseRepository warehouseRepository;
    private LoyaltyService loyaltyService;
    private ProductRepository productRepository;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cartRepository = mock(CartRepository.class);
        userRepository = mock(UserRepository.class);
        storeRepository = mock(StoreRepository.class);
        inventoryRepository = mock(ProductInventoryRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        notificationService = mock(NotificationService.class);
        warehouseRepository = mock(WarehouseRepository.class);
        loyaltyService = mock(LoyaltyService.class);
        productRepository = mock(ProductRepository.class);
        service = new OrderServiceImpl(orderRepository, cartRepository, userRepository, storeRepository,
                inventoryRepository, movementRepository, notificationService, warehouseRepository,
                loyaltyService, productRepository, new OrderPricingService());
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void finalOrderUsesDisplayedProductCatalogPriceAndPersistsShippingBreakdown() {
        User user = user();
        Store store = store();
        Product product = product(true, 239_000d);
        CartItem cartItem = CartItem.builder().product(product).quantity(2).build();
        Cart cart = Cart.builder().user(user).items(new ArrayList<>(List.of(cartItem))).build();
        ProductInventory stock = ProductInventory.builder().product(product).store(store).price(999_000d)
                .stockQuantity(5).reservedQuantity(1).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(storeRepository.findForUpdateById(4L)).thenReturn(Optional.of(store));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(productRepository.findForUpdateById(3L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findForUpdateByProductIdAndStoreId(3L, 4L)).thenReturn(Optional.of(stock));
        when(movementRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        Order order = service.placeOnlineOrder(7L, 4L, "Demo", "0900000000", "12 Test", "Hồ Chí Minh",
                "DELIVERY", "COD", null, null);

        assertThat(OrderPricingService.money(product.effectivePrice(user.getLoyaltyPoints())))
                .isEqualByComparingTo(order.getOrderItems().get(0).getPrice());
        assertThat(order.getOrderItems().get(0).getPrice()).isEqualByComparingTo("239000");
        assertThat(order.getSubtotalAmount()).isEqualByComparingTo("478000");
        assertThat(order.getDiscountAmount()).isZero();
        assertThat(order.getShippingAmount()).isEqualByComparingTo("30000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("508000");
        assertThat(stock.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void confirmedPaidOrderCanAdvanceToProcessing() {
        Order order = order("CONFIRMED", "VNPAY", "PAID");
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        Order updated = service.updateOrderStatus(10L, "PROCESSING");

        assertThat(updated.getStatus()).isEqualTo("PROCESSING");
        verify(orderRepository).save(order);
    }

    @Test
    void repeatingCancellationDoesNotRestoreStockTwiceAndCancelledOrderCannotReopen() {
        Product product = product(true, 20_000d);
        Store store = store();
        Order order = order("PENDING", "COD", "UNPAID");
        order.setOrderItems(new ArrayList<>(List.of(OrderItem.builder().order(order).product(product)
                .quantity(2).price(new java.math.BigDecimal("20000")).build())));
        ProductInventory inventory = ProductInventory.builder().product(product).store(store).price(20_000d)
                .stockQuantity(3).reservedQuantity(0).build();
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(inventoryRepository.findForUpdateByProductIdAndStoreId(3L, 4L)).thenReturn(Optional.of(inventory));
        when(movementRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        service.updateOrderStatus(10L, "CANCELLED");
        assertThat(inventory.getStockQuantity()).isEqualTo(5);
        assertThatThrownBy(() -> service.updateOrderStatus(10L, "CANCELLED"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.updateOrderStatus(10L, "PENDING"))
                .isInstanceOf(IllegalStateException.class);

        verify(inventoryRepository, times(1)).save(inventory);
        verify(movementRepository, times(1)).saveAll(anyList());
    }

    @Test
    void duplicateVnPayCallbackConfirmsOnlyOnceAndExpiryAfterPaymentDoesNotReleaseStock() {
        Order order = order("PENDING", "VNPAY", "UNPAID");
        order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        Order first = service.confirmVnPayPayment(10L);
        Order duplicate = service.confirmVnPayPayment(10L);
        boolean expired = service.expireUnpaidVnPayOrder(10L, LocalDateTime.now().plusHours(1));

        assertThat(first.getStatus()).isEqualTo("CONFIRMED");
        assertThat(duplicate.getPaymentStatus()).isEqualTo("PAID");
        assertThat(expired).isFalse();
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void expiryWinsWhenItLocksFirstAndLaterPaymentCannotConfirmOrReleaseStockAgain() {
        Product product = product(true, 20_000d);
        Store store = store();
        Order order = order("PENDING", "VNPAY", "UNPAID");
        order.setPaymentExpiresAt(LocalDateTime.now().minusSeconds(1));
        order.setOrderItems(new ArrayList<>(List.of(OrderItem.builder().order(order).product(product)
                .quantity(1).price(new java.math.BigDecimal("20000")).build())));
        ProductInventory inventory = ProductInventory.builder().product(product).store(store).price(20_000d)
                .stockQuantity(4).reservedQuantity(0).build();
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(inventoryRepository.findForUpdateByProductIdAndStoreId(3L, 4L)).thenReturn(Optional.of(inventory));
        when(movementRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.expireUnpaidVnPayOrder(10L, LocalDateTime.now())).isTrue();
        assertThatThrownBy(() -> service.confirmVnPayPayment(10L)).isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getPaymentStatus()).isEqualTo("EXPIRED");
        assertThat(inventory.getStockQuantity()).isEqualTo(5);
        verify(inventoryRepository, times(1)).save(inventory);
    }

    @Test
    void productDisabledBeforeCheckoutIsRejectedBeforeAnyStockIsChanged() {
        User user = user();
        Store store = store();
        Product inactive = product(false, 20_000d);
        Cart cart = Cart.builder().user(user).items(new ArrayList<>(List.of(
                CartItem.builder().product(inactive).quantity(1).build()))).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(storeRepository.findForUpdateById(4L)).thenReturn(Optional.of(store));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(productRepository.findForUpdateById(3L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.placeOnlineOrder(7L, 4L, "Demo", "0900000000", "12 Test", "Hồ Chí Minh",
                "PICKUP", "COD", null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không còn được phép bán");
        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    private User user() { return User.builder().id(7L).username("buyer").loyaltyPoints(0).build(); }
    private Store store() { return Store.builder().id(4L).name("Store").city("Hồ Chí Minh").active(true).pickupEnabled(true).build(); }
    private Product product(boolean active, double price) {
        return Product.builder().id(3L).name("Item").price(price).isActive(active).build();
    }
    private Order order(String status, String method, String paymentStatus) {
        return Order.builder().id(10L).user(user()).store(store()).status(status).paymentMethod(method)
                .paymentStatus(paymentStatus).totalAmount(new java.math.BigDecimal("30000")).build();
    }
}

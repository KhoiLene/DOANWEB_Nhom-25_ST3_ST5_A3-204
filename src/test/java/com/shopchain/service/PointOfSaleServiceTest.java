package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PointOfSaleServiceTest {
    private UserRepository users;
    private ProductRepository products;
    private StoreRepository stores;
    private ProductInventoryRepository inventory;
    private OrderRepository orders;
    private SalesInvoiceRepository invoices;
    private InventoryMovementRepository movements;
    private LoyaltyService loyalty;
    private PointOfSaleService service;
    private User cashier;
    private Store store;
    private Product product;
    private ProductInventory stock;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        products = mock(ProductRepository.class);
        stores = mock(StoreRepository.class);
        inventory = mock(ProductInventoryRepository.class);
        orders = mock(OrderRepository.class);
        invoices = mock(SalesInvoiceRepository.class);
        movements = mock(InventoryMovementRepository.class);
        loyalty = mock(LoyaltyService.class);
        service = new PointOfSaleService(users, products, stores, inventory, orders, invoices, movements,
                loyalty, new OrderPricingService());
        store = Store.builder().id(4L).name("Store").active(true).pickupEnabled(true).build();
        cashier = User.builder().id(9L).username("cashier").store(store).build();
        product = Product.builder().id(3L).name("Serum").sku("S-1").price(100_000d).isActive(true).build();
        stock = ProductInventory.builder().product(product).store(store).price(200_000d)
                .stockQuantity(5).reservedQuantity(0).build();
        when(users.findByUsername("cashier")).thenReturn(Optional.of(cashier));
        when(stores.findForUpdateById(4L)).thenReturn(Optional.of(store));
        when(products.findForUpdateById(3L)).thenReturn(Optional.of(product));
        when(inventory.findForUpdateByProductIdAndStoreId(3L, 4L)).thenReturn(Optional.of(stock));
        when(orders.save(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId(11L);
            return order;
        });
        when(invoices.save(any(SalesInvoice.class))).thenAnswer(call -> {
            SalesInvoice invoice = call.getArgument(0);
            invoice.setId(21L);
            return invoice;
        });
        when(movements.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void guestSaleKeepsCashierSeparateAndDoesNotAttachByPhone() {
        SalesInvoice invoice = service.sell("cashier", null, "Walk-in", "0900000000", "CASH", null,
                false, List.of(new PointOfSaleService.SaleLine(3L, 2)));

        assertThat(invoice.getOrder().getUser()).isNull();
        assertThat(invoice.getIssuedBy()).isSameAs(cashier);
        assertThat(invoice.getOrder().getStore()).isSameAs(store);
        assertThat(invoice.getCustomerName()).isEqualTo("Walk-in");
        assertThat(invoice.getOrder().getTotalAmount()).isEqualByComparingTo("200000");
        assertThat(stock.getStockQuantity()).isEqualTo(3);
        verify(users, never()).findByPhone(anyString());
        verify(loyalty, never()).awardForPaidOrder(anyLong());
    }

    @Test
    void explicitlyConfirmedMemberIsLinkedAndVipPriceAndPointsUseLockedAccount() {
        User member = User.builder().id(5L).username("member").fullName("Member")
                .phone("0900000001").email("member@example.test").loyaltyPoints(500).enabled(true)
                .role(Role.builder().name("ROLE_USER").build()).build();
        when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(member));
        when(users.findByPhone("0900000001")).thenReturn(List.of(member));
        product.setDiscountPercent(20d);
        product.setCampaignAudience("VIP");
        product.setCampaignName("VIP");

        SalesInvoice invoice = service.sell("cashier", 5L, "Member", "0900000001", "CASH", null,
                false, List.of(new PointOfSaleService.SaleLine(3L, 2)));

        assertThat(invoice.getOrder().getUser()).isSameAs(member);
        assertThat(invoice.getCustomerEmail()).isEqualTo("member@example.test");
        assertThat(invoice.getLines().get(0).getListUnitPrice()).isEqualByComparingTo("100000");
        assertThat(invoice.getLines().get(0).getUnitPrice()).isEqualByComparingTo("80000");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("200000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("40000");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("160000");
        verify(users).findByIdForUpdate(5L);
        verify(loyalty).awardForPaidOrder(11L);
    }

    @Test
    void memberCannotBeAttachedWhenPhoneDoesNotMatchExplicitLookup() {
        User member = User.builder().id(5L).phone("0900000001").enabled(true)
                .role(Role.builder().name("ROLE_USER").build()).build();
        when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(member));
        when(users.findByPhone("0900000002")).thenReturn(List.of());
        when(users.findByPhone("0900000001")).thenReturn(List.of(member));

        assertThatThrownBy(() -> service.sell("cashier", 5L, "Member", "0900000002", "CASH", null,
                false, List.of(new PointOfSaleService.SaleLine(3L, 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không khớp");
        verify(orders, never()).save(any());
        assertThat(stock.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void duplicatePhoneMatchesCannotBeAttachedEvenWithSubmittedMemberId() {
        User selected = User.builder().id(5L).phone("0900000001").enabled(true)
                .role(Role.builder().name("ROLE_USER").build()).build();
        User duplicate = User.builder().id(6L).phone("0900000001").enabled(true)
                .role(Role.builder().name("ROLE_USER").build()).build();
        when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(selected));
        when(users.findByPhone("0900000001")).thenReturn(List.of(selected, duplicate));

        assertThatThrownBy(() -> service.sell("cashier", 5L, "Member", "0900000001", "CASH", null,
                false, List.of(new PointOfSaleService.SaleLine(3L, 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng nhiều tài khoản");
        verify(orders, never()).save(any());
        assertThat(stock.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void heldReservationConsumesStockAndReservedQuantityExactlyOnceAtSaleCommit() {
        User member = User.builder().id(5L).username("member").fullName("Member")
                .phone("0900000001").enabled(true).loyaltyPoints(0)
                .role(Role.builder().name("ROLE_USER").build()).build();
        when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(member));
        StoreReservation reservation = StoreReservation.builder().id(8L).reservationCode("TH12345678")
                .user(member).store(store).status("HELD").items(List.of(StoreReservationItem.builder()
                        .product(product).quantity(1).unitPrice(new BigDecimal("90000"))
                        .listUnitPrice(new BigDecimal("100000")).build())).build();
        stock.setReservedQuantity(1);

        SalesInvoice invoice = service.sellReserved("cashier", reservation, "CASH", null, false);

        assertThat(invoice.getOrder().getUser()).isSameAs(member);
        assertThat(invoice.getOrder().getFulfillmentType()).isEqualTo("PICKUP");
        assertThat(stock.getStockQuantity()).isEqualTo(4);
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("90000");
        verify(movements, times(1)).saveAll(anyList());
        verify(loyalty).awardForPaidOrder(11L);
    }

    @Test
    void bankTransferRequiresExplicitManualConfirmationAndInactiveStoreOrProductCannotSell() {
        assertThatThrownBy(() -> service.sell("cashier", null, null, null, "BANK_TRANSFER", "TT123456",
                false, List.of(new PointOfSaleService.SaleLine(3L, 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tự kiểm tra");

        store.setActive(false);
        assertThatThrownBy(() -> service.sell("cashier", null, null, null, "CASH", null,
                false, List.of(new PointOfSaleService.SaleLine(3L, 1))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("không hoạt động");
        store.setActive(true);
        product.setIsActive(false);
        assertThatThrownBy(() -> service.sell("cashier", null, null, null, "CASH", null,
                false, List.of(new PointOfSaleService.SaleLine(3L, 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không còn được phép bán");
        verify(orders, never()).save(any());
        assertThat(stock.getStockQuantity()).isEqualTo(5);
    }
}

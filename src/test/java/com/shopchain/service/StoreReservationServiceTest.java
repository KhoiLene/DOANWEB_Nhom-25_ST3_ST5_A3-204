package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StoreReservationServiceTest {
    private StoreRepository stores;
    private UserRepository users;
    private ProductRepository products;
    private ProductInventoryRepository inventory;
    private StoreReservationRepository reservations;
    private OrderRepository orders;
    private InventoryMovementRepository movements;
    private PointOfSaleService pos;
    private StoreReservationService service;
    private Store store;
    private User customer;
    private StoreReservation reservation;
    private Product product;
    private ProductInventory stock;
    private SalesInvoice invoice;

    @BeforeEach
    void setUp() {
        stores = mock(StoreRepository.class);
        users = mock(UserRepository.class);
        products = mock(ProductRepository.class);
        inventory = mock(ProductInventoryRepository.class);
        reservations = mock(StoreReservationRepository.class);
        orders = mock(OrderRepository.class);
        movements = mock(InventoryMovementRepository.class);
        pos = mock(PointOfSaleService.class);
        service = new StoreReservationService(stores, users, products, inventory, reservations, orders,
                movements, pos, new OrderPricingService(), 30);
        store = Store.builder().id(4L).name("Store").address("Address").active(true).pickupEnabled(true).build();
        customer = User.builder().id(5L).username("member").fullName("Member").phone("0900000001").build();
        product = Product.builder().id(3L).name("Serum").imageUrl("serum.jpg").price(100_000d).isActive(true).build();
        stock = ProductInventory.builder().product(product).store(store).price(200_000d)
                .stockQuantity(5).reservedQuantity(1).build();
        StoreReservationItem line = StoreReservationItem.builder().id(7L).product(product).quantity(1)
                .unitPrice(new java.math.BigDecimal("90000")).listUnitPrice(new java.math.BigDecimal("100000")).build();
        reservation = StoreReservation.builder().id(8L).reservationCode("TH12345678").user(customer).store(store)
                .status("HELD").createdAt(LocalDateTime.now().minusMinutes(1)).expiresAt(LocalDateTime.now().plusMinutes(10))
                .items(new ArrayList<>(List.of(line))).build();
        line.setReservation(reservation);
        Order sale = Order.builder().id(11L).user(customer).store(store).build();
        invoice = SalesInvoice.builder().id(21L).invoiceNumber("PICKUP-11").order(sale).build();
        when(reservations.findForUpdateById(8L)).thenReturn(Optional.of(reservation));
        when(reservations.save(any(StoreReservation.class))).thenAnswer(call -> call.getArgument(0));
        when(inventory.findForUpdateByProductIdAndStoreId(3L, 4L)).thenReturn(Optional.of(stock));
        when(movements.save(any(InventoryMovement.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void pickupCreatesSaleAndReturnsReceiptAsOneDelegatedTransaction() {
        when(pos.sellReserved("cashier", reservation, "CASH", null, false)).thenReturn(invoice);

        StoreReservationService.PickupResult result = service.pickup(8L, 4L, "cashier", "CASH", null, false);

        assertThat(result.invoiceId()).isEqualTo(21L);
        assertThat(result.invoiceNumber()).isEqualTo("PICKUP-11");
        assertThat(result.reservation().status()).isEqualTo("PICKED_UP");
        assertThat(reservation.getOrder()).isSameAs(invoice.getOrder());
        verify(pos, times(1)).sellReserved("cashier", reservation, "CASH", null, false);
        verify(inventory, never()).save(any());
    }

    @Test
    void repeatedPickupReturnsExistingReceiptWithoutCreatingAnotherSale() {
        reservation.setStatus("PICKED_UP");
        reservation.setOrder(invoice.getOrder());
        when(pos.getInvoiceForOrder(11L)).thenReturn(invoice);

        StoreReservationService.PickupResult result = service.pickup(8L, 4L, "cashier", "CASH", null, false);

        assertThat(result.invoiceId()).isEqualTo(21L);
        verify(pos, never()).sellReserved(anyString(), any(), anyString(), any(), anyBoolean());
        verify(inventory, never()).save(any());
    }

    @Test
    void expiredPickupReleasesHoldButDoesNotCreateSaleOrSubtractStock() {
        reservation.setExpiresAt(LocalDateTime.now().minusSeconds(1));

        StoreReservationService.PickupResult result = service.pickup(8L, 4L, "cashier", "CASH", null, false);

        assertThat(result.reservation().status()).isEqualTo("EXPIRED");
        assertThat(result.invoiceId()).isNull();
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(stock.getStockQuantity()).isEqualTo(5);
        verify(pos, never()).sellReserved(anyString(), any(), anyString(), any(), anyBoolean());
        verify(inventory).save(stock);
    }

    @Test
    void cancelledReservationAndReservationFromAnotherBranchCannotBePickedUp() {
        reservation.setStatus("CANCELLED");
        assertThatThrownBy(() -> service.pickup(8L, 4L, "cashier", "CASH", null, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hủy");
        reservation.setStatus("HELD");
        assertThatThrownBy(() -> service.pickup(8L, 99L, "cashier", "CASH", null, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc chi nhánh");
        verify(pos, never()).sellReserved(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void oldPickedReservationWithoutReceiptIsNotChargedOrDebitedAgain() {
        reservation.setStatus("PICKED_UP");
        reservation.setOrder(null);

        assertThatThrownBy(() -> service.pickup(8L, 4L, "cashier", "CASH", null, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đối soát");
        verify(pos, never()).sellReserved(anyString(), any(), anyString(), any(), anyBoolean());
        verify(inventory, never()).save(any());
    }
}

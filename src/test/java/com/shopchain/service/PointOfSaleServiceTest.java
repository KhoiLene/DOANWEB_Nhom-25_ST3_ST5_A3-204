package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PointOfSaleServiceTest {
    @Test
    void posUsesCatalogPriceAndReceiptKeepsDiscountBreakdown() {
        User cashier = User.builder().id(9L).username("cashier")
                .store(Store.builder().id(4L).name("Store").build()).build();
        Product product = Product.builder().id(3L).name("Serum").sku("S-1").price(100_000d)
                .isActive(true).discountPercent(10d).campaignAudience("ALL").campaignName("Sale").build();
        ProductInventory inventory = ProductInventory.builder().product(product).store(cashier.getStore())
                .price(200_000d).stockQuantity(5).reservedQuantity(0).build();
        UserRepository users = mock(UserRepository.class);
        ProductInventoryRepository stocks = mock(ProductInventoryRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
        InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
        when(users.findByUsername("cashier")).thenReturn(Optional.of(cashier));
        when(stocks.findForUpdateByProductIdAndStoreId(3L, 4L)).thenReturn(Optional.of(inventory));
        when(orders.save(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId(11L);
            return order;
        });
        when(invoices.save(any(SalesInvoice.class))).thenAnswer(call -> call.getArgument(0));
        when(movements.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        PointOfSaleService service = new PointOfSaleService(users, stocks, orders, invoices, movements,
                new OrderPricingService());

        SalesInvoice invoice = service.sell("cashier", "Demo", null, "CASH", null,
                List.of(new PointOfSaleService.SaleLine(3L, 2)));

        assertThat(invoice.getOrder().getOrderItems().get(0).getPrice()).isEqualByComparingTo("90000");
        assertThat(invoice.getOrder().getOrderItems().get(0).getListUnitPrice()).isEqualByComparingTo("100000");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("200000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("20000");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("180000");
        assertThat(inventory.getStockQuantity()).isEqualTo(3);
    }
}

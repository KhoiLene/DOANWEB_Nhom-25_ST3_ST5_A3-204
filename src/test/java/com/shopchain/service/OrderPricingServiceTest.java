package com.shopchain.service;

import com.shopchain.entity.CartItem;
import com.shopchain.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPricingServiceTest {
    private final OrderPricingService pricing = new OrderPricingService();

    @Test
    void deliveryQuoteAppliesDiscountAndShippingOnceWithWholeDongRounding() {
        Product product = product(199_999d, 12.5d);
        CartItem line = CartItem.builder().product(product).quantity(2).build();

        var quote = pricing.quote(List.of(line), 0, "DELIVERY");

        assertThat(quote.subtotal()).isEqualByComparingTo("399998");
        assertThat(quote.merchandiseTotal()).isEqualByComparingTo("349998");
        assertThat(quote.discount()).isEqualByComparingTo("50000");
        assertThat(quote.shipping()).isEqualByComparingTo("30000");
        assertThat(quote.total()).isEqualByComparingTo("379998");
    }

    @Test
    void pickupDoesNotChargeShippingAndFreeShippingUsesDiscountedMerchandiseTotal() {
        var pickup = pricing.quote(List.of(CartItem.builder().product(product(120_000d, null)).quantity(1).build()), 0, "PICKUP");
        var freeDelivery = pricing.quote(List.of(CartItem.builder().product(product(500_000d, null)).quantity(1).build()), 0, "DELIVERY");

        assertThat(pickup.shipping()).isZero();
        assertThat(pickup.total()).isEqualByComparingTo("120000");
        assertThat(freeDelivery.shipping()).isZero();
    }

    @Test
    void publicDisplayedUnitPriceMatchesQuoteUnitAndIgnoresBranchInventoryPrice() {
        Product product = product(239_000d, 10d);
        CartItem line = CartItem.builder().product(product).quantity(1).build();

        var quote = pricing.quote(List.of(line), 0, "PICKUP");
        double displayedPrice = product.effectivePrice(0);

        assertThat(OrderPricingService.money(displayedPrice)).isEqualByComparingTo(quote.merchandiseTotal());
        assertThat(product.getPrice()).isEqualTo(239_000d);
    }

    @Test
    void optionalRedemptionReducesTotalAtOneHundredDongPerPointAndKeepsShippingSeparate() {
        var line = CartItem.builder().product(product(120_000d, null)).quantity(1).build();

        var unused = pricing.quote(List.of(line), 500, "DELIVERY", 0);
        var redeemed = pricing.quote(List.of(line), 500, "DELIVERY", 100);
        var pickup = pricing.quote(List.of(line), 500, "PICKUP", 100);

        assertThat(unused.total()).isEqualByComparingTo("150000");
        assertThat(redeemed.pointsDiscount()).isEqualByComparingTo("10000");
        assertThat(redeemed.discount()).isEqualByComparingTo("10000");
        assertThat(redeemed.total()).isEqualByComparingTo("140000");
        assertThat(pickup.shipping()).isZero();
        assertThat(pickup.total()).isEqualByComparingTo("110000");
    }

    @Test
    void redemptionCannotExceedBalanceOrMerchandiseValue() {
        var line = CartItem.builder().product(product(120d, null)).quantity(1).build();

        assertThatThrownBy(() -> pricing.quote(List.of(line), 50, "PICKUP", 51))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đủ điểm");
        assertThatThrownBy(() -> pricing.quote(List.of(line), 50, "PICKUP", 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vượt quá");
    }

    private Product product(double price, Double discount) {
        return Product.builder().name("Test product").price(price).isActive(true)
                .discountPercent(discount).campaignAudience("ALL").build();
    }
}

package com.shopchain.service;

import com.shopchain.entity.Cart;
import com.shopchain.entity.CartItem;
import com.shopchain.entity.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Single source of truth for online merchandise prices, discounts, shipping and totals. */
@Service
public class OrderPricingService {
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500000");
    public static final BigDecimal STANDARD_SHIPPING = new BigDecimal("30000");
    public static final BigDecimal VND_PER_LOYALTY_POINT = new BigDecimal("100");

    public Quote quote(Cart cart, Integer loyaltyPoints, String fulfillmentType) {
        return quote(cart == null ? null : cart.getItems(), loyaltyPoints, fulfillmentType, 0);
    }

    public Quote quote(Cart cart, Integer loyaltyPoints, String fulfillmentType, Integer redeemPoints) {
        return quote(cart == null ? null : cart.getItems(), loyaltyPoints, fulfillmentType, redeemPoints);
    }

    public Quote quote(List<CartItem> items, Integer loyaltyPoints, String fulfillmentType) {
        return quote(items, loyaltyPoints, fulfillmentType, 0);
    }

    public Quote quote(List<CartItem> items, Integer loyaltyPoints, String fulfillmentType, Integer requestedPoints) {
        if (requestedPoints == null || requestedPoints < 0) throw new IllegalArgumentException("Số điểm sử dụng không hợp lệ.");
        if (items == null || items.isEmpty()) {
            if (requestedPoints > 0) throw new IllegalArgumentException("Giỏ hàng trống, không thể đổi điểm.");
            return new Quote(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }
        boolean pickup = "PICKUP".equalsIgnoreCase(fulfillmentType);
        if (!pickup && !"DELIVERY".equalsIgnoreCase(fulfillmentType)) {
            throw new IllegalArgumentException("Vui lòng chọn cách nhận hàng.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal merchandiseTotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null || item.getQuantity() == null
                    || item.getQuantity() <= 0 || item.getQuantity() > 99) {
                throw new IllegalArgumentException("Sản phẩm trong giỏ hàng chưa hợp lệ.");
            }
            Product product = item.getProduct();
            BigDecimal listUnit = listUnitPrice(product);
            if (listUnit.signum() < 0) throw new IllegalArgumentException("Giá sản phẩm không hợp lệ.");
            BigDecimal saleUnit = saleUnitPrice(product, loyaltyPoints);
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
            subtotal = subtotal.add(listUnit.multiply(quantity));
            merchandiseTotal = merchandiseTotal.add(saleUnit.multiply(quantity));
            discount = discount.add(listUnit.subtract(saleUnit).max(BigDecimal.ZERO).multiply(quantity));
        }
        BigDecimal deliveryShipping = merchandiseTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : STANDARD_SHIPPING;
        BigDecimal shipping = pickup ? BigDecimal.ZERO : deliveryShipping;
        int availablePoints = Math.max(0, loyaltyPoints == null ? 0 : loyaltyPoints);
        int maxRedeemablePoints = merchandiseTotal.divideToIntegralValue(VND_PER_LOYALTY_POINT).intValue();
        if (requestedPoints > availablePoints) throw new IllegalArgumentException("Bạn không có đủ điểm để đổi.");
        if (requestedPoints > maxRedeemablePoints) throw new IllegalArgumentException("Số điểm đổi vượt quá số tiền hàng có thể giảm.");
        BigDecimal pointsDiscount = VND_PER_LOYALTY_POINT.multiply(BigDecimal.valueOf(requestedPoints));
        return new Quote(subtotal, discount.add(pointsDiscount), merchandiseTotal, shipping, deliveryShipping,
                merchandiseTotal.subtract(pointsDiscount).add(shipping), requestedPoints, pointsDiscount);
    }

    public BigDecimal listUnitPrice(Product product) {
        if (product == null) throw new IllegalArgumentException("Không tìm thấy sản phẩm.");
        return money(product.getPrice());
    }

    public BigDecimal saleUnitPrice(Product product, Integer loyaltyPoints) {
        if (product == null) throw new IllegalArgumentException("Không tìm thấy sản phẩm.");
        return money(product.effectivePrice(product.getPrice(), loyaltyPoints));
    }

    public static BigDecimal money(Double value) {
        if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("Giá sản phẩm không hợp lệ.");
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }

    public static double asLegacyDouble(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).doubleValue();
    }

    public record Quote(BigDecimal subtotal, BigDecimal discount, BigDecimal merchandiseTotal,
                        BigDecimal shipping, BigDecimal deliveryShipping, BigDecimal total,
                        int redeemedPoints, BigDecimal pointsDiscount) { }
}

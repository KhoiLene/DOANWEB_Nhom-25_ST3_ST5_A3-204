package com.shopchain.entity;

import java.util.List;
import java.util.Locale;

/** Legal order transitions shared by all staff/admin screens and service operations. */
public enum OrderStatus {
    PENDING, CONFIRMED, PROCESSING, SHIPPING, DELIVERED, CANCELLED, RETURNED;

    public static List<String> allowedTargets(Order order) {
        String current = normalize(order.getStatus());
        String payment = normalize(order.getPaymentStatus());
        return switch (current) {
            case "PENDING" -> {
                if ("VNPAY".equalsIgnoreCase(order.getPaymentMethod()) && !"PAID".equals(payment)) {
                    yield "UNPAID".equals(payment) ? List.of("CANCELLED") : List.of();
                }
                yield List.of("PROCESSING", "CANCELLED");
            }
            case "CONFIRMED" -> "PAID".equals(payment)
                    ? List.of("PROCESSING") : List.of("PROCESSING", "CANCELLED");
            case "PROCESSING" -> "PAID".equals(payment)
                    ? List.of("SHIPPING") : List.of("SHIPPING", "CANCELLED");
            case "SHIPPING" -> List.of("DELIVERED");
            default -> List.of();
        };
    }

    public static boolean canTransition(Order order, String target) {
        return target != null && allowedTargets(order).contains(normalize(target));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

package com.shopchain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRequest {
    private String message;
    private String type; // ORDER_NEW, ORDER_STATUS_UPDATE, SYSTEM_ALERT
    private Long orderId;
}

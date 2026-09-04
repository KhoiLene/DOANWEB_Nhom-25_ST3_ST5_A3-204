package com.shopchain.service;

import com.shopchain.dto.NotificationRequest;

public interface NotificationService {
    void sendNotificationToStore(Long storeId, NotificationRequest notification);
    void sendNotificationToUser(Long userId, NotificationRequest notification);
}

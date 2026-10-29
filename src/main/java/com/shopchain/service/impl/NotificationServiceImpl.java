package com.shopchain.service.impl;

import com.shopchain.dto.NotificationRequest;
import com.shopchain.service.NotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationServiceImpl(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void sendNotificationToStore(Long storeId, NotificationRequest notification) {
        // WebSocket notification
        String destination = "/queue/store/" + storeId;
        messagingTemplate.convertAndSend(destination, notification);

    }

    @Override
    public void sendNotificationToUser(Long userId, NotificationRequest notification) {
        // WebSocket notification
        String destination = "/queue/user/" + userId;
        messagingTemplate.convertAndSend(destination, notification);

    }
}

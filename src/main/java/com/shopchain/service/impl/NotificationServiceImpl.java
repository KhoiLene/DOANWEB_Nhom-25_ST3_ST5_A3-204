package com.shopchain.service.impl;

import com.shopchain.dto.NotificationRequest;
import com.shopchain.service.NotificationService;
import com.shopchain.service.SystemSettingService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SystemSettingService settingService;

    public NotificationServiceImpl(SimpMessagingTemplate messagingTemplate, SystemSettingService settingService) {
        this.messagingTemplate = messagingTemplate;
        this.settingService = settingService;
    }

    @Override
    public void sendNotificationToStore(Long storeId, NotificationRequest notification) {
        // WebSocket notification
        String destination = "/queue/store/" + storeId;
        messagingTemplate.convertAndSend(destination, notification);

        // TODO: implement Email/Zalo notification using settingService
        // String email = settingService.getSetting("smtp.user", "");
        // ...
    }

    @Override
    public void sendNotificationToUser(Long userId, NotificationRequest notification) {
        // WebSocket notification
        String destination = "/queue/user/" + userId;
        messagingTemplate.convertAndSend(destination, notification);

        // TODO: implement Email/Zalo notification using settingService
    }

    // New methods for external notifications
    public void sendGmail(String to, String subject, String content) {
        String host = settingService.getSetting("smtp.host", "smtp.gmail.com");
        String port = settingService.getSetting("smtp.port", "587");
        String user = settingService.getSetting("smtp.user", "");
        String pass = settingService.getSetting("smtp.pass", "");

        System.out.println("Sending Gmail via " + host + ":" + port + " from " + user);
        // Implementation using JavaMailSender would go here
    }

    public void sendZalo(String phone, String message) {
        String accessToken = settingService.getSetting("zalo.accessToken", "");
        String appId = settingService.getSetting("zalo.appId", "");

        System.out.println("Sending Zalo message to " + phone + " using app " + appId);
        // Implementation using RestTemplate/WebClient to Zalo API would go here
    }
}

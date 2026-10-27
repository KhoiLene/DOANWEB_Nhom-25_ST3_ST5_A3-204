package com.shopchain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable the in-memory broker for shared and per-user/store notifications.
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint for clients.
        registry.addEndpoint("/ws-shop").withSockJS();
        registry.addEndpoint("/ws-messages");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith("/topic/internal/")) return message;
                if (StompCommand.SEND == accessor.getCommand()) {
                    throw new org.springframework.security.access.AccessDeniedException("Chỉ gửi tin nhắn qua ứng dụng.");
                }
                if (StompCommand.SUBSCRIBE != accessor.getCommand()) return message;
                String role = destination.substring("/topic/internal/".length());
                if (!(accessor.getUser() instanceof Authentication authentication)
                        || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(role))) {
                    throw new org.springframework.security.access.AccessDeniedException("Không thể theo dõi tin nhắn bộ phận khác.");
                }
                return message;
            }
        });
    }
}

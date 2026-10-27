package com.shopchain.config;

import com.shopchain.entity.User;
import com.shopchain.repository.UserRepository;
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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Set<String> INTERNAL_ROLES = Set.of("ROLE_ADMIN", "ROLE_WAREHOUSE", "ROLE_STORE_STAFF");
    private static final Pattern USER_QUEUE = Pattern.compile("^/queue/user/([1-9][0-9]{0,17})$");
    private static final Pattern STORE_QUEUE = Pattern.compile("^/queue/store/([1-9][0-9]{0,17})$");

    private final UserRepository userRepository;

    public WebSocketConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
                if (StompCommand.SEND == accessor.getCommand()) {
                    throw denied();
                }
                if (StompCommand.SUBSCRIBE != accessor.getCommand()) return message;
                if (!(accessor.getUser() instanceof Authentication authentication)
                        || !canSubscribe(destination, authentication)) throw denied();
                return message;
            }
        });
    }

    private boolean canSubscribe(String destination, Authentication authentication) {
        if (destination == null || !authentication.isAuthenticated()) return false;
        Set<String> roles = authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
        if (destination.startsWith("/topic/internal/")) {
            String role = destination.substring("/topic/internal/".length());
            return INTERNAL_ROLES.contains(role) && roles.contains(role);
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) return false;
        if ("ROLE_ADMIN".equals(user.getRole() == null ? null : user.getRole().getName())) return true;

        Matcher userQueue = USER_QUEUE.matcher(destination);
        if (userQueue.matches()) {
            try {
                return user.getId().equals(Long.parseLong(userQueue.group(1)));
            } catch (NumberFormatException exception) {
                return false;
            }
        }

        Matcher storeQueue = STORE_QUEUE.matcher(destination);
        if (storeQueue.matches() && roles.contains("ROLE_STORE_STAFF") && user.getStore() != null) {
            try {
                return user.getStore().getId().equals(Long.parseLong(storeQueue.group(1)));
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return false;
    }

    private org.springframework.security.access.AccessDeniedException denied() {
        return new org.springframework.security.access.AccessDeniedException("Không có quyền truy cập kênh WebSocket này.");
    }
}

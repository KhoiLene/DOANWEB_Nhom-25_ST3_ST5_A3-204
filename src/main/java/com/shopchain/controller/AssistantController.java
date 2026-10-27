package com.shopchain.controller;

import com.shopchain.service.RoleAssistantService;
import com.shopchain.service.RoleAssistantService.ChatTurn;
import com.shopchain.service.BeautyProfileService;
import com.shopchain.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final RoleAssistantService assistantService;
    private final BeautyProfileService beautyProfileService;
    private final UserRepository userRepository;

    public AssistantController(RoleAssistantService assistantService, BeautyProfileService beautyProfileService,
                               UserRepository userRepository) {
        this.assistantService = assistantService;
        this.beautyProfileService = beautyProfileService;
        this.userRepository = userRepository;
    }

    @GetMapping("/context")
    public RoleAssistantService.AssistantContext context(Authentication authentication) {
        return assistantService.context(resolveRole(authentication));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request, Authentication authentication) {
        try {
            if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "Yêu cầu không hợp lệ."));
            String role = resolveRole(authentication);
            String profileContext = "";
            if ("customer".equals(role) && authentication != null && authentication.isAuthenticated()) {
                profileContext = userRepository.findByUsername(authentication.getName())
                        .map(user -> beautyProfileService.assistantContext(user.getId())).orElse("");
            }
            return ResponseEntity.ok(assistantService.ask(role, request.message(), request.history(), profileContext));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (com.shopchain.service.GeminiApiClient.GeminiApiException exception) {
            return ResponseEntity.status(503).body(Map.of("error", exception.getMessage()));
        }
    }

    private String resolveRole(Authentication authentication) {
        // Anonymous storefront visitors receive only the public customer assistant corpus.
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) return "customer";
        if (authentication.getAuthorities() == null) return "customer";
        List<String> roles = authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();
        if (roles.contains("ROLE_ADMIN")) return "admin";
        if (roles.contains("ROLE_WAREHOUSE")) return "warehouse";
        if (roles.contains("ROLE_STORE_STAFF")) return "store-staff";
        if (roles.contains("ROLE_USER")) return "customer";
        throw new org.springframework.security.access.AccessDeniedException("Vai trò này chưa có trợ lý riêng.");
    }

    public record ChatRequest(String message, List<ChatTurn> history) { }
}

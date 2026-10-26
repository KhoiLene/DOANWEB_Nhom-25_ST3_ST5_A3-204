package com.shopchain.controller;

import com.shopchain.service.RoleAssistantService;
import com.shopchain.service.RoleAssistantService.ChatTurn;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final RoleAssistantService assistantService;

    public AssistantController(RoleAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/context")
    public RoleAssistantService.AssistantContext context(Authentication authentication) {
        return assistantService.context(resolveRole(authentication));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request, Authentication authentication) {
        try {
            if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "Yêu cầu không hợp lệ."));
            return ResponseEntity.ok(assistantService.ask(resolveRole(authentication), request.message(), request.history()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (com.shopchain.service.GeminiApiClient.GeminiApiException exception) {
            return ResponseEntity.status(503).body(Map.of("error", exception.getMessage()));
        }
    }

    private String resolveRole(Authentication authentication) {
        // Anonymous storefront visitors receive only the public customer assistant corpus.
        if (authentication == null) return "customer";
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

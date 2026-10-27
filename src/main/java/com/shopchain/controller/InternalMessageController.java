package com.shopchain.controller;

import com.shopchain.entity.InternalMessage;
import com.shopchain.repository.InternalMessageRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/messages")
public class InternalMessageController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy");
    private final InternalMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public InternalMessageController(InternalMessageRepository messageRepository, UserRepository userRepository,
                                     SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public String inbox(Model model, Authentication authentication) {
        String role = currentRole(authentication);
        model.addAttribute("departments", allowedRecipients(role));
        model.addAttribute("currentRole", role);
        model.addAttribute("workAreaUrl", switch (role) {
            case "ROLE_ADMIN" -> "/admin/dashboard";
            case "ROLE_WAREHOUSE" -> "/warehouse/orders";
            default -> "/staff/pos";
        });
        model.addAttribute("pageTitle", "Tin nhắn nội bộ");
        return "internal-messages";
    }

    @GetMapping("/api/{otherRole}")
    @ResponseBody
    public ResponseEntity<?> conversation(@PathVariable String otherRole, Authentication authentication) {
        String role = currentRole(authentication);
        if (!canContact(role, otherRole)) return ResponseEntity.badRequest().body(Map.of("error", "Bộ phận không hợp lệ."));
        List<InternalMessage> recent = new ArrayList<>(messageRepository.findConversation(role, otherRole, PageRequest.of(0, 100)));
        Collections.reverse(recent);
        return ResponseEntity.ok(recent.stream().map(this::view).toList());
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<?> sendLive(@RequestBody SendRequest request, Authentication authentication) {
        try {
            return ResponseEntity.ok(view(saveMessage(request.recipientRole(), request.body(), authentication)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping
    public String send(@RequestParam String recipientRole, @RequestParam String body,
                       Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            saveMessage(recipientRole, body, authentication);
            redirectAttributes.addFlashAttribute("messageSent", "Đã gửi tin nhắn.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("messageError", exception.getMessage());
        }
        return "redirect:/messages";
    }

    private InternalMessage saveMessage(String recipientRole, String body, Authentication authentication) {
        String role = currentRole(authentication);
        if (!canContact(role, recipientRole)) throw new IllegalArgumentException("Bạn không thể gửi tới bộ phận đã chọn.");
        String cleanBody = body == null ? "" : body.trim();
        if (cleanBody.isBlank() || cleanBody.length() > 1500)
            throw new IllegalArgumentException("Tin nhắn cần có nội dung và không vượt quá 1.500 ký tự.");
        var sender = userRepository.findByUsername(authentication.getName()).orElseThrow();
        InternalMessage saved = messageRepository.save(InternalMessage.builder().sender(sender)
                .recipientRole(recipientRole).body(cleanBody).createdAt(LocalDateTime.now()).build());
        MessageView payload = view(saved);
        messagingTemplate.convertAndSend("/topic/internal/" + role, payload);
        messagingTemplate.convertAndSend("/topic/internal/" + recipientRole, payload);
        return saved;
    }

    private MessageView view(InternalMessage message) {
        var sender = message.getSender();
        String displayName = sender.getFullName() == null || sender.getFullName().isBlank()
                ? sender.getUsername() : sender.getFullName();
        return new MessageView(message.getId(), displayName, sender.getRole().getName(),
                message.getRecipientRole(), message.getBody(), message.getCreatedAt().format(TIME_FORMAT));
    }

    private String currentRole(Authentication authentication) {
        if (authentication == null) throw new org.springframework.security.access.AccessDeniedException("Cần đăng nhập.");
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
        if (roles.contains("ROLE_WAREHOUSE")) return "ROLE_WAREHOUSE";
        if (roles.contains("ROLE_STORE_STAFF")) return "ROLE_STORE_STAFF";
        throw new org.springframework.security.access.AccessDeniedException("Vai trò không có hộp thư nội bộ.");
    }

    private boolean canContact(String currentRole, String otherRole) {
        return allowedRecipients(currentRole).stream().anyMatch(department -> department.role().equals(otherRole));
    }

    private List<Department> allowedRecipients(String role) {
        if (role.equals("ROLE_ADMIN")) return List.of(new Department("ROLE_STORE_STAFF", "Nhân viên cửa hàng"),
                new Department("ROLE_WAREHOUSE", "Nhân viên kho"));
        if (role.equals("ROLE_STORE_STAFF")) return List.of(new Department("ROLE_ADMIN", "Quản trị viên"),
                new Department("ROLE_WAREHOUSE", "Nhân viên kho"));
        return List.of(new Department("ROLE_ADMIN", "Quản trị viên"),
                new Department("ROLE_STORE_STAFF", "Nhân viên cửa hàng"));
    }

    public record Department(String role, String label) { }
    public record SendRequest(String recipientRole, String body) { }
    public record MessageView(Long id, String senderName, String senderRole, String recipientRole,
                              String body, String createdAt) { }
}

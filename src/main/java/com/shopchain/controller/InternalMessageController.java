package com.shopchain.controller;

import com.shopchain.entity.InternalMessage;
import com.shopchain.repository.InternalMessageRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/messages")
public class InternalMessageController {
    private final InternalMessageRepository messageRepository;
    private final UserRepository userRepository;

    public InternalMessageController(InternalMessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String inbox(Model model, Authentication authentication) {
        String username = authentication.getName();
        List<String> roles = roleNames(authentication);
        boolean admin = roles.contains("ROLE_ADMIN");
        model.addAttribute("messages", admin
                ? messageRepository.findTop100ByOrderByCreatedAtDesc()
                : messageRepository.findVisibleTo(roles, username, PageRequest.of(0, 100)));
        model.addAttribute("departments", allowedRecipients(roles));
        model.addAttribute("currentUsername", username);
        model.addAttribute("pageTitle", "Tin nhắn nội bộ");
        return "internal-messages";
    }

    @PostMapping
    public String send(@RequestParam String recipientRole, @RequestParam String body,
                       Authentication authentication, RedirectAttributes redirectAttributes) {
        List<String> roles = roleNames(authentication);
        List<Department> recipients = allowedRecipients(roles);
        if (recipients.stream().noneMatch(department -> department.role().equals(recipientRole))) {
            redirectAttributes.addFlashAttribute("messageError", "Bạn không thể gửi tới bộ phận đã chọn.");
            return "redirect:/messages";
        }
        String cleanBody = body == null ? "" : body.trim();
        if (cleanBody.isBlank() || cleanBody.length() > 1500) {
            redirectAttributes.addFlashAttribute("messageError", "Tin nhắn cần có nội dung và không vượt quá 1.500 ký tự.");
            return "redirect:/messages";
        }
        var sender = userRepository.findByUsername(authentication.getName()).orElseThrow();
        messageRepository.save(InternalMessage.builder().sender(sender).recipientRole(recipientRole)
                .body(cleanBody).createdAt(LocalDateTime.now()).build());
        redirectAttributes.addFlashAttribute("messageSent", "Đã gửi tin nhắn tới " + labelFor(recipientRole) + ".");
        return "redirect:/messages";
    }

    private List<String> roleNames(Authentication authentication) {
        return authentication.getAuthorities().stream().map(granted -> granted.getAuthority()).toList();
    }

    private List<Department> allowedRecipients(List<String> roles) {
        if (roles.contains("ROLE_ADMIN")) {
            return List.of(new Department("ROLE_STORE_STAFF", "Nhân viên cửa hàng"),
                    new Department("ROLE_WAREHOUSE", "Nhân viên kho"));
        }
        if (roles.contains("ROLE_STORE_STAFF")) {
            return List.of(new Department("ROLE_ADMIN", "Quản trị viên"),
                    new Department("ROLE_WAREHOUSE", "Nhân viên kho"));
        }
        return List.of(new Department("ROLE_ADMIN", "Quản trị viên"),
                new Department("ROLE_STORE_STAFF", "Nhân viên cửa hàng"));
    }

    private String labelFor(String role) {
        return switch (role) {
            case "ROLE_ADMIN" -> "Quản trị viên";
            case "ROLE_STORE_STAFF" -> "Nhân viên cửa hàng";
            case "ROLE_WAREHOUSE" -> "Nhân viên kho";
            default -> "bộ phận";
        };
    }

    public record Department(String role, String label) { }
}

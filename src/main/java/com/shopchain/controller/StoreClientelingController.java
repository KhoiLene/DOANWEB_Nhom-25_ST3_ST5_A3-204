package com.shopchain.controller;

import com.shopchain.service.StoreClientelingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/staff/customers")
public class StoreClientelingController {
    private final StoreClientelingService clientelingService;

    public StoreClientelingController(StoreClientelingService clientelingService) {
        this.clientelingService = clientelingService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String phone, Authentication authentication) {
        try {
            StoreClientelingService.MemberCard member = clientelingService.lookup(authentication.getName(), phone);
            if (member == null) return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy thành viên. Có thể tiếp tục bán như khách lẻ."));
            return ResponseEntity.ok(member);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}

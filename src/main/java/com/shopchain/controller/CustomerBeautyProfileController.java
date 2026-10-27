package com.shopchain.controller;

import com.shopchain.entity.User;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.BeautyProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/beauty-profile")
public class CustomerBeautyProfileController {
    private final UserRepository userRepository;
    private final BeautyProfileService profileService;

    public CustomerBeautyProfileController(UserRepository userRepository, BeautyProfileService profileService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
    }

    @GetMapping
    public BeautyProfileService.BeautyProfileView get(Authentication authentication) {
        return profileService.get(currentUser(authentication).getId());
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestBody BeautyProfileService.ProfileUpdate update, Authentication authentication) {
        try {
            return ResponseEntity.ok(profileService.save(currentUser(authentication), update));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/routine")
    public BeautyProfileService.CareRoutine routine(Authentication authentication) {
        return profileService.routine(currentUser(authentication).getId());
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản."));
    }
}

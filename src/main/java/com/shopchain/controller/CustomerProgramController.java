package com.shopchain.controller;

import com.shopchain.service.CustomerProgramService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer/promotions")
public class CustomerProgramController {
    private final CustomerProgramService programService;
    private final com.shopchain.repository.UserRepository userRepository;

    public CustomerProgramController(CustomerProgramService programService,
                                     com.shopchain.repository.UserRepository userRepository) {
        this.programService = programService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<CustomerProgramService.ProgramCard> list(Authentication authentication) {
        return programService.listFor(currentUserId(authentication));
    }

    @PostMapping("/{promotionId}/join")
    public Map<String, Object> join(@PathVariable UUID promotionId, Authentication authentication) {
        var joined = programService.join(currentUserId(authentication), promotionId);
        return Map.of("joined", joined.joined(), "title", joined.promotion().getTitle());
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow().getId();
    }
}

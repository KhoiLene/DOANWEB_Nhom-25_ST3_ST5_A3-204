package com.shopchain.config;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AssistantKnowledgeInitializer implements CommandLineRunner {
    private static final Map<String, String> SEED_FILES = Map.of(
            "customer", "customer.md",
            "store-staff", "store-staff.md",
            "warehouse", "warehouse.md",
            "admin", "admin.md");

    private final AssistantKnowledgeSourceRepository repository;

    public AssistantKnowledgeInitializer(AssistantKnowledgeSourceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        for (var seed : SEED_FILES.entrySet()) {
            if (repository.countByRoleKey(seed.getKey()) > 0) continue;
            String content;
            try (var input = new ClassPathResource("assistant-knowledge/" + seed.getValue()).getInputStream()) {
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            repository.save(AssistantKnowledgeSource.builder()
                    .roleKey(seed.getKey())
                    .title("Hướng dẫn khởi đầu")
                    .content(content)
                    .active(true)
                    .build());
        }
    }
}

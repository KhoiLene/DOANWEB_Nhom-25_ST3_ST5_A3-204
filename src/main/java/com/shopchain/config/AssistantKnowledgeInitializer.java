package com.shopchain.config;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class AssistantKnowledgeInitializer implements CommandLineRunner {
    private static final Map<String, String> SEED_FILES = Map.of(
            "customer", "customer.md",
            "store-staff", "store-staff.md",
            "warehouse", "warehouse.md",
            "admin", "admin.md");
    private static final List<KnowledgeSeed> ROLE_GUIDES = List.of(
            new KnowledgeSeed("customer", "Cẩm nang làm đẹp cơ bản cho khách hàng mới", "customer-beauty-guide.md"),
            new KnowledgeSeed("customer", "Danh mục mỹ phẩm và gợi ý chọn sản phẩm", "customer-product-guide.md"),
            new KnowledgeSeed("customer", "Cẩm nang chăm sóc tóc từ cơ bản đến chi tiết", "customer-hair-care-guide.md"),
            new KnowledgeSeed("customer", "Cẩm nang chăm sóc môi từ cơ bản đến chi tiết", "customer-lip-care-guide.md"),
            new KnowledgeSeed("customer", "Cẩm nang tìm chi nhánh và chọn nơi nhận hàng", "customer-store-finder-guide.md"),
            new KnowledgeSeed("store-staff", "Cẩm nang thao tác nhân viên cửa hàng", "store-staff-guide.md"),
            new KnowledgeSeed("warehouse", "Cẩm nang thao tác nhân viên kho", "warehouse-guide.md"),
            new KnowledgeSeed("admin", "Cẩm nang thao tác admin", "admin-guide.md"));

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
        for (KnowledgeSeed seed : ROLE_GUIDES) {
            if (repository.existsByRoleKeyAndTitle(seed.roleKey(), seed.title())) continue;
            String content;
            try (var input = new ClassPathResource("assistant-knowledge/" + seed.fileName()).getInputStream()) {
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            repository.save(AssistantKnowledgeSource.builder()
                    .roleKey(seed.roleKey())
                    .title(seed.title())
                    .content(content)
                    .active(true)
                    .build());
        }
    }

    private record KnowledgeSeed(String roleKey, String title, String fileName) { }
}

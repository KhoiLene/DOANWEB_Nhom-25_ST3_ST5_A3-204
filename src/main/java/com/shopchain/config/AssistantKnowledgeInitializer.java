package com.shopchain.config;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import com.shopchain.repository.SystemSettingRepository;
import com.shopchain.service.AssistantKnowledgeChangedEvent;
import com.shopchain.service.AssistantKnowledgeVectorIndexService;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AssistantKnowledgeInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AssistantKnowledgeInitializer.class);
    private static final String REVISION_SETTING_KEY = "assistant.knowledge.seedRevision";
    private static final String CURRENT_SEED_REVISION = "2026-10-30-role-guides-v2";

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
            new KnowledgeSeed("customer", "Giải đáp đặt hàng trong nước và từ nước ngoài", "customer-delivery-guide.md"),
            new KnowledgeSeed("customer", "Hướng dẫn ưu đãi, thời gian và điều kiện áp dụng", "customer-promotion-guide.md"),
            new KnowledgeSeed("store-staff", "Cẩm nang thao tác nhân viên cửa hàng", "store-staff-guide.md"),
            new KnowledgeSeed("store-staff", "Hướng dẫn giải thích khuyến mãi cho khách", "store-staff-promotion-guide.md"),
            new KnowledgeSeed("warehouse", "Cẩm nang thao tác nhân viên kho", "warehouse-guide.md"),
            new KnowledgeSeed("warehouse", "Đơn giao xa và câu hỏi về giao quốc tế", "warehouse-delivery-guide.md"),
            new KnowledgeSeed("admin", "Cẩm nang thao tác admin", "admin-guide.md"),
            new KnowledgeSeed("admin", "Hướng dẫn quản trị hệ thống và chuỗi chi nhánh", "admin-operations-guide.md"));

    private final AssistantKnowledgeSourceRepository repository;
    private final SystemSettingRepository settingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AssistantKnowledgeInitializer(AssistantKnowledgeSourceRepository repository,
                                         SystemSettingRepository settingRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.settingRepository = settingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run(String... args) {
        try {
            seedKnowledge();
            synchronizeBundledKnowledgeIfNeeded();
        } catch (Exception e) {
            log.warn("Could not complete initial assistant knowledge seeding: {}", e.getMessage());
        }
    }

    private void seedKnowledge() throws Exception {
        Set<String> roleKeys = new HashSet<>(SEED_FILES.keySet());
        ROLE_GUIDES.forEach(seed -> roleKeys.add(seed.roleKey()));
        Set<String> existingKeys = new HashSet<>();
        Set<String> rolesWithSources = new HashSet<>();
        for (Object[] row : repository.findRoleTitleKeysByRoleKeyIn(roleKeys)) {
            String roleKey = (String) row[0];
            String title = (String) row[1];
            rolesWithSources.add(roleKey);
            existingKeys.add(sourceKey(roleKey, title));
        }

        List<AssistantKnowledgeSource> missing = new ArrayList<>();
        for (var seed : SEED_FILES.entrySet()) {
            if (rolesWithSources.contains(seed.getKey())) continue;
            String content;
            try (var input = new ClassPathResource("assistant-knowledge/" + seed.getValue()).getInputStream()) {
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            missing.add(AssistantKnowledgeSource.builder()
                    .roleKey(seed.getKey())
                    .title("Hướng dẫn khởi đầu")
                    .content(content)
                    .active(true)
                    .build());
            rolesWithSources.add(seed.getKey());
            existingKeys.add(sourceKey(seed.getKey(), "Hướng dẫn khởi đầu"));
        }
        for (KnowledgeSeed seed : ROLE_GUIDES) {
            if (!existingKeys.add(sourceKey(seed.roleKey(), seed.title()))) continue;
            String content;
            try (var input = new ClassPathResource("assistant-knowledge/" + seed.fileName()).getInputStream()) {
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            missing.add(AssistantKnowledgeSource.builder()
                    .roleKey(seed.roleKey())
                    .title(seed.title())
                    .content(content)
                    .active(true)
                    .build());
        }
        if (!missing.isEmpty()) repository.saveAll(missing);
    }

    /**
     * Apply each bundled role guide once per content revision. This updates the seeded
     * PostgreSQL sources already used by RAG without overwriting admin edits on every boot.
     */
    private void synchronizeBundledKnowledgeIfNeeded() throws Exception {
        String appliedRevision = settingRepository.findBySettingKey(REVISION_SETTING_KEY)
                .map(SystemSetting::getSettingValue).orElse(null);
        if (CURRENT_SEED_REVISION.equals(appliedRevision)) return;

        Map<String, AssistantKnowledgeSource> existing = new java.util.HashMap<>();
        for (AssistantKnowledgeSource source : repository.findAll()) {
            existing.putIfAbsent(sourceKey(source.getRoleKey(), source.getTitle()), source);
        }

        for (Map.Entry<String, String> seed : SEED_FILES.entrySet()) {
            synchronizeSource(seed.getKey(), "Hướng dẫn khởi đầu", seed.getValue(), existing);
        }
        for (KnowledgeSeed seed : ROLE_GUIDES) {
            synchronizeSource(seed.roleKey(), seed.title(), seed.fileName(), existing);
        }

        SystemSetting revision = settingRepository.findBySettingKey(REVISION_SETTING_KEY)
                .orElseGet(() -> SystemSetting.builder().settingKey(REVISION_SETTING_KEY)
                        .description("Phiên bản đồng bộ tài liệu RAG mặc định theo role")
                        .build());
        revision.setSettingValue(CURRENT_SEED_REVISION);
        settingRepository.save(revision);
        log.info("Synchronized bundled assistant knowledge revision {}", CURRENT_SEED_REVISION);
    }

    private void synchronizeSource(String roleKey, String title, String fileName,
                                   Map<String, AssistantKnowledgeSource> existing) throws Exception {
        String content;
        try (var input = new ClassPathResource("assistant-knowledge/" + fileName).getInputStream()) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        String hash = AssistantKnowledgeVectorIndexService.contentHash(title, content);
        String key = sourceKey(roleKey, title);
        AssistantKnowledgeSource source = existing.get(key);
        boolean changed = source == null || !content.equals(source.getContent())
                || !hash.equals(source.getContentHash());
        if (!changed) return;

        if (source == null) {
            source = AssistantKnowledgeSource.builder().roleKey(roleKey).title(title).content(content)
                    .active(true).indexStatus("PENDING").indexedChunkCount(0).contentHash(hash).build();
            source = repository.save(source);
            existing.put(key, source);
        } else {
            source.setContent(content);
            source.setContentHash(hash);
            source.setIndexStatus(source.isActive() ? "PENDING" : "DISABLED");
            source.setIndexedChunkCount(0);
            source.setIndexedAt(null);
            source.setIndexError(null);
            source.setEmbeddingModel(null);
            source.setEmbeddingDimensions(null);
            source = repository.save(source);
        }
        eventPublisher.publishEvent(new AssistantKnowledgeChangedEvent(source.getId(), false));
    }

    private String sourceKey(String roleKey, String title) {
        return roleKey + "\u0000" + title;
    }

    private record KnowledgeSeed(String roleKey, String title, String fileName) { }
}

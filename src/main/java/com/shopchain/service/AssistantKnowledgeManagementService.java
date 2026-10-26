package com.shopchain.service;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AssistantKnowledgeManagementService {
    public static final Set<String> ROLE_KEYS = Set.of("customer", "store-staff", "warehouse", "admin");
    private final AssistantKnowledgeSourceRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public AssistantKnowledgeManagementService(AssistantKnowledgeSourceRepository repository,
                                               ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSourceDto> list() {
        return repository.findAllByOrderByRoleKeyAscUpdatedAtDesc().stream()
                .map(AssistantKnowledgeManagementService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSourceDto> listForRole(String roleKey) {
        requireRole(roleKey);
        return repository.findByRoleKeyOrderByUpdatedAtDesc(roleKey).stream()
                .map(AssistantKnowledgeManagementService::toDto).toList();
    }

    @Transactional
    public KnowledgeSourceDto create(KnowledgeSourceRequest request) {
        validate(request);
        AssistantKnowledgeSource source = AssistantKnowledgeSource.builder()
                .roleKey(request.roleKey().trim())
                .title(request.title().trim())
                .content(request.content().trim())
                .active(request.active())
                .contentHash(AssistantKnowledgeVectorIndexService.contentHash(request.title(), request.content()))
                .indexStatus(request.active() ? "PENDING" : "DISABLED")
                .build();
        AssistantKnowledgeSource saved = repository.save(source);
        eventPublisher.publishEvent(new AssistantKnowledgeChangedEvent(saved.getId(), false));
        return toDto(saved);
    }

    @Transactional
    public KnowledgeSourceDto update(Long id, KnowledgeSourceRequest request) {
        validate(request);
        AssistantKnowledgeSource source = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nguồn tri thức."));
        source.setRoleKey(request.roleKey().trim());
        source.setTitle(request.title().trim());
        source.setContent(request.content().trim());
        source.setActive(request.active());
        source.setContentHash(AssistantKnowledgeVectorIndexService.contentHash(request.title(), request.content()));
        resetIndexState(source, request.active());
        AssistantKnowledgeSource saved = repository.save(source);
        eventPublisher.publishEvent(new AssistantKnowledgeChangedEvent(saved.getId(), false));
        return toDto(saved);
    }

    @Transactional
    public KnowledgeSourceDto toggle(Long id) {
        AssistantKnowledgeSource source = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nguồn tri thức."));
        source.setActive(!source.isActive());
        resetIndexState(source, source.isActive());
        AssistantKnowledgeSource saved = repository.save(source);
        eventPublisher.publishEvent(new AssistantKnowledgeChangedEvent(saved.getId(), false));
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        AssistantKnowledgeSource source = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nguồn tri thức."));
        repository.delete(source);
        eventPublisher.publishEvent(new AssistantKnowledgeChangedEvent(id, true));
    }

    private void validate(KnowledgeSourceRequest request) {
        if (request == null) throw new IllegalArgumentException("Dữ liệu nguồn tri thức không hợp lệ.");
        requireRole(request.roleKey());
        if (request.title() == null || request.title().isBlank() || request.title().trim().length() > 180) {
            throw new IllegalArgumentException("Tên nguồn cần có và tối đa 180 ký tự.");
        }
        if (request.content() == null || request.content().isBlank() || request.content().trim().length() > 100_000) {
            throw new IllegalArgumentException("Nội dung cần có và tối đa 100.000 ký tự.");
        }
    }

    private void requireRole(String roleKey) {
        if (roleKey == null || !ROLE_KEYS.contains(roleKey.trim())) {
            throw new IllegalArgumentException("Phạm vi role không hợp lệ.");
        }
    }

    private void resetIndexState(AssistantKnowledgeSource source, boolean active) {
        source.setIndexStatus(active ? "PENDING" : "DISABLED");
        source.setIndexedChunkCount(0);
        source.setIndexedAt(null);
        source.setIndexError(null);
        source.setEmbeddingModel(null);
        source.setEmbeddingDimensions(null);
    }

    private static KnowledgeSourceDto toDto(AssistantKnowledgeSource source) {
        return new KnowledgeSourceDto(source.getId(), source.getRoleKey(), source.getTitle(),
                source.getContent(), source.isActive(), source.getUpdatedAt(),
                source.getIndexStatus() == null ? "PENDING" : source.getIndexStatus(),
                source.getIndexedChunkCount() == null ? 0 : source.getIndexedChunkCount(),
                source.getIndexedAt(), source.getIndexError(), source.getEmbeddingModel(),
                source.getEmbeddingDimensions());
    }

    public record KnowledgeSourceRequest(String roleKey, String title, String content, boolean active) { }
    public record KnowledgeSourceDto(Long id, String roleKey, String title, String content,
                                     boolean active, java.time.LocalDateTime updatedAt,
                                     String indexStatus, int indexedChunkCount,
                                     java.time.LocalDateTime indexedAt, String indexError,
                                     String embeddingModel, Integer embeddingDimensions) { }
}

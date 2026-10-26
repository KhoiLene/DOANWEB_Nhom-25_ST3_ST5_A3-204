package com.shopchain.service;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantKnowledgeVectorIndexService {
    private static final Logger log = LoggerFactory.getLogger(AssistantKnowledgeVectorIndexService.class);
    private static final int CHUNK_CHARS = 1800;
    private static final int CHUNK_OVERLAP_CHARS = 240;

    private final AssistantKnowledgeSourceRepository repository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final GeminiApiClient gemini;
    private final AssistantKnowledgeGraphService graphService;
    private final boolean enabled;

    public AssistantKnowledgeVectorIndexService(AssistantKnowledgeSourceRepository repository,
                                                ObjectProvider<VectorStore> vectorStoreProvider,
                                                GeminiApiClient gemini,
                                                AssistantKnowledgeGraphService graphService,
                                                @Value("${assistant.rag.vector.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.gemini = gemini;
        this.graphService = graphService;
        this.enabled = enabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("knowledgeIndexExecutor")
    public void handleSourceChanged(AssistantKnowledgeChangedEvent event) {
        if (event.deleted()) {
            deleteVectors(event.sourceId());
            return;
        }
        reindex(event.sourceId());
    }

    public ReindexSummary reindexAll() {
        return reindexSources(repository.findAllByOrderByRoleKeyAscUpdatedAtDesc());
    }

    public ReindexSummary reindexPending() {
        return reindexSources(repository.findAllByOrderByRoleKeyAscUpdatedAtDesc().stream()
                .filter(source -> source.isActive() && isStale(source))
                .toList());
    }

    private boolean isStale(AssistantKnowledgeSource source) {
        return !"INDEXED".equals(source.getIndexStatus())
                || !gemini.embeddingModel().equals(source.getEmbeddingModel())
                || !Integer.valueOf(gemini.dimensions()).equals(source.getEmbeddingDimensions());
    }

    private ReindexSummary reindexSources(List<AssistantKnowledgeSource> sources) {
        int indexed = 0;
        int pending = 0;
        int failed = 0;
        int disabled = 0;
        for (AssistantKnowledgeSource source : sources) {
            String result = reindex(source.getId());
            switch (result) {
                case "INDEXED" -> indexed++;
                case "PENDING" -> pending++;
                case "DISABLED" -> disabled++;
                case "FAILED" -> failed++;
                default -> { }
            }
        }
        return new ReindexSummary(sources.size(), indexed, pending, failed, disabled);
    }

    public String reindex(Long sourceId) {
        AssistantKnowledgeSource source = repository.findById(sourceId).orElse(null);
        if (source == null) {
            deleteVectors(sourceId);
            return "DELETED";
        }
        String expectedHash = source.getContentHash();
        if (source.isActive() && (expectedHash == null || expectedHash.isBlank())) {
            expectedHash = contentHash(source.getTitle(), source.getContent());
            source.setContentHash(expectedHash);
            repository.save(source);
        }
        if (!source.isActive()) {
            try {
                deleteSourceVectors(vectorStoreProvider.getIfAvailable(), sourceId);
                updateIndexState(sourceId, expectedHash, "DISABLED", 0, null);
                return "DISABLED";
            } catch (RuntimeException exception) {
                updateIndexState(sourceId, expectedHash, "FAILED", 0, safeMessage(exception));
                log.warn("Could not remove disabled Gemini RAG source {}", sourceId);
                return "FAILED";
            }
        }
        if (!enabled) {
            updateIndexState(sourceId, expectedHash, "PENDING", 0, "Vector RAG đang tắt trong cấu hình.");
            return "PENDING";
        }
        if (!gemini.isConfigured()) {
            updateIndexState(sourceId, expectedHash, "PENDING", 0, "Chưa cấu hình GEMINI_API_KEY.");
            return "PENDING";
        }

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            updateIndexState(sourceId, expectedHash, "PENDING", 0, "Qdrant chưa sẵn sàng.");
            return "PENDING";
        }

        try {
            List<String> chunks = split(source.getTitle(), source.getContent());
            List<Document> documents = new ArrayList<>(chunks.size());
            for (int index = 0; index < chunks.size(); index++) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("sourceId", sourceId.toString());
                metadata.put("sourceTitle", gemini.sanitize(source.getTitle()));
                metadata.put("roleKey", source.getRoleKey());
                metadata.put("active", true);
                metadata.put("chunkIndex", index);
                metadata.put("contentHash", expectedHash);
                metadata.put("embeddingModel", gemini.embeddingModel());
                metadata.put("embeddingDimensions", gemini.dimensions());
                String pointId = UUID.nameUUIDFromBytes((sourceId + ":" + expectedHash + ":" + index)
                        .getBytes(StandardCharsets.UTF_8)).toString();
                documents.add(new Document(pointId, chunks.get(index), metadata));
            }
            if (!documents.isEmpty()) vectorStore.add(documents);
            deleteStaleVectors(vectorStore, sourceId, expectedHash);
            updateIndexState(sourceId, expectedHash, "INDEXED", documents.size(), null);
            return "INDEXED";
        } catch (RuntimeException exception) {
            updateIndexState(sourceId, expectedHash, "FAILED", 0, safeMessage(exception));
            log.warn("Could not index Gemini RAG source {}: {}", sourceId, safeMessage(exception));
            return "FAILED";
        }
    }

    private void deleteStaleVectors(VectorStore vectorStore, Long sourceId, String currentHash) {
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        Filter.Expression staleSource = filters.and(
                filters.eq("sourceId", sourceId.toString()),
                filters.ne("contentHash", currentHash)).build();
        vectorStore.delete(staleSource);
    }

    private void deleteSourceVectors(VectorStore vectorStore, Long sourceId) {
        if (vectorStore == null || sourceId == null) return;
        Filter.Expression filter = new FilterExpressionBuilder().eq("sourceId", sourceId.toString()).build();
        vectorStore.delete(filter);
    }

    public void deleteVectors(Long sourceId) {
        graphService.invalidateCache();
        try {
            deleteSourceVectors(vectorStoreProvider.getIfAvailable(), sourceId);
        } catch (RuntimeException exception) {
            log.warn("Could not remove deleted Gemini RAG source {}: {}", sourceId, safeMessage(exception));
        }
    }

    private void updateIndexState(Long sourceId, String expectedHash, String state, int chunks, String error) {
        AssistantKnowledgeSource current = repository.findById(sourceId).orElse(null);
        if (current == null || !java.util.Objects.equals(expectedHash, current.getContentHash())) return;
        current.setIndexStatus(state);
        current.setIndexedChunkCount(chunks);
        current.setIndexedAt("INDEXED".equals(state) ? LocalDateTime.now() : null);
        current.setIndexError(error);
        current.setEmbeddingModel("INDEXED".equals(state) ? gemini.embeddingModel() : null);
        current.setEmbeddingDimensions("INDEXED".equals(state) ? gemini.dimensions() : null);
        repository.save(current);
        graphService.invalidateCache();
    }

    private List<String> split(String title, String content) {
        String safeTitle = gemini.sanitize(title).trim();
        String normalized = gemini.sanitize(content).replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return List.of("Nguồn: " + safeTitle);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_CHARS);
            if (end < normalized.length()) {
                int boundary = Math.max(normalized.lastIndexOf(' ', end), normalized.lastIndexOf('\n', end));
                if (boundary > start + CHUNK_CHARS / 2) end = boundary;
            }
            String part = normalized.substring(start, end).trim();
            if (!part.isBlank()) chunks.add("Nguồn: " + safeTitle + "\n" + part);
            if (end >= normalized.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP_CHARS);
            while (start < normalized.length() && !Character.isWhitespace(normalized.charAt(start))) start++;
        }
        return chunks;
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof GeminiApiClient.GeminiApiException) return exception.getMessage();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Không thể cập nhật vector index Gemini/Qdrant.";
        return message.length() > 400 ? message.substring(0, 400) : message;
    }

    public static String contentHash(String title, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(((title == null ? "" : title.trim()) + "\n"
                    + (content == null ? "" : content.trim())).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record ReindexSummary(int total, int indexed, int pending, int failed, int disabled) { }
}

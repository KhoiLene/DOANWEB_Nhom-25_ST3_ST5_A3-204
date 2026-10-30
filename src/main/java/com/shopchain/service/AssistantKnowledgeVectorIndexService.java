package com.shopchain.service;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AssistantKnowledgeVectorIndexService {
    private static final Logger log = LoggerFactory.getLogger(AssistantKnowledgeVectorIndexService.class);
    private static final int CHUNK_CHARS = 1800;
    private static final int CHUNK_OVERLAP_CHARS = 240;

    private final AssistantKnowledgeSourceRepository repository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<QdrantClient> qdrantClientProvider;
    private final GeminiApiClient gemini;
    private final AssistantKnowledgeGraphService graphService;
    private final boolean enabled;
    private final String collectionName;
    private final String qdrantHost;
    private final String activeProfiles;
    private volatile boolean collectionInitialized = false;

    @Autowired
    public AssistantKnowledgeVectorIndexService(AssistantKnowledgeSourceRepository repository,
                                                ObjectProvider<VectorStore> vectorStoreProvider,
                                                ObjectProvider<QdrantClient> qdrantClientProvider,
                                                GeminiApiClient gemini,
                                                AssistantKnowledgeGraphService graphService,
                                                @Value("${assistant.rag.vector.enabled:true}") boolean enabled,
                                                @Value("${spring.ai.vectorstore.qdrant.collection-name:${assistant.rag.collection:techtra_knowledge_gemini_embedding_2_768}}") String collectionName,
                                                @Value("${spring.ai.vectorstore.qdrant.host:${QDRANT_HOST:localhost}}") String qdrantHost,
                                                @Value("${spring.profiles.active:}") String activeProfiles) {
        this.repository = repository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.qdrantClientProvider = qdrantClientProvider;
        this.gemini = gemini;
        this.graphService = graphService;
        this.enabled = enabled;
        this.collectionName = collectionName;
        this.qdrantHost = qdrantHost;
        this.activeProfiles = activeProfiles;
    }

    public AssistantKnowledgeVectorIndexService(AssistantKnowledgeSourceRepository repository,
                                                ObjectProvider<VectorStore> vectorStoreProvider,
                                                GeminiApiClient gemini,
                                                AssistantKnowledgeGraphService graphService,
                                                boolean enabled) {
        this(repository, vectorStoreProvider, null, gemini, graphService, enabled,
                "techtra_knowledge_gemini_embedding_2_768", "localhost", "");
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
        List<AssistantKnowledgeSource> activeSources = repository.findAllByOrderByRoleKeyAscUpdatedAtDesc().stream()
                .filter(AssistantKnowledgeSource::isActive)
                .toList();

        // The SQL records can still say INDEXED after a Qdrant cluster has been recreated or cleared.
        // In that case the vector collection is the source of truth: rebuild it from the active SQL corpus.
        if (!activeSources.isEmpty() && Boolean.FALSE.equals(collectionExists())) {
            log.warn("Qdrant collection '{}' is missing; rebuilding {} active Gemini knowledge sources.",
                    collectionName, activeSources.size());
            return reindexSources(activeSources);
        }

        return reindexSources(activeSources.stream().filter(this::isStale).toList());
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
        if (isProduction() && isLocalhost(qdrantHost)) {
            updateIndexState(sourceId, expectedHash, "PENDING", 0, "Chưa cấu hình QDRANT_HOST cho môi trường production.");
            return "PENDING";
        }

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            updateIndexState(sourceId, expectedHash, "PENDING", 0, "Qdrant chưa sẵn sàng.");
            return "PENDING";
        }

        try {
            ensureCollectionExists(vectorStore);
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

    private boolean isProduction() {
        return activeProfiles != null && (activeProfiles.contains("prod") || activeProfiles.contains("production"));
    }

    private boolean isLocalhost(String host) {
        return host == null || host.isBlank() || "localhost".equalsIgnoreCase(host.trim()) || "127.0.0.1".equals(host.trim());
    }

    private synchronized void ensureCollectionExists(VectorStore vectorStore) {
        if (collectionInitialized) return;
        QdrantClient client = qdrantClient(vectorStore);
        if (client == null) return;
        try {
            Boolean exists = client.collectionExistsAsync(collectionName).get(5, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(exists)) {
                Collections.VectorParams params = Collections.VectorParams.newBuilder()
                        .setDistance(Collections.Distance.Cosine)
                        .setSize(gemini.dimensions())
                        .build();
                client.createCollectionAsync(collectionName, params).get(10, TimeUnit.SECONDS);
                log.info("Initialized Qdrant collection '{}' with dimension {}", collectionName, gemini.dimensions());
            }
            collectionInitialized = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking Qdrant collection.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to communicate with Qdrant: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out communicating with Qdrant.", e);
        }
    }

    /** Returns null when Qdrant cannot be checked, so a transient outage won't trigger a costly full re-embed. */
    private Boolean collectionExists() {
        if (!enabled || !gemini.isConfigured() || (isProduction() && isLocalhost(qdrantHost))) return null;
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) return null;
        QdrantClient client = qdrantClient(vectorStore);
        if (client == null) return null;
        try {
            return client.collectionExistsAsync(collectionName).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while checking Qdrant collection availability.");
            return null;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            log.warn("Could not check Qdrant collection availability ({}).", rootCause(exception).getClass().getSimpleName());
            return null;
        }
    }

    private QdrantClient qdrantClient(VectorStore vectorStore) {
        QdrantClient client = qdrantClientProvider != null ? qdrantClientProvider.getIfAvailable() : null;
        if (client == null && vectorStore != null) {
            Optional<QdrantClient> nativeClient = vectorStore.getNativeClient();
            if (nativeClient.isPresent()) client = nativeClient.get();
        }
        return client;
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    public record ReindexSummary(int total, int indexed, int pending, int failed, int disabled) { }
}

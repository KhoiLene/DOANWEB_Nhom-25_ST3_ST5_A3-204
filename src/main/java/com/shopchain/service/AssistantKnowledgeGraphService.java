package com.shopchain.service;

import com.shopchain.entity.AssistantKnowledgeSource;
import com.shopchain.repository.AssistantKnowledgeSourceRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssistantKnowledgeGraphService {
    private static final int MAX_SOURCES_PER_ROLE = 8;

    private final AssistantKnowledgeSourceRepository repository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final GeminiApiClient gemini;
    private final boolean vectorEnabled;
    private final int topK;
    private final double similarityThreshold;
    private final String qdrantHost;
    private final String activeProfiles;
    private volatile CachedEdges cachedEdges;

    public AssistantKnowledgeGraphService(AssistantKnowledgeSourceRepository repository,
                                         ObjectProvider<VectorStore> vectorStoreProvider,
                                         GeminiApiClient gemini,
                                         @Value("${assistant.rag.vector.enabled:true}") boolean vectorEnabled,
                                         @Value("${assistant.rag.top-k:6}") int topK,
                                         @Value("${assistant.rag.similarity-threshold:0.35}") double similarityThreshold,
                                         @Value("${spring.ai.vectorstore.qdrant.host:${QDRANT_HOST:localhost}}") String qdrantHost,
                                         @Value("${spring.profiles.active:}") String activeProfiles) {
        this.repository = repository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.gemini = gemini;
        this.vectorEnabled = vectorEnabled;
        this.topK = Math.max(2, Math.min(topK, 8));
        this.similarityThreshold = Math.max(0, Math.min(similarityThreshold, 1));
        this.qdrantHost = qdrantHost;
        this.activeProfiles = activeProfiles;
    }

    public AssistantKnowledgeGraphService(AssistantKnowledgeSourceRepository repository,
                                         ObjectProvider<VectorStore> vectorStoreProvider,
                                         GeminiApiClient gemini,
                                         boolean vectorEnabled,
                                         int topK,
                                         double similarityThreshold) {
        this(repository, vectorStoreProvider, gemini, vectorEnabled, topK, similarityThreshold, "localhost", "");
    }

    private boolean isProduction() {
        return activeProfiles != null && (activeProfiles.contains("prod") || activeProfiles.contains("production"));
    }

    private boolean isLocalhost(String host) {
        return host == null || host.isBlank() || "localhost".equalsIgnoreCase(host.trim()) || "127.0.0.1".equals(host.trim());
    }

    public KnowledgeGraph graph() {
        List<AssistantKnowledgeSource> sources = repository.findAllByOrderByRoleKeyAscUpdatedAtDesc();
        List<GraphSource> nodes = sources.stream().map(source -> new GraphSource(
                source.getId(), source.getRoleKey(), source.getTitle(), source.isActive(),
                source.getIndexStatus() == null ? "PENDING" : source.getIndexStatus(),
                source.getIndexedChunkCount() == null ? 0 : source.getIndexedChunkCount()))
                .toList();
        List<AssistantKnowledgeSource> indexed = sources.stream()
                .filter(this::isCurrentIndexed)
                .toList();
        int chunks = indexed.stream().mapToInt(source -> source.getIndexedChunkCount() == null ? 0 : source.getIndexedChunkCount()).sum();
        if (!vectorEnabled || !gemini.isConfigured()) {
            return new KnowledgeGraph(false, gemini.embeddingModel(), gemini.dimensions(), indexed.size(), chunks,
                    nodes, List.of(), "Cần cấu hình Gemini API key để dựng các cạnh láng giềng embedding.");
        }
        if (isProduction() && isLocalhost(qdrantHost)) {
            return new KnowledgeGraph(false, gemini.embeddingModel(), gemini.dimensions(), indexed.size(), chunks,
                    nodes, List.of(), "Chưa cấu hình QDRANT_HOST trên production; cạnh embedding sẽ hiện sau khi cấu hình Qdrant.");
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return new KnowledgeGraph(false, gemini.embeddingModel(), gemini.dimensions(), indexed.size(), chunks,
                    nodes, List.of(), "Qdrant chưa sẵn sàng; cạnh embedding sẽ hiện sau khi kết nối lại.");
        }

        Map<Long, AssistantKnowledgeSource> sourceById = new HashMap<>();
        sources.forEach(source -> sourceById.put(source.getId(), source));
        List<GraphEdge> edges;
        try {
            edges = getCachedEdges(indexed, sourceById, vectorStore);
        } catch (RuntimeException exception) {
            return new KnowledgeGraph(false, gemini.embeddingModel(), gemini.dimensions(), indexed.size(), chunks,
                    nodes, List.of(), "Không thể truy vấn Gemini/Qdrant để dựng đồ thị lúc này.");
        }
        return new KnowledgeGraph(true, gemini.embeddingModel(), gemini.dimensions(), indexed.size(), chunks,
                nodes, edges, "Cạnh là láng giềng vector thật trong cùng role; giới hạn 8 nguồn mỗi role. Đồ thị được cache 60 giây để tiết kiệm quota.");
    }

    private synchronized List<GraphEdge> getCachedEdges(List<AssistantKnowledgeSource> indexed,
                                                        Map<Long, AssistantKnowledgeSource> sourceById,
                                                        VectorStore vectorStore) {
        CachedEdges existing = cachedEdges;
        if (existing != null && System.currentTimeMillis() - existing.createdAt() < 60_000) {
            return existing.edges().stream().filter(edge -> isCurrentIndexed(sourceById.get(edge.from()))
                    && isCurrentIndexed(sourceById.get(edge.to()))).toList();
        }
        Set<String> seenEdges = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();
        String currentRole = "";
        int countForRole = 0;
        try {
            for (AssistantKnowledgeSource source : indexed) {
                if (!source.getRoleKey().equals(currentRole)) {
                    currentRole = source.getRoleKey();
                    countForRole = 0;
                }
                if (countForRole++ >= MAX_SOURCES_PER_ROLE) continue;
                String query = gemini.sanitize(source.getTitle() + "\n" + source.getContent());
                if (query.length() > 1400) query = query.substring(0, 1400);
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                Filter.Expression roleFilter = filterBuilder
                        .and(filterBuilder.eq("roleKey", source.getRoleKey()), filterBuilder.eq("active", true))
                        .build();
                List<Document> neighbors = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(roleFilter)
                        .build());
                for (Document neighbor : neighbors) {
                    Object rawId = neighbor.getMetadata().get("sourceId");
                    if (rawId == null) continue;
                    Long targetId;
                    try { targetId = Long.valueOf(rawId.toString()); }
                    catch (NumberFormatException ignored) { continue; }
                    AssistantKnowledgeSource target = sourceById.get(targetId);
                    if (target == null || targetId.equals(source.getId()) || !target.isActive()
                            || !"INDEXED".equals(target.getIndexStatus())
                            || !gemini.embeddingModel().equals(target.getEmbeddingModel())
                            || !Integer.valueOf(gemini.dimensions()).equals(target.getEmbeddingDimensions())
                            || !source.getRoleKey().equals(target.getRoleKey())
                            || !java.util.Objects.equals(target.getContentHash(), neighbor.getMetadata().get("contentHash"))) continue;
                    long from = Math.min(source.getId(), targetId);
                    long to = Math.max(source.getId(), targetId);
                    String edgeKey = source.getRoleKey() + ":" + from + ":" + to;
                    if (seenEdges.add(edgeKey)) edges.add(new GraphEdge(from, to, source.getRoleKey(), "embedding-neighbor"));
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Gemini/Qdrant graph search failed.", exception);
        }
        edges.sort(Comparator.comparing(GraphEdge::roleKey).thenComparingLong(GraphEdge::from).thenComparingLong(GraphEdge::to));
        cachedEdges = new CachedEdges(System.currentTimeMillis(), List.copyOf(edges));
        return List.copyOf(edges);
    }

    public void invalidateCache() { cachedEdges = null; }

    private boolean isCurrentIndexed(AssistantKnowledgeSource source) {
        return source != null && source.isActive() && "INDEXED".equals(source.getIndexStatus())
                && gemini.embeddingModel().equals(source.getEmbeddingModel())
                && Integer.valueOf(gemini.dimensions()).equals(source.getEmbeddingDimensions());
    }

    private record CachedEdges(long createdAt, List<GraphEdge> edges) { }

    public record KnowledgeGraph(boolean vectorAvailable, String embeddingModel, int embeddingDimensions,
                                 int indexedSources, int indexedChunks, List<GraphSource> nodes,
                                 List<GraphEdge> edges, String status) { }
    public record GraphSource(Long id, String roleKey, String title, boolean active,
                              String indexStatus, int indexedChunkCount) { }
    public record GraphEdge(Long from, Long to, String roleKey, String type) { }
}

package com.shopchain.config;

import com.shopchain.service.AssistantKnowledgeVectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssistantKnowledgeVectorBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AssistantKnowledgeVectorBootstrap.class);
    private final AssistantKnowledgeVectorIndexService indexService;

    public AssistantKnowledgeVectorBootstrap(AssistantKnowledgeVectorIndexService indexService) {
        this.indexService = indexService;
    }

    @Async("knowledgeIndexExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void indexPendingSources() {
        report(indexService.reindexPending(), "Gemini knowledge index bootstrap");
    }

    /**
     * Qdrant Cloud can be reachable only after the application has finished starting.
     * Retry the lightweight collection/status check so a cold start timeout does not
     * leave the SQL index flags permanently out of sync with an empty Qdrant cluster.
     */
    @Scheduled(initialDelayString = "${assistant.rag.bootstrap.retry-initial-delay-ms:-1}",
            fixedDelayString = "${assistant.rag.bootstrap.retry-delay-ms:300000}")
    public void retryPendingSources() {
        report(indexService.reindexPending(), "Gemini knowledge index retry");
    }

    private void report(AssistantKnowledgeVectorIndexService.ReindexSummary summary, String label) {
        if (summary.total() > 0) {
            log.info("{}: total={}, indexed={}, pending={}, failed={}", label,
                    summary.total(), summary.indexed(), summary.pending(), summary.failed());
        }
    }
}

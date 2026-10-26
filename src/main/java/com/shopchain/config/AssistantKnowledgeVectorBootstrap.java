package com.shopchain.config;

import com.shopchain.service.AssistantKnowledgeVectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
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
        var summary = indexService.reindexPending();
        if (summary.total() > 0) {
            log.info("Gemini knowledge index bootstrap: total={}, indexed={}, pending={}, failed={}",
                    summary.total(), summary.indexed(), summary.pending(), summary.failed());
        }
    }
}

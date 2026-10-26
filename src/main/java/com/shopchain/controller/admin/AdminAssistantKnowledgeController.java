package com.shopchain.controller.admin;

import com.shopchain.service.AssistantKnowledgeManagementService;
import com.shopchain.service.AssistantKnowledgeManagementService.KnowledgeSourceDto;
import com.shopchain.service.AssistantKnowledgeManagementService.KnowledgeSourceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class AdminAssistantKnowledgeController {
    private final AssistantKnowledgeManagementService knowledgeService;

    public AdminAssistantKnowledgeController(AssistantKnowledgeManagementService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/admin/assistant-knowledge")
    public String page(Model model) {
        model.addAttribute("title", "Kho tri thức RAG");
        model.addAttribute("activePage", "assistant-knowledge");
        model.addAttribute("content", "admin/assistant-knowledge");
        return "admin/layout";
    }

    @RestController
    @RequestMapping("/api/admin/assistant-knowledge")
    public static class KnowledgeApi {
        private final AssistantKnowledgeManagementService knowledgeService;

        public KnowledgeApi(AssistantKnowledgeManagementService knowledgeService) {
            this.knowledgeService = knowledgeService;
        }

        @GetMapping
        public java.util.List<KnowledgeSourceDto> list() { return knowledgeService.list(); }

        @PostMapping
        public ResponseEntity<?> create(@RequestBody(required = false) KnowledgeSourceRequest request) {
            try {
                return ResponseEntity.status(HttpStatus.CREATED).body(knowledgeService.create(request));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
            }
        }

        @PutMapping("/{id}")
        public ResponseEntity<?> update(@PathVariable Long id,
                                        @RequestBody(required = false) KnowledgeSourceRequest request) {
            try {
                return ResponseEntity.ok(knowledgeService.update(id, request));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
            }
        }

        @PatchMapping("/{id}/toggle")
        public ResponseEntity<?> toggle(@PathVariable Long id) {
            try {
                return ResponseEntity.ok(knowledgeService.toggle(id));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
            }
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<?> delete(@PathVariable Long id) {
            try {
                knowledgeService.delete(id);
                return ResponseEntity.noContent().build();
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
            }
        }
    }
}

package com.shopchain.repository;

import com.shopchain.entity.AssistantKnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistantKnowledgeSourceRepository extends JpaRepository<AssistantKnowledgeSource, Long> {
    List<AssistantKnowledgeSource> findAllByOrderByRoleKeyAscUpdatedAtDesc();
    List<AssistantKnowledgeSource> findByRoleKeyOrderByUpdatedAtDesc(String roleKey);
    List<AssistantKnowledgeSource> findByRoleKeyAndActiveTrueOrderByUpdatedAtDesc(String roleKey);
    long countByRoleKey(String roleKey);
    long countByRoleKeyAndActiveTrue(String roleKey);
    long countByRoleKeyAndActiveTrueAndIndexStatus(String roleKey, String indexStatus);
}

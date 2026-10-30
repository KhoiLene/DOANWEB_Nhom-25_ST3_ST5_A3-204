package com.shopchain.repository;

import com.shopchain.entity.AssistantKnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AssistantKnowledgeSourceRepository extends JpaRepository<AssistantKnowledgeSource, Long> {
    List<AssistantKnowledgeSource> findAllByOrderByRoleKeyAscUpdatedAtDesc();
    List<AssistantKnowledgeSource> findByRoleKeyOrderByUpdatedAtDesc(String roleKey);
    List<AssistantKnowledgeSource> findByRoleKeyAndActiveTrueOrderByUpdatedAtDesc(String roleKey);
    long countByRoleKey(String roleKey);
    long countByRoleKeyAndActiveTrue(String roleKey);
    long countByRoleKeyAndActiveTrueAndIndexStatus(String roleKey, String indexStatus);
    boolean existsByRoleKeyAndTitle(String roleKey, String title);
    @Query("select source.roleKey, source.title from AssistantKnowledgeSource source where source.roleKey in :roleKeys")
    List<Object[]> findRoleTitleKeysByRoleKeyIn(@Param("roleKeys") Collection<String> roleKeys);
}

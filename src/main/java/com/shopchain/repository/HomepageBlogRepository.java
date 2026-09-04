package com.shopchain.repository;

import com.shopchain.entity.HomepageBlog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface HomepageBlogRepository extends JpaRepository<HomepageBlog, UUID> {
    List<HomepageBlog> findByEnabledTrueOrderBySortOrderAsc();
}

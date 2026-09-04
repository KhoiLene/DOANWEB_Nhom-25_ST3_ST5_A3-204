package com.shopchain.repository;

import com.shopchain.entity.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NewsCategoryRepository extends JpaRepository<NewsCategory, Long> {
    List<NewsCategory> findByIsActiveTrueOrderBySortOrderAsc();
}

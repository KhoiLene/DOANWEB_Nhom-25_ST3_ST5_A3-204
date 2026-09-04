package com.shopchain.repository;

import com.shopchain.entity.HomepagePromoBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface HomepagePromoBannerRepository extends JpaRepository<HomepagePromoBanner, UUID> {
    List<HomepagePromoBanner> findByEnabledTrueOrderBySortOrderAsc();
}

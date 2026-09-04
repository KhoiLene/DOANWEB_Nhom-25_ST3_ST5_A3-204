package com.shopchain.repository;

import com.shopchain.entity.HomepageValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface HomepageValueRepository extends JpaRepository<HomepageValue, UUID> {
    List<HomepageValue> findByEnabledTrueOrderBySortOrderAsc();
}

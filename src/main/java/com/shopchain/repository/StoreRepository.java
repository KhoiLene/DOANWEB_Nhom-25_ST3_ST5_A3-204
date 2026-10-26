package com.shopchain.repository;

import com.shopchain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
    java.util.List<Store> findByActiveTrueAndPickupEnabledTrue();
    java.util.List<Store> findByActiveTrueAndPickupEnabledTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();
}

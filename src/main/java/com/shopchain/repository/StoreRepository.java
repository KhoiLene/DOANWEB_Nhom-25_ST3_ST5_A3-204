package com.shopchain.repository;

import com.shopchain.entity.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select store from Store store where store.id = :id")
    Optional<Store> findForUpdateById(@Param("id") Long id);

    java.util.List<Store> findByActiveTrueAndPickupEnabledTrue();
    java.util.List<Store> findByActiveTrueAndPickupEnabledTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();
}

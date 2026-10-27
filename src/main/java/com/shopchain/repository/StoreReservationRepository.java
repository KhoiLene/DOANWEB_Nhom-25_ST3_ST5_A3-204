package com.shopchain.repository;

import com.shopchain.entity.StoreReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoreReservationRepository extends JpaRepository<StoreReservation, Long> {
    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    List<StoreReservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "store", "items", "items.product"})
    List<StoreReservation> findByStoreIdAndStatusOrderByCreatedAtAsc(Long storeId, String status);

    @EntityGraph(attributePaths = {"user", "store", "items", "items.product"})
    @Query("select r from StoreReservation r where r.status = 'HELD' and r.expiresAt <= :now")
    List<StoreReservation> findExpired(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StoreReservation r where r.id = :id")
    Optional<StoreReservation> findForUpdateById(@Param("id") Long id);
}

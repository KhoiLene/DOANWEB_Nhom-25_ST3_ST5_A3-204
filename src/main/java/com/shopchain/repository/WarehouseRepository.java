package com.shopchain.repository;

import com.shopchain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    java.util.List<Warehouse> findByActiveTrue();
}

package com.shopchain.repository;

import com.shopchain.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {
    Optional<SalesInvoice> findByOrderId(Long orderId);
    List<SalesInvoice> findByOrderStoreIdOrderByIssuedAtDesc(Long storeId);
    List<SalesInvoice> findByOrderUserIdOrderByIssuedAtDesc(Long userId);
}

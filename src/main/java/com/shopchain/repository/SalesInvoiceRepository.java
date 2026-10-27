package com.shopchain.repository;

import com.shopchain.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {
    Optional<SalesInvoice> findByOrderId(Long orderId);
    List<SalesInvoice> findByOrderStoreIdOrderByIssuedAtDesc(Long storeId);
    List<SalesInvoice> findByOrderUserIdOrderByIssuedAtDesc(Long userId);

    @Query("select distinct invoice from SalesInvoice invoice left join fetch invoice.lines " +
            "where invoice.order.store.id = :storeId and invoice.customerPhone in :phones " +
            "order by invoice.issuedAt desc")
    List<SalesInvoice> findRecentForStoreAndPhones(@Param("storeId") Long storeId, @Param("phones") List<String> phones);
}

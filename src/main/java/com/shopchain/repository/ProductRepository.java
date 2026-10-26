package com.shopchain.repository;

import com.shopchain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p WHERE (p.isActive = true OR p.isActive IS NULL) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:saleOnly = false OR p.isFlashSale = true)")
    Page<Product> searchActiveProducts(@Param("keyword") String keyword,
                                       @Param("categoryId") Long categoryId,
                                       @Param("saleOnly") boolean saleOnly,
                                       Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.name LIKE %:name% ")
    Page<Product> findByCategoryAndNameContaining(@Param("categoryId") Long categoryId, @Param("name") String name, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    List<Product> findByIsFlashSaleTrueAndIsActiveTrue();

    List<Product> findByIsFeaturedTrueAndIsActiveTrue();
}

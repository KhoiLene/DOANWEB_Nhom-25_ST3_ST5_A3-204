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
            "AND (:saleOnly = false OR (p.discountPercent > 0 AND p.discountPercent < 100 " +
            "AND (p.campaignStartAt IS NULL OR p.campaignStartAt <= CURRENT_TIMESTAMP) " +
            "AND (p.campaignEndAt IS NULL OR p.campaignEndAt >= CURRENT_TIMESTAMP)))")
    Page<Product> searchActiveProducts(@Param("keyword") String keyword,
                                       @Param("categoryId") Long categoryId,
                                       @Param("saleOnly") boolean saleOnly,
                                       Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (p.isActive = true OR p.isActive IS NULL) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:saleOnly = false OR (p.discountPercent > 0 AND p.discountPercent < 100 " +
            "AND (p.campaignStartAt IS NULL OR p.campaignStartAt <= CURRENT_TIMESTAMP) " +
            "AND (p.campaignEndAt IS NULL OR p.campaignEndAt >= CURRENT_TIMESTAMP))) " +
            "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
            "AND (:storeId IS NULL OR EXISTS (SELECT inventory.id FROM ProductInventory inventory " +
            "WHERE inventory.product = p AND inventory.store.id = :storeId AND inventory.stockQuantity > 0))")
    Page<Product> searchFilteredActiveProducts(@Param("keyword") String keyword,
                                               @Param("categoryId") Long categoryId,
                                               @Param("saleOnly") boolean saleOnly,
                                               @Param("minPrice") Double minPrice,
                                               @Param("maxPrice") Double maxPrice,
                                               @Param("storeId") Long storeId,
                                               Pageable pageable);

    @Query(value = "SELECT p.* FROM products p " +
            "LEFT JOIN (SELECT oi.product_id, SUM(oi.quantity) AS units_sold " +
            "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
            "WHERE o.status NOT IN ('CANCELLED', 'RETURNED') GROUP BY oi.product_id) sales " +
            "ON sales.product_id = p.id " +
            "WHERE (p.is_active = true OR p.is_active IS NULL) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(COALESCE(p.name, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS TEXT)), '%') OR " +
            "LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS TEXT)), '%') OR " +
            "LOWER(COALESCE(p.sku, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS TEXT)), '%')) " +
            "AND (:saleOnly = false OR (p.discount_percent > 0 AND p.discount_percent < 100 " +
            "AND (p.campaign_start_at IS NULL OR p.campaign_start_at <= CURRENT_TIMESTAMP) " +
            "AND (p.campaign_end_at IS NULL OR p.campaign_end_at >= CURRENT_TIMESTAMP))) " +
            "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
            "AND (:storeId IS NULL OR EXISTS (SELECT 1 FROM product_inventory inventory " +
            "WHERE inventory.product_id = p.id AND inventory.store_id = :storeId AND inventory.stock_quantity > 0)) " +
            "ORDER BY COALESCE(sales.units_sold, 0) DESC, p.id DESC",
            countQuery = "SELECT COUNT(*) FROM products p WHERE (p.is_active = true OR p.is_active IS NULL) " +
                    "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
                    "AND (:keyword IS NULL OR :keyword = '' OR " +
                    "LOWER(COALESCE(p.name, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS TEXT)), '%') OR " +
                    "LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS TEXT)), '%') OR " +
                    "LOWER(COALESCE(p.sku, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS TEXT)), '%')) " +
                    "AND (:saleOnly = false OR (p.discount_percent > 0 AND p.discount_percent < 100 " +
                    "AND (p.campaign_start_at IS NULL OR p.campaign_start_at <= CURRENT_TIMESTAMP) " +
                    "AND (p.campaign_end_at IS NULL OR p.campaign_end_at >= CURRENT_TIMESTAMP))) " +
                    "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
                    "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
                    "AND (:storeId IS NULL OR EXISTS (SELECT 1 FROM product_inventory inventory " +
                    "WHERE inventory.product_id = p.id AND inventory.store_id = :storeId AND inventory.stock_quantity > 0))",
            nativeQuery = true)
    Page<Product> searchBestSellingActiveProducts(@Param("keyword") String keyword,
                                                  @Param("categoryId") Long categoryId,
                                                  @Param("saleOnly") boolean saleOnly,
                                                  @Param("minPrice") Double minPrice,
                                                  @Param("maxPrice") Double maxPrice,
                                                  @Param("storeId") Long storeId,
                                                  Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.name LIKE %:name% ")
    Page<Product> findByCategoryAndNameContaining(@Param("categoryId") Long categoryId, @Param("name") String name, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.discountPercent > 0 AND p.discountPercent < 100 " +
            "AND (p.campaignStartAt IS NULL OR p.campaignStartAt <= CURRENT_TIMESTAMP) " +
            "AND (p.campaignEndAt IS NULL OR p.campaignEndAt >= CURRENT_TIMESTAMP)")
    List<Product> findByIsFlashSaleTrueAndIsActiveTrue();

    List<Product> findByIsFeaturedTrueAndIsActiveTrue();
}

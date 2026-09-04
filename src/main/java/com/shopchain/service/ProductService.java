package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.dto.ProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface ProductService {
    Product createProductForStore(ProductRequest dto, MultipartFile imageFile, Long storeId) throws IOException;
    String uploadImage(MultipartFile file) throws IOException;
    Page<Product> searchProducts(String keyword, Long categoryId, Pageable pageable);
    Product getProductDetail(Long id);
    void deleteProduct(Long id);
    List<Product> getFlashSaleProducts();
    List<Product> getFeaturedProducts();
    List<Product> getRelatedProducts(Long productId);
}

package com.shopchain.service.impl;

import com.cloudinary.Cloudinary;
import com.shopchain.dto.ProductRequest;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {

    private final Cloudinary cloudinary;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(Cloudinary cloudinary, ProductRepository productRepository,
                               ProductInventoryRepository inventoryRepository, StoreRepository storeRepository,
                               CategoryRepository categoryRepository) {
        this.cloudinary = cloudinary;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.storeRepository = storeRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public String uploadImage(MultipartFile file) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
            com.cloudinary.utils.ObjectUtils.asMap("folder", "shop_products"));
        return uploadResult.get("url").toString();
    }

    @Override
    @Transactional
    public Product createProductForStore(ProductRequest dto, MultipartFile imageFile, Long storeId) throws IOException {
        // 1. Upload image to Cloudinary
        String imageUrl = uploadImage(imageFile);

        // 2. Find or create Category
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // 3. Save general Product info
        Product product = Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .imageUrl(imageUrl)
                .price(dto.getPrice())
                .category(category)
                .build();
        product = productRepository.save(product);

        // 4. Save Store-specific Inventory
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        ProductInventory inventory = ProductInventory.builder()
                .product(product)
                .store(store)
                .price(dto.getPrice())
                .stockQuantity(dto.getQuantity())
                .build();
        inventoryRepository.save(inventory);

        return product;
    }

    @Override
    public Page<Product> searchProducts(String keyword, Long categoryId, Pageable pageable) {
        if (keyword != null && categoryId != null) {
            return productRepository.findByCategoryAndNameContaining(categoryId, keyword, pageable);
        } else if (keyword != null) {
            return productRepository.findByNameContainingIgnoreCase(keyword, pageable);
        } else if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable);
        }
        return productRepository.findAll(pageable);
    }

    @Override
    public Product getProductDetail(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Override
    public List<Product> getFlashSaleProducts() {
        return productRepository.findByIsFlashSaleTrueAndIsActiveTrue();
    }

    @Override
    public List<Product> getFeaturedProducts() {
        return productRepository.findByIsFeaturedTrueAndIsActiveTrue();
    }

    @Override
    public List<Product> getRelatedProducts(Long productId) {
        Product product = getProductDetail(productId);
        return productRepository.findByCategoryId(product.getCategory().getId(), PageRequest.of(0, 4))
                .stream()
                .filter(p -> !p.getId().equals(productId))
                .toList();
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductDetail(id);
        // Cloudinary image delete logic can be added here
        productRepository.delete(product);
    }
}

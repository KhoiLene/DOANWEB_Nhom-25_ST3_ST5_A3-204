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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn ảnh sản phẩm.");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Ảnh sản phẩm tối đa 5 MB.");
        byte[] content = file.getBytes();
        if (!isSupportedImage(content)) throw new IllegalArgumentException("Tệp tải lên phải là ảnh JPEG, PNG, WebP hoặc GIF hợp lệ.");

        Map uploadResult = cloudinary.uploader().upload(content,
                com.cloudinary.utils.ObjectUtils.asMap("folder", "shop_products", "resource_type", "image",
                        "unique_filename", true, "overwrite", false, "secure", true));
        Object secureUrl = uploadResult.get("secure_url");
        if (secureUrl == null || !secureUrl.toString().startsWith("https://")) {
            throw new IOException("Cloudinary không trả về địa chỉ ảnh HTTPS hợp lệ.");
        }
        return secureUrl.toString();
    }

    private boolean isSupportedImage(byte[] content) {
        if (content == null || content.length < 12) return false;
        boolean png = content.length >= 8 && Arrays.equals(Arrays.copyOf(content, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        boolean jpeg = (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
        boolean gif = content.length >= 6 && ("GIF87a".equals(new String(content, 0, 6, StandardCharsets.US_ASCII))
                || "GIF89a".equals(new String(content, 0, 6, StandardCharsets.US_ASCII)));
        boolean webp = "RIFF".equals(new String(content, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(content, 8, 4, StandardCharsets.US_ASCII));
        return png || jpeg || gif || webp;
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
        return searchProducts(keyword, categoryId, false, pageable);
    }

    @Override
    public Page<Product> searchProducts(String keyword, Long categoryId, boolean saleOnly, Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return productRepository.searchActiveProducts(normalizedKeyword, categoryId, saleOnly, pageable);
    }

    @Override
    public Page<Product> searchFilteredProducts(String keyword, Long categoryId, boolean saleOnly,
                                                Double minPrice, Double maxPrice, Long storeId, Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return productRepository.searchFilteredActiveProducts(normalizedKeyword, categoryId, saleOnly,
                minPrice, maxPrice, storeId, pageable);
    }

    @Override
    public Page<Product> searchBestSellingProducts(String keyword, Long categoryId, boolean saleOnly,
                                                   Double minPrice, Double maxPrice, Long storeId, Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return productRepository.searchBestSellingActiveProducts(normalizedKeyword, categoryId, saleOnly,
                minPrice, maxPrice, storeId, pageable);
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
                .filter(related -> !Boolean.FALSE.equals(related.getIsActive()))
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

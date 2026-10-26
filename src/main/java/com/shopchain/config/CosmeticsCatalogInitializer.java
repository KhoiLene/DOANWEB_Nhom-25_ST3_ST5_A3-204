package com.shopchain.config;

import com.shopchain.entity.Category;
import com.shopchain.entity.HomepageValue;
import com.shopchain.entity.HomepagePromoBanner;
import com.shopchain.entity.HomepageBlog;
import com.shopchain.entity.Product;
import com.shopchain.repository.CategoryRepository;
import com.shopchain.repository.HomepageValueRepository;
import com.shopchain.repository.HomepagePromoBannerRepository;
import com.shopchain.repository.HomepageBlogRepository;
import com.shopchain.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(10)
public class CosmeticsCatalogInitializer implements CommandLineRunner {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final HomepageValueRepository homepageValueRepository;
    private final HomepagePromoBannerRepository homepagePromoBannerRepository;
    private final HomepageBlogRepository homepageBlogRepository;

    public CosmeticsCatalogInitializer(CategoryRepository categoryRepository,
                                       ProductRepository productRepository,
                                       HomepageValueRepository homepageValueRepository,
                                       HomepagePromoBannerRepository homepagePromoBannerRepository,
                                       HomepageBlogRepository homepageBlogRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.homepageValueRepository = homepageValueRepository;
        this.homepagePromoBannerRepository = homepagePromoBannerRepository;
        this.homepageBlogRepository = homepageBlogRepository;
    }

    @Override
    public void run(String... args) {
        Map<String, Category> categories = new HashMap<>();
        Map<String, Category> existingCategories = new HashMap<>();
        categoryRepository.findAll().forEach(category -> existingCategories.put(
                category.getName().toLowerCase(Locale.ROOT), category));
        for (CategorySeed seed : categorySeeds()) {
            Category category = existingCategories.get(seed.name().toLowerCase(Locale.ROOT));
            if (category == null) {
                category = new Category();
            }
            if (category.getId() == null) {
                category.setName(seed.name());
                category.setDescription(seed.description());
                category.setImageUrl(seed.imageUrl());
            } else if (category.getImageUrl() == null || category.getImageUrl().isBlank()) {
                category.setImageUrl(seed.imageUrl());
            }
            categories.put(seed.name(), categoryRepository.save(category));
        }

        Map<String, Product> existingProducts = new HashMap<>();
        productRepository.findAll().forEach(product -> existingProducts.put(
                product.getName().toLowerCase(Locale.ROOT), product));
        int productIndex = 1;
        for (ProductSeed seed : productSeeds()) {
            String key = seed.name().toLowerCase(Locale.ROOT);
            Product product = existingProducts.get(key);
            if (product == null) {
                product = Product.builder()
                        .name(seed.name())
                        .description(seed.description())
                        .imageUrl(seed.imageUrl())
                        .price(seed.price())
                        .isActive(true)
                        .isFeatured(seed.featured())
                        .isFlashSale(seed.flashSale())
                        .category(categories.get(seed.category()))
                        .build();
            }
            if (product.getSku() == null || product.getSku().isBlank()) {
                product.setSku("TECH-" + String.format("%04d", productIndex));
            }
            productRepository.save(product);
            productIndex++;
        }

        if (homepageValueRepository.count() == 0) {
            homepageValueRepository.saveAll(List.of(
                    HomepageValue.builder().icon("fa-solid fa-leaf").title("Thành phần chọn lọc")
                            .description("Thông tin thành phần rõ ràng, ưu tiên công thức dịu nhẹ cho chăm sóc hằng ngày.")
                            .sortOrder(1).enabled(true).build(),
                    HomepageValue.builder().icon("fa-solid fa-heart").title("Chăm sóc theo nhu cầu")
                            .description("Từ dưỡng ẩm, chống nắng đến chăm sóc tóc và cơ thể.")
                            .sortOrder(2).enabled(true).build(),
                    HomepageValue.builder().icon("fa-solid fa-box").title("Đóng gói cẩn thận")
                            .description("Đơn hàng được chuẩn bị kỹ trước khi giao đến bạn.")
                            .sortOrder(3).enabled(true).build()
            ));
        }

        if (homepagePromoBannerRepository.count() == 0) {
            homepagePromoBannerRepository.saveAll(List.of(
                    HomepagePromoBanner.builder().position("left").tag("CHĂM SÓC DA")
                            .title("Bắt đầu với chu trình dưỡng da dịu nhẹ")
                            .imageUrl("https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=1200&q=85")
                            .ctaText("Xem sản phẩm").ctaLink("/products?categoryId=1").sortOrder(1).enabled(true).build(),
                    HomepagePromoBanner.builder().position("right").tag("LÀM ĐẸP MỖI NGÀY")
                            .title("Tìm món trang điểm hợp phong cách của bạn")
                            .imageUrl("https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=85")
                            .ctaText("Khám phá ngay").ctaLink("/products?categoryId=3").sortOrder(2).enabled(true).build()
            ));
        }

        if (homepageBlogRepository.count() == 0) {
            homepageBlogRepository.saveAll(List.of(
                    HomepageBlog.builder().title("Các bước chăm sóc da cơ bản cho người mới bắt đầu")
                            .description("Làm sạch, dưỡng ẩm và chống nắng: cách xây dựng chu trình gọn nhẹ theo nhu cầu của bạn.")
                            .imageUrl("https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1000&q=85")
                            .author("Góc làm đẹp Techtra").sortOrder(1).enabled(true).build(),
                    HomepageBlog.builder().title("Cách chọn kem chống nắng cho thói quen hằng ngày")
                            .description("Những điểm nên xem trên nhãn sản phẩm và cách sử dụng kem chống nắng trong ngày.")
                            .imageUrl("https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=1000&q=85")
                            .author("Góc làm đẹp Techtra").sortOrder(2).enabled(true).build(),
                    HomepageBlog.builder().title("Chăm sóc tóc đơn giản sau mỗi lần gội")
                            .description("Một vài bước chăm sóc cơ bản giúp tóc dễ chải và mềm mại hơn sau khi gội.")
                            .imageUrl("https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=1000&q=85")
                            .author("Góc làm đẹp Techtra").sortOrder(3).enabled(true).build()
            ));
        }
    }

    private List<CategorySeed> categorySeeds() {
        return List.of(
                new CategorySeed("Chăm sóc da", "Làm sạch, dưỡng ẩm và chăm sóc da mỗi ngày.",
                        "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85"),
                new CategorySeed("Chống nắng", "Bảo vệ làn da trước tác động của ánh nắng.",
                        "https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=900&q=85"),
                new CategorySeed("Trang điểm", "Các món trang điểm cơ bản cho vẻ ngoài tự nhiên.",
                        "https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=900&q=85"),
                new CategorySeed("Chăm sóc tóc & cơ thể", "Sản phẩm làm sạch và dưỡng tóc, da cơ thể.",
                        "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85")
        );
    }

    private List<ProductSeed> productSeeds() {
        return List.of(
                new ProductSeed("Sữa rửa mặt trà xanh dịu nhẹ", "Làm sạch bụi bẩn và dầu thừa, phù hợp cho chu trình chăm sóc da hằng ngày.", 189000, "Chăm sóc da", true, true, "https://images.unsplash.com/photo-1556229010-6c3f2c9ca5f8?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Nước cân bằng hoa cúc", "Bước cân bằng nhẹ nhàng sau khi rửa mặt, giúp da mềm và dễ chịu.", 219000, "Chăm sóc da", true, false, "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Serum cấp ẩm Hyaluronic", "Tinh chất cấp ẩm cho làn da khô căng, dùng trước kem dưỡng.", 329000, "Chăm sóc da", true, true, "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Kem dưỡng phục hồi rau má", "Kem dưỡng dùng buổi sáng hoặc tối, hỗ trợ làm dịu cảm giác khô ráp.", 359000, "Chăm sóc da", true, false, "https://images.unsplash.com/photo-1570194065650-d99fb4bedf0a?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Mặt nạ đất sét thanh lọc", "Mặt nạ rửa giúp làm sạch sâu, dùng định kỳ trong tuần.", 249000, "Chăm sóc da", false, false, "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Kem chống nắng phổ rộng SPF 50", "Kết cấu dễ tán cho bước bảo vệ da ban ngày. Thoa lại theo hướng dẫn sử dụng.", 299000, "Chống nắng", true, true, "https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Son dưỡng môi bơ hạt mỡ", "Son dưỡng môi dùng hằng ngày, tiện mang theo.", 129000, "Trang điểm", true, false, "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Son tint màu hồng đất", "Màu son dễ dùng cho phong cách trang điểm nhẹ nhàng.", 239000, "Trang điểm", true, true, "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Phấn má hồng đào", "Phấn má mịn, tán nhẹ để tạo sắc hồng tự nhiên.", 269000, "Trang điểm", false, false, "https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Dầu gội bưởi dịu nhẹ", "Làm sạch tóc và da đầu, hương bưởi tươi mát.", 259000, "Chăm sóc tóc & cơ thể", true, false, "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Dầu xả dưỡng tóc mềm mượt", "Dầu xả giúp tóc dễ chải và mềm mại sau khi gội.", 249000, "Chăm sóc tóc & cơ thể", false, false, "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Sữa dưỡng thể hương hoa nhẹ", "Dưỡng ẩm cơ thể với mùi hương nhẹ nhàng dùng mỗi ngày.", 279000, "Chăm sóc tóc & cơ thể", true, false, "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85")
        );
    }

    private record CategorySeed(String name, String description, String imageUrl) { }

    private record ProductSeed(String name, String description, double price, String category,
                               boolean featured, boolean flashSale, String imageUrl) { }
}

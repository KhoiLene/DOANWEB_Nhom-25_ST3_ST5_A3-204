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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(CosmeticsCatalogInitializer.class);

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
        try {
            seedCatalog();
        } catch (Exception e) {
            log.warn("Could not complete initial cosmetics catalog seeding: {}", e.getMessage());
        }
    }

    private void seedCatalog() {
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
                        .discountPercent(seed.flashSale() ? 10d : null)
                        .campaignName(seed.flashSale() ? "Flash Sale Techtra" : null)
                        .campaignAudience("ALL")
                        .brandName("Techtra Beauty (sản phẩm minh họa)")
                        .suitableFor(suitableFor(seed.category()))
                        .usageInstructions(usageFor(seed.name(), seed.category()))
                        .category(categories.get(seed.category()))
                        .build();
            }
            // Keep legacy/demo catalog entries consistent with the Flash Sale flag while
            // preserving any campaign values that an administrator has already set.
            if (Boolean.TRUE.equals(product.getIsFlashSale())) {
                if (product.getDiscountPercent() == null) product.setDiscountPercent(10d);
                if (product.getCampaignName() == null || product.getCampaignName().isBlank())
                    product.setCampaignName("Flash Sale Techtra");
                if (product.getCampaignAudience() == null || product.getCampaignAudience().isBlank())
                    product.setCampaignAudience("ALL");
            }
            if (product.getSku() == null || product.getSku().isBlank()) {
                product.setSku("TECH-" + String.format("%04d", productIndex));
            }
            if (product.getBrandName() == null || product.getBrandName().isBlank())
                product.setBrandName("Techtra Beauty (sản phẩm minh họa)");
            if (product.getSuitableFor() == null || product.getSuitableFor().isBlank())
                product.setSuitableFor(suitableFor(seed.category()));
            if (product.getUsageInstructions() == null || product.getUsageInstructions().isBlank())
                product.setUsageInstructions(usageFor(seed.name(), seed.category()));
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
                        "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85"),
                new CategorySeed("Chăm sóc tóc", "Dầu gội, dầu xả và sản phẩm dưỡng tóc theo nhu cầu.",
                        "https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?auto=format&fit=crop&w=900&q=85"),
                new CategorySeed("Chăm sóc cơ thể", "Sản phẩm làm sạch và dưỡng ẩm cơ thể hằng ngày.",
                        "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85"),
                new CategorySeed("Dưỡng môi", "Son dưỡng và sản phẩm chăm sóc môi cơ bản.",
                        "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=900&q=85")
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
                new ProductSeed("Sữa dưỡng thể hương hoa nhẹ", "Dưỡng ẩm cơ thể với mùi hương nhẹ nhàng dùng mỗi ngày.", 279000, "Chăm sóc tóc & cơ thể", true, false, "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Gel dưỡng ẩm trà xanh", "Gel dưỡng ẩm mỏng nhẹ cho chu trình chăm sóc da hằng ngày. Thoa theo hướng dẫn trên nhãn.", 289000, "Chăm sóc da", true, false, "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Nước tẩy trang dịu nhẹ", "Sản phẩm làm sạch lớp trang điểm và bụi bẩn; làm theo hướng dẫn của nhà sản xuất.", 229000, "Chăm sóc da", true, false, "https://images.unsplash.com/photo-1556229010-6c3f2c9ca5f8?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Kem dưỡng ẩm ban đêm", "Kem dưỡng dùng buổi tối để bổ sung bước dưỡng ẩm sau khi làm sạch.", 389000, "Chăm sóc da", true, false, "https://images.unsplash.com/photo-1570194065650-d99fb4bedf0a?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Sữa rửa mặt tạo bọt dịu nhẹ", "Sữa rửa mặt dùng theo nhu cầu làm sạch hằng ngày; tránh chà xát mạnh.", 199000, "Chăm sóc da", false, false, "https://images.unsplash.com/photo-1556229010-6c3f2c9ca5f8?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Mặt nạ giấy cấp ẩm", "Mặt nạ giấy là bước chăm sóc bổ sung, sử dụng theo thời gian hướng dẫn trên bao bì.", 59000, "Chăm sóc da", false, true, "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Son dưỡng môi không màu", "Son dưỡng không màu dùng khi môi khô; thoa lại theo nhu cầu và hướng dẫn trên nhãn.", 99000, "Dưỡng môi", true, false, "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Mặt nạ ngủ môi", "Sản phẩm chăm sóc môi dùng buổi tối theo hướng dẫn trên nhãn.", 179000, "Dưỡng môi", false, false, "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Son dưỡng có màu hồng đào", "Son dưỡng có màu nhẹ cho nhu cầu chăm sóc và trang điểm môi hằng ngày.", 149000, "Dưỡng môi", true, false, "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Dầu gội làm sạch dịu nhẹ", "Dầu gội cơ bản cho bước làm sạch tóc và da đầu; điều chỉnh tần suất theo nhu cầu.", 239000, "Chăm sóc tóc", true, false, "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Mặt nạ ủ tóc mềm mượt", "Sản phẩm dưỡng tóc bổ sung sau khi gội; dùng đúng lượng và thời gian ghi trên nhãn.", 299000, "Chăm sóc tóc", false, false, "https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Tinh dầu dưỡng tóc", "Sản phẩm dưỡng tóc dùng ở thân hoặc ngọn tóc theo hướng dẫn của nhà sản xuất.", 319000, "Chăm sóc tóc", true, false, "https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Sữa tắm hương cam dịu nhẹ", "Sản phẩm làm sạch cơ thể dùng khi tắm; đọc kỹ hướng dẫn và lưu ý trên nhãn.", 189000, "Chăm sóc cơ thể", true, false, "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Kem dưỡng da tay", "Kem dưỡng dùng cho da tay khi có cảm giác khô; thoa lại theo nhu cầu.", 119000, "Chăm sóc cơ thể", false, false, "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=900&q=85"),
                new ProductSeed("Tẩy tế bào chết cơ thể", "Sản phẩm chăm sóc cơ thể bổ sung; dùng nhẹ nhàng và theo đúng tần suất trên nhãn.", 219000, "Chăm sóc cơ thể", false, false, "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=900&q=85")
        );
    }

    private String suitableFor(String category) {
        return switch (category) {
            case "Chăm sóc tóc", "Chăm sóc tóc & cơ thể" -> "Chăm sóc tóc hoặc cơ thể hằng ngày; chọn theo nhu cầu và nhãn sản phẩm.";
            case "Dưỡng môi" -> "Chăm sóc môi hằng ngày.";
            case "Chống nắng" -> "Bước bảo vệ da ban ngày.";
            case "Trang điểm" -> "Trang điểm cá nhân; chọn màu và chất liệu phù hợp.";
            default -> "Chăm sóc da hằng ngày; thử ở vùng da nhỏ nếu da nhạy cảm.";
        };
    }

    private String usageFor(String name, String category) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("sữa rửa mặt")) return "Làm ướt da, dùng lượng vừa đủ, mát-xa nhẹ rồi rửa sạch. Đọc hướng dẫn trên bao bì.";
        if (lower.contains("tẩy trang")) return "Dùng ở bước làm sạch đầu tiên theo hướng dẫn trên nhãn, sau đó tiếp tục bước rửa mặt.";
        if (lower.contains("chống nắng")) return "Thoa ở bước cuối của chu trình buổi sáng; thoa lại theo hướng dẫn trên nhãn.";
        if (lower.contains("serum") || lower.contains("tinh chất")) return "Dùng sau bước làm sạch và trước kem dưỡng; bắt đầu với lượng nhỏ.";
        if (lower.contains("mặt nạ")) return "Sử dụng theo đúng thời gian và tần suất in trên bao bì; không để quá lâu.";
        if (lower.contains("dầu gội")) return "Làm ướt tóc, thoa lên da đầu, mát-xa nhẹ rồi xả sạch.";
        if (lower.contains("dầu xả") || lower.contains("ủ tóc")) return "Thoa lên thân và ngọn tóc sau khi gội, tránh da đầu nếu nhãn không hướng dẫn khác; xả sạch.";
        if (lower.contains("dưỡng tóc")) return "Dùng lượng nhỏ ở thân hoặc ngọn tóc theo hướng dẫn trên bao bì.";
        if (lower.contains("son") || category.equals("Dưỡng môi")) return "Thoa lượng vừa đủ lên môi; dùng lại theo nhu cầu và hướng dẫn trên nhãn.";
        if (lower.contains("kem dưỡng") || lower.contains("gel dưỡng") || lower.contains("sữa dưỡng")) return "Dùng sau bước làm sạch, thoa lớp mỏng và điều chỉnh theo nhu cầu.";
        return "Sử dụng theo hướng dẫn trên nhãn sản phẩm; thử ở vùng nhỏ nếu da nhạy cảm.";
    }

    private record CategorySeed(String name, String description, String imageUrl) { }

    private record ProductSeed(String name, String description, double price, String category,
                               boolean featured, boolean flashSale, String imageUrl) { }
}

package com.shopchain.config;

import com.shopchain.entity.NewsCategory;
import com.shopchain.entity.Post;
import com.shopchain.repository.NewsCategoryRepository;
import com.shopchain.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seeds original demo beauty guides and community-style stories without overwriting admin content. */
@Component
@Order(15)
public class BeautyArticleInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BeautyArticleInitializer.class);

    private final NewsCategoryRepository categoryRepository;
    private final PostRepository postRepository;

    public BeautyArticleInitializer(NewsCategoryRepository categoryRepository, PostRepository postRepository) {
        this.categoryRepository = categoryRepository;
        this.postRepository = postRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            seedArticles();
        } catch (Exception e) {
            log.warn("Could not complete beauty articles seeding: {}", e.getMessage());
        }
    }

    private void seedArticles() {
        Map<String, NewsCategory> categories = new HashMap<>();
        categoryRepository.findAll().forEach(category -> categories.put(category.getSlug(), category));
        for (CategorySeed seed : categorySeeds()) {
            NewsCategory category = categories.get(seed.slug());
            if (category == null) {
                category = NewsCategory.builder().name(seed.name()).slug(seed.slug()).description(seed.description())
                        .icon(seed.icon()).sortOrder(seed.order()).isActive(true)
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
                category = categoryRepository.save(category);
                categories.put(seed.slug(), category);
            }
        }

        List<ArticleSeed> seeds = articleSeeds();
        Set<String> existingSlugs = new HashSet<>(postRepository.findSlugsIn(
                seeds.stream().map(ArticleSeed::slug).toList()));
        List<Post> missingPosts = new ArrayList<>();
        for (ArticleSeed seed : seeds) {
            if (!existingSlugs.add(seed.slug())) continue;
            LocalDateTime now = LocalDateTime.now();
            missingPosts.add(Post.builder().title(seed.title()).slug(seed.slug()).summary(seed.summary())
                    .content(seed.content()).thumbnail(seed.thumbnail()).siteName("Góc làm đẹp Techtra")
                    .category(categories.get(seed.category())).status("published").postType("article")
                    .publishedAt(now.minusDays(seed.daysAgo())).createdAt(now).updatedAt(now).build());
        }
        if (!missingPosts.isEmpty()) postRepository.saveAll(missingPosts);
    }

    private List<CategorySeed> categorySeeds() {
        return List.of(
                new CategorySeed("cham-da", "Chăm sóc da", "Hướng dẫn chăm sóc da cơ bản và dễ áp dụng.", "fa-solid fa-spa", 1),
                new CategorySeed("cham-toc", "Chăm sóc tóc", "Thói quen chăm sóc tóc và da đầu.", "fa-solid fa-wind", 2),
                new CategorySeed("cham-moi", "Chăm sóc môi", "Gợi ý chăm sóc môi hằng ngày.", "fa-solid fa-heart", 3),
                new CategorySeed("chuyen-lam-dep", "Chuyện làm đẹp", "Kinh nghiệm và chuyện vui trong cộng đồng.", "fa-solid fa-comments", 4)
        );
    }

    private List<ArticleSeed> articleSeeds() {
        return List.of(
                new ArticleSeed("chu-trinh-cham-da-dau-tien", "Chu trình chăm da đầu tiên: bắt đầu ít bước thôi",
                        "Nếu mới làm quen với chăm sóc da, một chu trình gọn giúp bạn dễ theo dõi phản ứng của da hơn.", "cham-da", 1,
                        "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=85",
                        "<p>Chăm da không cần bắt đầu bằng thật nhiều sản phẩm. Trước tiên, hãy tạo thói quen đơn giản và chọn món phù hợp với cảm giác của da.</p><h2>Buổi sáng</h2><ul><li>Làm sạch nhẹ nhàng; nếu da không nhiều dầu, có thể rửa bằng nước theo cảm nhận của bạn.</li><li>Dưỡng ẩm nếu da có cảm giác khô hoặc căng.</li><li>Dùng kem chống nắng theo hướng dẫn trên nhãn khi ra ngoài ban ngày.</li></ul><h2>Buổi tối</h2><ul><li>Làm sạch bụi bẩn và lớp chống nắng, nhất là sau một ngày hoạt động.</li><li>Dưỡng ẩm để da dễ chịu.</li></ul><p>Thêm từng sản phẩm một và thử trước trên một vùng da nhỏ. Nếu da nóng rát, sưng hoặc kích ứng kéo dài, hãy ngưng dùng và hỏi chuyên gia y tế.</p>"),
                new ArticleSeed("doc-nhan-kem-chong-nang", "Đọc nhãn kem chống nắng mà không cần nhớ quá nhiều thuật ngữ",
                        "Một vài thông tin trên bao bì giúp bạn chọn được sản phẩm dễ dùng trong lịch sinh hoạt của mình.", "cham-da", 3,
                        "https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=1200&q=85",
                        "<p>Kem chống nắng tốt nhất là loại phù hợp nhu cầu và bạn có thể dùng đủ, đều đặn. Khi chọn, hãy xem chỉ số bảo vệ, hướng dẫn sử dụng, hạn dùng và cảm giác trên da.</p><ul><li>Chọn sản phẩm có chỉ số chống nắng rõ ràng trên nhãn.</li><li>Thoa theo lượng và tần suất mà nhà sản xuất hướng dẫn.</li><li>Thoa lại khi ở ngoài lâu, đổ mồ hôi hoặc sau khi bơi theo chỉ dẫn trên bao bì.</li><li>Kết hợp mũ, áo và bóng râm; kem chống nắng không thay thế các biện pháp che chắn.</li></ul><p>Nếu da nhạy cảm, đọc kỹ thành phần và thử lượng nhỏ trước khi dùng toàn mặt.</p>"),
                new ArticleSeed("chon-duong-am-theo-cam-giac-da", "Chọn kem dưỡng theo cảm giác da, không cần tự gắn nhãn phức tạp",
                        "Da có thể thay đổi theo thời tiết và sinh hoạt. Hãy quan sát cảm giác sau khi rửa mặt để chọn kết cấu phù hợp.", "cham-da", 5,
                        "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=1200&q=85",
                        "<p>Sau khi rửa mặt, hãy để ý da có căng, khô, bóng dầu hay dễ khó chịu không. Đây là manh mối thực tế để chọn sản phẩm dưỡng ẩm.</p><ul><li>Da hay căng khô: thử kết cấu kem mềm, thoa lượng vừa đủ.</li><li>Da dễ bóng: có thể thích gel hoặc lotion nhẹ.</li><li>Da nhạy cảm: ưu tiên công thức đơn giản, tránh đổi nhiều sản phẩm cùng lúc.</li></ul><p>Đây chỉ là cách quan sát để chọn mỹ phẩm, không thay thế chẩn đoán da liễu.</p>"),
                new ArticleSeed("goi-dau-va-xa-toc-dung-cach", "Gội đầu và dùng dầu xả: vài thói quen nhỏ dễ làm",
                        "Làm sạch da đầu và dưỡng phần thân tóc theo cách phù hợp giúp quy trình gội đầu nhẹ nhàng hơn.", "cham-toc", 7,
                        "https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?auto=format&fit=crop&w=1200&q=85",
                        "<p>Khi gội, tập trung làm sạch da đầu bằng đầu ngón tay thay vì cào bằng móng. Lượng dầu gội và tần suất gội có thể thay đổi theo hoạt động, thời tiết và da đầu của mỗi người.</p><ol><li>Làm ướt tóc kỹ trước khi dùng dầu gội.</li><li>Mát-xa nhẹ da đầu rồi xả sạch.</li><li>Thoa dầu xả chủ yếu lên thân và ngọn tóc nếu hướng dẫn sản phẩm phù hợp.</li><li>Gỡ rối nhẹ nhàng khi tóc còn ẩm; tránh chà xát mạnh bằng khăn.</li></ol><p>Nếu da đầu ngứa hoặc bong tróc kéo dài, nên tìm tư vấn y tế.</p>"),
                new ArticleSeed("routine-toc-kho-xo", "Tóc khô xơ sau khi tạo kiểu: bắt đầu với những điều cơ bản",
                        "Giảm nhiệt, chải nhẹ và dùng sản phẩm dưỡng phù hợp là những bước đơn giản để chăm tóc dễ tổn thương.", "cham-toc", 9,
                        "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=1200&q=85",
                        "<p>Tóc có thể khô xơ sau khi thường xuyên dùng nhiệt, nhuộm hoặc tiếp xúc với nắng. Hãy thử điều chỉnh thói quen trước khi mua nhiều sản phẩm.</p><ul><li>Giảm nhiệt độ máy và hạn chế số lần tạo kiểu.</li><li>Dùng sản phẩm bảo vệ nhiệt theo đúng hướng dẫn nếu bạn cần sấy hoặc tạo kiểu.</li><li>Thoa một lượng nhỏ dầu dưỡng lên thân tóc, tránh bôi quá nhiều ở chân tóc.</li><li>Tỉa phần chẻ ngọn khi cần; sản phẩm dưỡng không thể nối lại sợi tóc đã chẻ.</li></ul>"),
                new ArticleSeed("cham-moi-qua-dem", "Chăm môi đơn giản khi ngồi điều hòa cả ngày",
                        "Một thỏi dưỡng môi dễ mang theo có thể giúp bạn nhớ chăm môi trong ngày bận rộn.", "cham-moi", 11,
                        "https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=1200&q=85",
                        "<p>Môi khô có thể do thời tiết, điều hòa hoặc thói quen liếm môi. Hãy chọn sản phẩm dưỡng có nhãn và hướng dẫn rõ ràng.</p><ul><li>Thoa một lớp mỏng khi môi có cảm giác khô.</li><li>Mang theo sản phẩm nếu thường xuyên ở phòng máy lạnh.</li><li>Ban ngày ngoài trời, cân nhắc sản phẩm dưỡng môi có chống nắng và làm theo hướng dẫn.</li><li>Tránh bóc lớp da môi bằng tay.</li></ul><p>Nếu môi nứt đau hoặc sưng kéo dài, nên hỏi chuyên gia y tế.</p>"),
                new ArticleSeed("chuyen-di-cho-kem-duong", "Chuyện vui: đi mua một món, về nhà với cả túi đồ dưỡng da",
                        "Một câu chuyện quen thuộc của hội bắt đầu chăm da: thấy gì cũng muốn thử, rồi quên mất mục tiêu ban đầu.", "chuyen-lam-dep", 13,
                        "https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=85",
                        "<p>“Mình chỉ định mua kem dưỡng thôi”, bạn Linh nói trước khi bước vào cửa hàng. Mười phút sau, giỏ hàng đã có thêm mặt nạ, serum và một món đang được giảm giá.</p><p>Về nhà, Linh quyết định xem lại nhu cầu: da đang thiếu ẩm, sản phẩm hiện tại còn gần đầy, vậy món cần dùng trước mắt vẫn là kem dưỡng. Những món còn lại được lưu vào danh sách để tìm hiểu sau.</p><p>Mẹo rút ra: viết một nhu cầu trước khi mua, kiểm tra sản phẩm đang có và chỉ thêm từng món để biết món nào hợp với mình. Còn bạn, lần gần nhất “đi mua một món” của bạn kết thúc thế nào?</p>"),
                new ArticleSeed("mot-san-pham-moi-moi-lan", "Thử một sản phẩm mới mỗi lần: cách đỡ rối cho người mới",
                        "Thêm quá nhiều món trong cùng một tuần sẽ khiến bạn khó biết sản phẩm nào hợp hoặc gây khó chịu.", "chuyen-lam-dep", 15,
                        "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=1200&q=85",
                        "<p>Khi bắt đầu một sản phẩm mới, hãy đọc hướng dẫn và thử trên vùng da nhỏ nếu phù hợp. Duy trì các bước quen thuộc trong thời gian đầu giúp bạn quan sát dễ hơn.</p><ul><li>Ghi lại ngày bắt đầu và cách dùng.</li><li>Không tự ý tăng lượng hoặc dùng dày hơn hướng dẫn.</li><li>Ngưng dùng nếu có phản ứng bất thường rõ rệt.</li><li>Kiên nhẫn: nhiều sản phẩm chăm sóc cần thời gian, nhưng không có món nào phù hợp với tất cả mọi người.</li></ul><p>Chia sẻ trải nghiệm cá nhân hữu ích, nhưng không nên xem đó là lời khuyên điều trị.</p>")
        );
    }

    private record CategorySeed(String slug, String name, String description, String icon, int order) { }
    private record ArticleSeed(String slug, String title, String summary, String category, int daysAgo,
                               String thumbnail, String content) { }
}

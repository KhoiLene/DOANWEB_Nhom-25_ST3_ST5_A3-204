package com.shopchain.service;

import com.shopchain.entity.BeautyProfile;
import com.shopchain.entity.Product;
import com.shopchain.entity.User;
import com.shopchain.repository.BeautyProfileRepository;
import com.shopchain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class BeautyProfileService {
    private final BeautyProfileRepository profileRepository;
    private final ProductRepository productRepository;

    public BeautyProfileService(BeautyProfileRepository profileRepository, ProductRepository productRepository) {
        this.profileRepository = profileRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public BeautyProfileView get(Long userId) {
        return profileRepository.findByUserId(userId).map(this::toView)
                .orElseGet(() -> new BeautyProfileView(null, null, null, null, null, null, false));
    }

    @Transactional
    public BeautyProfileView save(User user, ProfileUpdate input) {
        if (input == null) throw new IllegalArgumentException("Thông tin hồ sơ chưa hợp lệ.");
        if (input.maxBudget() != null && (!Double.isFinite(input.maxBudget()) || input.maxBudget() < 0 || input.maxBudget() > 100_000_000))
            throw new IllegalArgumentException("Ngân sách phải từ 0 đến 100.000.000 đồng.");
        BeautyProfile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> BeautyProfile.builder().user(user).build());
        profile.setSkinType(clean(input.skinType(), 40));
        profile.setHairType(clean(input.hairType(), 60));
        profile.setSkinConcerns(clean(input.skinConcerns(), 300));
        profile.setHairConcerns(clean(input.hairConcerns(), 300));
        profile.setSensitivities(clean(input.sensitivities(), 300));
        profile.setMaxBudget(input.maxBudget());
        profile.setAssistantConsent(input.assistantConsent());
        return toView(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public String assistantContext(Long userId) {
        return profileRepository.findByUserId(userId)
                .filter(BeautyProfile::isAssistantConsent)
                .map(profile -> {
                    StringBuilder context = new StringBuilder("HỒ SƠ LÀM ĐẸP DO KHÁCH TỰ KHAI (chỉ dùng để cá nhân hóa, không phải chẩn đoán):");
                    append(context, "Loại da", profile.getSkinType());
                    append(context, "Loại tóc", profile.getHairType());
                    append(context, "Mối quan tâm về da", profile.getSkinConcerns());
                    append(context, "Mối quan tâm về tóc", profile.getHairConcerns());
                    append(context, "Thành phần/điều khách muốn tránh", profile.getSensitivities());
                    if (profile.getMaxBudget() != null) context.append("\nNgân sách tham khảo: ").append(Math.round(profile.getMaxBudget())).append(" đồng.");
                    return context.toString();
                }).orElse("");
    }

    @Transactional(readOnly = true)
    public CareRoutine routine(Long userId) {
        BeautyProfileView profile = get(userId);
        List<Product> catalog = productRepository.findAll().stream()
                .filter(product -> product.getIsActive() == null || product.getIsActive())
                .filter(product -> profile.maxBudget() == null || product.getPrice() == null || product.getPrice() <= profile.maxBudget())
                .toList();
        return new CareRoutine(
                "Gợi ý dựa trên hồ sơ và sản phẩm đang bán; kiểm tra nhãn, thành phần và cách dùng trước khi mua.",
                choose(catalog, profile, profile.skinConcerns(), "sữa rửa mặt", "làm sạch", "cleanser"),
                choose(catalog, profile, profile.skinConcerns(), "dưỡng ẩm", "kem dưỡng", "gel dưỡng", "moisturizer"),
                choose(catalog, profile, profile.skinConcerns(), "chống nắng", "sunscreen"),
                choose(catalog, profile, profile.skinConcerns(), "serum", "tinh chất"),
                choose(catalog, profile, profile.hairConcerns(), "dầu gội", "shampoo"),
                choose(catalog, profile, profile.hairConcerns(), "dầu xả", "ủ tóc", "conditioner"),
                choose(catalog, profile, null, "son dưỡng", "dưỡng môi", "mặt nạ ngủ môi"));
    }

    private RoutineProduct choose(List<Product> products, BeautyProfileView profile, String concern, String... terms) {
        return products.stream()
                .filter(product -> containsAny(product, terms))
                .sorted(Comparator.comparingInt((Product product) -> routineScore(product, profile, concern)).reversed()
                        .thenComparing(Product::getIsFeatured, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .map(product -> new RoutineProduct(product.getId(), product.getName(), product.getImageUrl(), product.getPrice(),
                        product.getCategory() == null ? "Sản phẩm" : product.getCategory().getName(),
                        product.getUsageInstructions() == null || product.getUsageInstructions().isBlank()
                                ? "Xem hướng dẫn trên nhãn sản phẩm." : product.getUsageInstructions()))
                .orElse(null);
    }

    private int routineScore(Product product, BeautyProfileView profile, String concern) {
        String details = normalize(String.join(" ", safe(product.getName()), safe(product.getDescription()), safe(product.getSuitableFor())));
        int score = 0;
        if (concern != null) {
            for (String token : normalize(concern).split("\\s+"))
                if (token.length() > 2 && details.contains(token)) score += 3;
        }
        if (profile.sensitivities() != null && product.getIngredients() != null) {
            for (String token : normalize(profile.sensitivities()).split("[,;\\s]+"))
                if (token.length() > 2 && normalize(product.getIngredients()).contains(token)) score -= 20;
        }
        return score;
    }

    private boolean containsAny(Product product, String... terms) {
        String text = normalize(String.join(" ", safe(product.getName()), safe(product.getDescription()),
                safe(product.getCategory() == null ? null : product.getCategory().getName())));
        for (String term : terms) if (text.contains(normalize(term))) return true;
        return false;
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd');
    }

    private void append(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) builder.append("\n").append(label).append(": ").append(value);
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        if (result.length() > maxLength) throw new IllegalArgumentException("Một trường hồ sơ dài quá " + maxLength + " ký tự.");
        return result;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private BeautyProfileView toView(BeautyProfile profile) {
        return new BeautyProfileView(profile.getSkinType(), profile.getHairType(), profile.getSkinConcerns(),
                profile.getHairConcerns(), profile.getSensitivities(), profile.getMaxBudget(), profile.isAssistantConsent());
    }

    public record ProfileUpdate(String skinType, String hairType, String skinConcerns, String hairConcerns,
                                String sensitivities, Double maxBudget, boolean assistantConsent) { }
    public record BeautyProfileView(String skinType, String hairType, String skinConcerns, String hairConcerns,
                                    String sensitivities, Double maxBudget, boolean assistantConsent) { }
    public record RoutineProduct(Long id, String name, String imageUrl, Double price, String category, String usage) { }
    public record CareRoutine(String introduction, RoutineProduct cleanser, RoutineProduct moisturizer,
                              RoutineProduct sunscreen, RoutineProduct serum, RoutineProduct shampoo,
                              RoutineProduct conditioner, RoutineProduct lipCare) { }
}

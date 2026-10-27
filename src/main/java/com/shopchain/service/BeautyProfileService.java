package com.shopchain.service;

import com.shopchain.entity.BeautyProfile;
import com.shopchain.entity.BeautyRoutineLog;
import com.shopchain.entity.Product;
import com.shopchain.entity.User;
import com.shopchain.repository.BeautyProfileRepository;
import com.shopchain.repository.BeautyRoutineLogRepository;
import com.shopchain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class BeautyProfileService {
    private final BeautyProfileRepository profileRepository;
    private final ProductRepository productRepository;
    private final BeautyRoutineLogRepository routineLogRepository;

    private static final ZoneId CUSTOMER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public BeautyProfileService(BeautyProfileRepository profileRepository, ProductRepository productRepository,
                                BeautyRoutineLogRepository routineLogRepository) {
        this.profileRepository = profileRepository;
        this.productRepository = productRepository;
        this.routineLogRepository = routineLogRepository;
    }

    @Transactional(readOnly = true)
    public BeautyProfileView get(Long userId) {
        return profileRepository.findByUserId(userId).map(this::toView)
                .orElseGet(() -> new BeautyProfileView(null, null, null, null, null, null, false, false, false));
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
        profile.setShareBeautyProfileWithStore(input.shareBeautyProfileWithStore());
        profile.setSharePurchaseHistoryWithStore(input.sharePurchaseHistoryWithStore());
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

    @Transactional(readOnly = true)
    public CareJourney journey(Long userId) {
        LocalDate today = LocalDate.now(CUSTOMER_ZONE);
        CareRoutine routine = routine(userId);
        Map<String, Boolean> completed = routineLogRepository.findAllByUserIdAndCareDate(userId, today).stream()
                .collect(java.util.stream.Collectors.toMap(BeautyRoutineLog::getStepKey, BeautyRoutineLog::isCompleted));
        List<CareStep> steps = new ArrayList<>();
        addStep(steps, completed, "morning_cleanse", "Buổi sáng", "Làm sạch nhẹ", routine.cleanser());
        addStep(steps, completed, "morning_moisturize", "Buổi sáng", "Dưỡng ẩm", routine.moisturizer());
        addStep(steps, completed, "morning_sunscreen", "Buổi sáng", "Chống nắng", routine.sunscreen());
        addStep(steps, completed, "evening_cleanse", "Buổi tối", "Làm sạch", routine.cleanser());
        addStep(steps, completed, "evening_serum", "Buổi tối", "Dưỡng bổ sung", routine.serum());
        addStep(steps, completed, "evening_moisturize", "Buổi tối", "Dưỡng ẩm", routine.moisturizer());
        addStep(steps, completed, "hair_shampoo", "Chăm sóc thêm · theo tần suất riêng", "Dầu gội", routine.shampoo(), false);
        addStep(steps, completed, "hair_conditioner", "Chăm sóc thêm · theo tần suất riêng", "Dầu xả", routine.conditioner(), false);
        addStep(steps, completed, "lip_care", "Chăm sóc thêm · theo tần suất riêng", "Dưỡng môi", routine.lipCare(), false);
        long done = steps.stream().filter(CareStep::daily).filter(CareStep::completed).count();
        int total = (int) steps.stream().filter(CareStep::daily).count();
        return new CareJourney(today, steps, (int) done, total, completionStreak(userId, today), routine.introduction());
    }

    @Transactional
    public CareJourney updateStep(User user, StepUpdate update) {
        if (update == null || update.stepKey() == null || update.stepKey().isBlank())
            throw new IllegalArgumentException("Hãy chọn một bước trong lộ trình.");
        Map<String, Consumer<CareRoutine>> allowed = Map.of(
                "morning_cleanse", r -> requireProduct(r.cleanser()),
                "morning_moisturize", r -> requireProduct(r.moisturizer()),
                "morning_sunscreen", r -> requireProduct(r.sunscreen()),
                "evening_cleanse", r -> requireProduct(r.cleanser()),
                "evening_serum", r -> requireProduct(r.serum()),
                "evening_moisturize", r -> requireProduct(r.moisturizer()),
                "hair_shampoo", r -> requireProduct(r.shampoo()),
                "hair_conditioner", r -> requireProduct(r.conditioner()),
                "lip_care", r -> requireProduct(r.lipCare()));
        Consumer<CareRoutine> stepValidator = allowed.get(update.stepKey());
        if (stepValidator == null) throw new IllegalArgumentException("Bước chăm sóc không hợp lệ.");
        try {
            stepValidator.accept(routine(user.getId()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("Bước này chưa có sản phẩm gợi ý trong lộ trình của bạn.");
        }
        LocalDate today = LocalDate.now(CUSTOMER_ZONE);
        BeautyRoutineLog log = routineLogRepository.findByUserIdAndCareDateAndStepKey(user.getId(), today, update.stepKey())
                .orElseGet(() -> BeautyRoutineLog.builder().user(user).careDate(today).stepKey(update.stepKey()).build());
        log.setCompleted(update.completed());
        routineLogRepository.save(log);
        return journey(user.getId());
    }

    private void addStep(List<CareStep> steps, Map<String, Boolean> completed, String key, String period, String label,
                         RoutineProduct product) {
        addStep(steps, completed, key, period, label, product, true);
    }

    private void addStep(List<CareStep> steps, Map<String, Boolean> completed, String key, String period, String label,
                         RoutineProduct product, boolean daily) {
        if (product != null) steps.add(new CareStep(key, period, label, product, completed.getOrDefault(key, false), daily));
    }

    private int completionStreak(Long userId, LocalDate today) {
        Set<LocalDate> activeDates = new java.util.HashSet<>(routineLogRepository.findCompletedDates(userId, today));
        LocalDate streakDate = activeDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (activeDates.contains(streakDate)) {
            streak++;
            streakDate = streakDate.minusDays(1);
        }
        return streak;
    }

    private void requireProduct(RoutineProduct product) {
        if (product == null) throw new IllegalStateException("No product recommendation");
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
                profile.getHairConcerns(), profile.getSensitivities(), profile.getMaxBudget(), profile.isAssistantConsent(),
                profile.isShareBeautyProfileWithStore(), profile.isSharePurchaseHistoryWithStore());
    }

    public record ProfileUpdate(String skinType, String hairType, String skinConcerns, String hairConcerns,
                                String sensitivities, Double maxBudget, boolean assistantConsent,
                                boolean shareBeautyProfileWithStore, boolean sharePurchaseHistoryWithStore) { }
    public record BeautyProfileView(String skinType, String hairType, String skinConcerns, String hairConcerns,
                                    String sensitivities, Double maxBudget, boolean assistantConsent,
                                    boolean shareBeautyProfileWithStore, boolean sharePurchaseHistoryWithStore) { }
    public record RoutineProduct(Long id, String name, String imageUrl, Double price, String category, String usage) { }
    public record CareRoutine(String introduction, RoutineProduct cleanser, RoutineProduct moisturizer,
                              RoutineProduct sunscreen, RoutineProduct serum, RoutineProduct shampoo,
                              RoutineProduct conditioner, RoutineProduct lipCare) { }
    public record StepUpdate(String stepKey, boolean completed) { }
    public record CareStep(String key, String period, String label, RoutineProduct product, boolean completed, boolean daily) { }
    public record CareJourney(LocalDate date, List<CareStep> steps, int completedSteps, int totalSteps,
                              int streakDays, String introduction) { }
}

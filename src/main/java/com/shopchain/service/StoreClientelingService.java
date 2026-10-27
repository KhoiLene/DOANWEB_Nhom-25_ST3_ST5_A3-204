package com.shopchain.service;

import com.shopchain.entity.BeautyProfile;
import com.shopchain.entity.ProductInventory;
import com.shopchain.entity.SalesInvoice;
import com.shopchain.entity.SalesInvoiceLine;
import com.shopchain.entity.User;
import com.shopchain.repository.BeautyProfileRepository;
import com.shopchain.repository.ProductInventoryRepository;
import com.shopchain.repository.SalesInvoiceRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StoreClientelingService {
    private final UserRepository userRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final BeautyProfileRepository beautyProfileRepository;
    private final ProductInventoryRepository inventoryRepository;

    public StoreClientelingService(UserRepository userRepository, SalesInvoiceRepository invoiceRepository,
                                   BeautyProfileRepository beautyProfileRepository,
                                   ProductInventoryRepository inventoryRepository) {
        this.userRepository = userRepository;
        this.invoiceRepository = invoiceRepository;
        this.beautyProfileRepository = beautyProfileRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public MemberCard lookup(String staffUsername, String phone) {
        String normalizedPhone = normalizePhone(phone);
        User staff = userRepository.findByUsername(staffUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản nhân viên."));
        if (staff.getStore() == null || !staff.getStore().isActive())
            throw new IllegalArgumentException("Tài khoản nhân viên chưa được gán chi nhánh đang hoạt động.");

        java.util.Map<Long, User> phoneMatches = new java.util.LinkedHashMap<>();
        userRepository.findByPhone(phone.trim()).forEach(user -> phoneMatches.put(user.getId(), user));
        userRepository.findByPhone(normalizedPhone).forEach(user -> phoneMatches.put(user.getId(), user));
        List<User> matches = phoneMatches.values().stream()
                .filter(User::isEnabled)
                .filter(user -> user.getRole() != null && "ROLE_USER".equals(user.getRole().getName()))
                .toList();
        if (matches.size() != 1) return null;
        User customer = matches.get(0);
        int points = customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints();
        String tier = points >= 1500 ? "Kim cương" : points >= 500 ? "Vàng" : "Thành viên";
        BeautyProfile profile = beautyProfileRepository.findByUserId(customer.getId()).orElse(null);

        SharedPreferences preferences = profile != null && profile.isShareBeautyProfileWithStore()
                ? new SharedPreferences(profile.getSkinType(), profile.getHairType(), profile.getSkinConcerns(), profile.getHairConcerns())
                : null;
        List<RecentPurchase> purchases = profile != null && profile.isSharePurchaseHistoryWithStore()
                ? recentPurchases(customer, staff.getStore().getId(), normalizedPhone) : List.of();
        return new MemberCard(customer.getFullName(), maskPhone(normalizedPhone), points, tier, preferences, purchases);
    }

    private List<RecentPurchase> recentPurchases(User customer, Long storeId, String searchedPhone) {
        List<RecentPurchase> purchases = new ArrayList<>();
        java.util.Set<String> phones = new java.util.LinkedHashSet<>();
        phones.add(searchedPhone);
        phones.add(normalizePhone(customer.getPhone()));
        if (customer.getPhone() != null && !customer.getPhone().isBlank()) phones.add(customer.getPhone().trim());
        phones.removeIf(value -> value == null || value.isBlank());
        java.util.Set<Long> seenProducts = new java.util.HashSet<>();
        for (SalesInvoice invoice : invoiceRepository.findRecentForStoreAndPhones(storeId, List.copyOf(phones))) {
            if (!"ISSUED".equalsIgnoreCase(invoice.getStatus())) continue;
            for (SalesInvoiceLine line : invoice.getLines()) {
                if (line.getProductId() == null || !seenProducts.add(line.getProductId())) continue;
                ProductInventory inventory = inventoryRepository.findByProductIdAndStoreId(line.getProductId(), storeId).orElse(null);
                purchases.add(new RecentPurchase(line.getProductId(), line.getProductName(), invoice.getIssuedAt(),
                        line.getQuantity(), inventory == null ? 0 : inventory.availableQuantity()));
                if (purchases.size() == 4) return List.copyOf(purchases);
            }
        }
        return List.copyOf(purchases);
    }

    private String normalizePhone(String phone) {
        if (phone == null) throw new IllegalArgumentException("Nhập số điện thoại thành viên.");
        String normalized = phone.trim().replaceAll("[\\s().-]", "");
        if (normalized.length() < 9 || normalized.length() > 16 || !normalized.matches("\\+?\\d+"))
            throw new IllegalArgumentException("Số điện thoại chưa đúng định dạng.");
        return normalized;
    }

    private String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return "•••• " + digits.substring(Math.max(0, digits.length() - 4));
    }

    public record SharedPreferences(String skinType, String hairType, String skinConcerns, String hairConcerns) { }
    public record RecentPurchase(Long productId, String productName, java.time.LocalDateTime purchasedAt,
                                 Integer previousQuantity, int availableAtThisStore) { }
    public record MemberCard(String name, String maskedPhone, int loyaltyPoints, String tier,
                             SharedPreferences sharedPreferences, List<RecentPurchase> recentPurchases) { }
}

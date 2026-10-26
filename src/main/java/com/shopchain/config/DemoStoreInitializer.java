package com.shopchain.config;

import com.shopchain.entity.Product;
import com.shopchain.entity.ProductInventory;
import com.shopchain.entity.Store;
import com.shopchain.repository.ProductInventoryRepository;
import com.shopchain.repository.ProductRepository;
import com.shopchain.repository.StoreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Adds clearly labelled, idempotent branch data for the local demo catalog. */
@Component
@Order(20)
public class DemoStoreInitializer implements CommandLineRunner {
    private static final int INITIAL_DEMO_STOCK = 5;

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;

    public DemoStoreInitializer(StoreRepository storeRepository,
                                ProductRepository productRepository,
                                ProductInventoryRepository inventoryRepository) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Store> existingStores = storeRepository.findAll().stream()
                .collect(Collectors.toMap(store -> key(store.getName()), Function.identity(), (first, ignored) -> first));
        List<Product> demoProducts = productRepository.findAll().stream()
                .filter(product -> product.getSku() != null && product.getSku().startsWith("TECH-"))
                .toList();

        for (StoreSeed seed : branchSeeds()) {
            String key = key(seed.name());
            Store store = existingStores.get(key);
            if (store == null) {
                store = storeRepository.save(Store.builder()
                        .name(seed.name())
                        .address(seed.address())
                        .city(seed.city())
                        .district(seed.district())
                        .latitude(seed.latitude())
                        .longitude(seed.longitude())
                        .active(true)
                        .pickupEnabled(true)
                        .build());
                existingStores.put(key, store);
            }
            seedInventory(store, demoProducts);
        }
    }

    private void seedInventory(Store store, List<Product> products) {
        for (Product product : products) {
            if (inventoryRepository.findByProductIdAndStoreId(product.getId(), store.getId()).isEmpty()) {
                inventoryRepository.save(ProductInventory.builder()
                        .product(product)
                        .store(store)
                        .price(product.getPrice())
                        .stockQuantity(INITIAL_DEMO_STOCK)
                        .build());
            }
        }
    }

    private String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private List<StoreSeed> branchSeeds() {
        return List.of(
                new StoreSeed("Điểm demo Techtra - Thủ Đức", "Vị trí mô phỏng khu vực trung tâm Thủ Đức",
                        "Thành phố Hồ Chí Minh", "Thành phố Thủ Đức", 10.8505, 106.7717),
                new StoreSeed("Điểm demo Techtra - Quận 7", "Vị trí mô phỏng khu vực Phú Mỹ Hưng",
                        "Thành phố Hồ Chí Minh", "Quận 7", 10.7328, 106.7210),
                new StoreSeed("Điểm demo Techtra - Hoàn Kiếm", "Vị trí mô phỏng khu vực trung tâm Hoàn Kiếm",
                        "Thành phố Hà Nội", "Quận Hoàn Kiếm", 21.0285, 105.8542),
                new StoreSeed("Điểm demo Techtra - Cầu Giấy", "Vị trí mô phỏng khu vực Cầu Giấy",
                        "Thành phố Hà Nội", "Quận Cầu Giấy", 21.0368, 105.7902),
                new StoreSeed("Điểm demo Techtra - Hải Châu", "Vị trí mô phỏng khu vực trung tâm Hải Châu",
                        "Thành phố Đà Nẵng", "Quận Hải Châu", 16.0471, 108.2068),
                new StoreSeed("Điểm demo Techtra - Ninh Kiều", "Vị trí mô phỏng khu vực trung tâm Ninh Kiều",
                        "Thành phố Cần Thơ", "Quận Ninh Kiều", 10.0452, 105.7469),
                new StoreSeed("Điểm demo Techtra - Hồng Bàng", "Vị trí mô phỏng khu vực trung tâm Hồng Bàng",
                        "Thành phố Hải Phòng", "Quận Hồng Bàng", 20.8449, 106.6881),
                new StoreSeed("Điểm demo Techtra - Nha Trang", "Vị trí mô phỏng khu vực trung tâm Nha Trang",
                        "Tỉnh Khánh Hòa", "Thành phố Nha Trang", 12.2388, 109.1967),
                new StoreSeed("Điểm demo Techtra - Vũng Tàu", "Vị trí mô phỏng khu vực trung tâm Vũng Tàu",
                        "Thành phố Hồ Chí Minh", "Thành phố Vũng Tàu", 10.3459, 107.0843),
                new StoreSeed("Điểm demo Techtra - Ba Đình", "Vị trí mô phỏng khu vực Ba Đình",
                        "Thành phố Hà Nội", "Quận Ba Đình", 21.0341, 105.8146),
                new StoreSeed("Điểm demo Techtra - Hạ Long", "Vị trí mô phỏng khu vực trung tâm Hạ Long",
                        "Tỉnh Quảng Ninh", "Thành phố Hạ Long", 20.9505, 107.0731),
                new StoreSeed("Điểm demo Techtra - Huế", "Vị trí mô phỏng khu vực trung tâm Huế",
                        "Thành phố Huế", "Quận Thuận Hóa", 16.4637, 107.5909),
                new StoreSeed("Điểm demo Techtra - Vinh", "Vị trí mô phỏng khu vực trung tâm Vinh",
                        "Tỉnh Nghệ An", "Thành phố Vinh", 18.6796, 105.6813),
                new StoreSeed("Điểm demo Techtra - Quy Nhơn", "Vị trí mô phỏng khu vực trung tâm Quy Nhơn",
                        "Tỉnh Bình Định", "Thành phố Quy Nhơn", 13.7820, 109.2197),
                new StoreSeed("Điểm demo Techtra - Buôn Ma Thuột", "Vị trí mô phỏng khu vực trung tâm Buôn Ma Thuột",
                        "Tỉnh Đắk Lắk", "Thành phố Buôn Ma Thuột", 12.6664, 108.0382),
                new StoreSeed("Điểm demo Techtra - Đà Lạt", "Vị trí mô phỏng khu vực trung tâm Đà Lạt",
                        "Tỉnh Lâm Đồng", "Thành phố Đà Lạt", 11.9404, 108.4583),
                new StoreSeed("Điểm demo Techtra - Biên Hòa", "Vị trí mô phỏng khu vực trung tâm Biên Hòa",
                        "Tỉnh Đồng Nai", "Thành phố Biên Hòa", 10.9574, 106.8427),
                new StoreSeed("Điểm demo Techtra - Mỹ Tho", "Vị trí mô phỏng khu vực trung tâm Mỹ Tho",
                        "Tỉnh Tiền Giang", "Thành phố Mỹ Tho", 10.3600, 106.3599),
                new StoreSeed("Điểm demo Techtra - Rạch Giá", "Vị trí mô phỏng khu vực trung tâm Rạch Giá",
                        "Tỉnh Kiên Giang", "Thành phố Rạch Giá", 10.0125, 105.0809),
                new StoreSeed("Điểm demo Techtra - Long Xuyên", "Vị trí mô phỏng khu vực trung tâm Long Xuyên",
                        "Tỉnh An Giang", "Thành phố Long Xuyên", 10.3864, 105.4352),
                new StoreSeed("Điểm demo Techtra - Phú Quốc", "Vị trí mô phỏng khu vực trung tâm Dương Đông",
                        "Tỉnh Kiên Giang", "Thành phố Phú Quốc", 10.2167, 103.9597)
        );
    }

    private record StoreSeed(String name, String address, String city, String district,
                             double latitude, double longitude) { }
}

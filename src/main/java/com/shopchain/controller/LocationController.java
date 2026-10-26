package com.shopchain.controller;

import com.shopchain.entity.Store;
import com.shopchain.repository.StoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final StoreRepository storeRepository;

    public LocationController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping("/nearest")
    public List<StoreResult> nearest(@RequestParam double latitude, @RequestParam double longitude,
                                     @RequestParam(defaultValue = "5") int limit) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Tọa độ không hợp lệ.");
        }
        return storeRepository.findByActiveTrueAndPickupEnabledTrueAndLatitudeIsNotNullAndLongitudeIsNotNull()
                .stream()
                .map(store -> result(store, distanceKm(latitude, longitude, store.getLatitude(), store.getLongitude())))
                .sorted(Comparator.comparingDouble(StoreResult::distanceKm))
                .limit(Math.min(5, Math.max(1, limit)))
                .toList();
    }

    @GetMapping("/search")
    public List<StoreResult> search(@RequestParam String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return storeRepository.findAll().stream()
                .filter(Store::isActive)
                .filter(Store::isPickupEnabled)
                .filter(store -> normalized.isBlank()
                        || contains(store.getName(), normalized)
                        || contains(store.getAddress(), normalized)
                        || contains(store.getDistrict(), normalized)
                        || contains(store.getCity(), normalized))
                .sorted(Comparator.comparingInt(store -> searchRank(store, normalized)))
                .map(store -> result(store, null))
                .limit(5)
                .toList();
    }

    private int searchRank(Store store, String query) {
        if (query.isBlank()) return 4;
        if (equals(store.getName(), query) || equals(store.getAddress(), query)) return 0;
        if (startsWith(store.getName(), query) || startsWith(store.getAddress(), query)) return 1;
        if (equals(store.getDistrict(), query) || equals(store.getCity(), query)) return 2;
        return 3;
    }

    private boolean equals(String value, String query) {
        return value != null && value.trim().equalsIgnoreCase(query);
    }

    private boolean startsWith(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private StoreResult result(Store store, Double distance) {
        return new StoreResult(store.getId(), store.getName(), store.getAddress(), store.getDistrict(),
                store.getCity(), store.getPhone(), store.getLatitude(), store.getLongitude(), distance);
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record StoreResult(Long id, String name, String address, String district, String city,
                              String phone, Double latitude, Double longitude, Double distanceKm) { }
}

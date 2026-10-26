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
    public List<StoreResult> nearest(@RequestParam double latitude, @RequestParam double longitude) {
        return storeRepository.findByActiveTrueAndPickupEnabledTrueAndLatitudeIsNotNullAndLongitudeIsNotNull()
                .stream()
                .map(store -> result(store, distanceKm(latitude, longitude, store.getLatitude(), store.getLongitude())))
                .sorted(Comparator.comparingDouble(StoreResult::distanceKm))
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
                .map(store -> result(store, null))
                .toList();
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

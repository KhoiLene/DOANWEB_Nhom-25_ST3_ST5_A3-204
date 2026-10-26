package com.shopchain.controller;

import com.shopchain.entity.Store;
import com.shopchain.repository.StoreRepository;
import com.shopchain.service.NominatimGeocodingService;
import com.shopchain.service.NominatimGeocodingService.GeocodingUnavailableException;
import com.shopchain.service.NominatimGeocodingService.GeocodedLocation;
import com.shopchain.service.NominatimGeocodingService.AddressSuggestion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final StoreRepository storeRepository;
    private final NominatimGeocodingService geocodingService;

    public LocationController(StoreRepository storeRepository, NominatimGeocodingService geocodingService) {
        this.storeRepository = storeRepository;
        this.geocodingService = geocodingService;
    }

    @GetMapping("/geocode")
    public ResponseEntity<?> geocode(@RequestParam String query,
                                     @RequestParam(required = false) String countryCode) {
        try {
            GeocodedLocation location = geocodingService.geocode(query, countryCode);
            return ResponseEntity.ok(location);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
        } catch (GeocodingUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(java.util.Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestParam String query) {
        try {
            List<AddressSuggestion> suggestions = geocodingService.suggest(query);
            return ResponseEntity.ok(suggestions);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
        } catch (GeocodingUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(java.util.Map.of("error", exception.getMessage()));
        }
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
        String normalized = query == null ? "" : fold(query.trim());
        List<String> terms = java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(term -> term.length() > 1).toList();
        return storeRepository.findAll().stream()
                .filter(Store::isActive)
                .filter(Store::isPickupEnabled)
                .filter(store -> matchesTerms(store, terms))
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
        return value != null && fold(value.trim()).equals(query);
    }

    private boolean startsWith(String value, String query) {
        return value != null && fold(value).startsWith(query);
    }

    private boolean contains(String value, String query) {
        return value != null && fold(value).contains(query);
    }

    private boolean matchesTerms(Store store, List<String> terms) {
        if (terms.isEmpty()) return true;
        return terms.stream().allMatch(term -> contains(store.getName(), term)
                || contains(store.getAddress(), term)
                || contains(store.getDistrict(), term)
                || contains(store.getCity(), term));
    }

    private String fold(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
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

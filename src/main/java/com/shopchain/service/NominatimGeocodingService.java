package com.shopchain.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Photon-backed address search. The old service name remains for callers during migration. */
@Service
public class NominatimGeocodingService {
    private static final Duration MIN_REQUEST_GAP = Duration.ofMillis(700);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private final RestClient client = createClient();
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private Instant lastRequestAt = Instant.EPOCH;

    private RestClient createClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2500);
        requestFactory.setReadTimeout(5000);
        return RestClient.builder()
                .baseUrl("https://photon.komoot.io")
                .defaultHeader(HttpHeaders.USER_AGENT, "TechtraBeautyStoreLocator/1.0 (demo)")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory)
                .build();
    }

    public List<AddressSuggestion> suggest(String query) {
        String normalized = validateQuery(query, 3, 160);
        return lookup(normalized, 5, "vn");
    }

    public GeocodedLocation geocode(String query, String countryCode) {
        String normalized = validateQuery(query, 1, 300);
        String country = countryCode == null || countryCode.isBlank() ? "vn" : countryCode.trim().toLowerCase(Locale.ROOT);
        if (!country.equals("vn")) throw new IllegalArgumentException("Chỉ hỗ trợ tìm địa chỉ tại Việt Nam.");
        List<AddressSuggestion> results = lookup(normalized, 1, country);
        if (results.isEmpty()) throw new IllegalArgumentException("Không tìm thấy địa chỉ. Hãy nhập chi tiết hơn.");
        AddressSuggestion result = results.get(0);
        return new GeocodedLocation(result.latitude(), result.longitude(), result.label(),
                result.district(), result.city());
    }

    private String validateQuery(String query, int minLength, int maxLength) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Hãy nhập địa chỉ cần tìm.");
        String trimmed = query.trim();
        if (trimmed.length() < minLength) throw new IllegalArgumentException("Nhập ít nhất " + minLength + " ký tự để tìm địa chỉ.");
        if (trimmed.length() > maxLength) throw new IllegalArgumentException("Địa chỉ tối đa " + maxLength + " ký tự.");
        return trimmed;
    }

    private List<AddressSuggestion> lookup(String query, int limit, String country) {
        String cacheKey = country + ":" + query.toLowerCase(Locale.ROOT) + ":" + limit;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(Instant.now())) return cached.results();

        PhotonResponse response;
        synchronized (this) {
            cached = cache.get(cacheKey);
            if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(Instant.now())) return cached.results();
            waitForRateLimit();
            lastRequestAt = Instant.now();
            try {
                response = client.get()
                        .uri(builder -> builder.path("/api")
                                .queryParam("q", query)
                                .queryParam("limit", limit)
                                .queryParam("countrycode", country.toUpperCase(Locale.ROOT))
                                .build())
                        .retrieve()
                        .body(PhotonResponse.class);
            } catch (RestClientException exception) {
                throw new GeocodingUnavailableException("Dịch vụ gợi ý địa chỉ đang bận. Hãy thử lại sau.");
            }
        }
        List<AddressSuggestion> results = parseResults(response);
        cache.put(cacheKey, new CacheEntry(Instant.now(), results));
        if (cache.size() > 500) {
            Instant cutoff = Instant.now().minus(CACHE_TTL);
            cache.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
            int excess = cache.size() - 500;
            if (excess > 0) {
                cache.entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getValue().createdAt()))
                        .limit(excess)
                        .map(java.util.Map.Entry::getKey)
                        .toList()
                        .forEach(cache::remove);
            }
        }
        return results;
    }

    private List<AddressSuggestion> parseResults(PhotonResponse response) {
        List<AddressSuggestion> results = new ArrayList<>();
        if (response == null || response.features() == null) return results;
        for (PhotonFeature feature : response.features()) {
            if (feature == null || feature.geometry() == null || feature.geometry().coordinates() == null
                    || feature.geometry().coordinates().size() < 2 || feature.properties() == null) continue;
            PhotonProperties properties = feature.properties();
            double longitude = feature.geometry().coordinates().get(0);
            double latitude = feature.geometry().coordinates().get(1);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) continue;
            String label = formatLabel(feature.properties());
            if (!label.isBlank()) results.add(new AddressSuggestion(label, latitude, longitude,
                    firstNonBlank(properties.district(), properties.county()),
                    firstNonBlank(properties.city(), properties.state())));
        }
        return List.copyOf(results);
    }

    private String formatLabel(PhotonProperties properties) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addPart(parts, properties.name());
        String street = ((properties.houseNumber() == null ? "" : properties.houseNumber()) + " "
                + (properties.street() == null ? "" : properties.street())).trim();
        addPart(parts, street);
        addPart(parts, properties.district());
        addPart(parts, properties.city());
        addPart(parts, properties.county());
        addPart(parts, properties.state());
        addPart(parts, properties.country());
        return String.join(", ", parts);
    }

    private void addPart(LinkedHashSet<String> parts, String value) {
        if (value == null || value.isBlank()) return;
        String trimmed = value.trim();
        if (parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(trimmed))) parts.add(trimmed);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred.trim();
        return fallback == null || fallback.isBlank() ? "" : fallback.trim();
    }

    private void waitForRateLimit() {
        long remainingMillis = MIN_REQUEST_GAP.toMillis() - Duration.between(lastRequestAt, Instant.now()).toMillis();
        if (remainingMillis <= 0) return;
        try {
            Thread.sleep(remainingMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeocodingUnavailableException("Yêu cầu tìm địa chỉ đã bị gián đoạn.");
        }
    }

    private record CacheEntry(Instant createdAt, List<AddressSuggestion> results) { }
    private record PhotonResponse(List<PhotonFeature> features) { }
    private record PhotonFeature(PhotonProperties properties, PhotonGeometry geometry) { }
    private record PhotonGeometry(List<Double> coordinates) { }
    private record PhotonProperties(String name, @JsonProperty("housenumber") String houseNumber,
                                    String street, String district, String city, String county,
                                    String state, String country) { }
    public record AddressSuggestion(String label, double latitude, double longitude, String district, String city) { }
    public record GeocodedLocation(double latitude, double longitude, String displayName, String district, String city) { }

    public static class GeocodingUnavailableException extends RuntimeException {
        public GeocodingUnavailableException(String message) { super(message); }
    }
}

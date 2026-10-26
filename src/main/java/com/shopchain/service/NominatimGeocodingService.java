package com.shopchain.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class NominatimGeocodingService {
    private static final Duration MIN_REQUEST_GAP = Duration.ofSeconds(1);
    private final RestClient client = createClient();
    private Instant lastRequestAt = Instant.EPOCH;

    private RestClient createClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(7000);
        return RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader(HttpHeaders.USER_AGENT, "TechtraBeautyStoreLocator/1.0")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory)
                .build();
    }

    public GeocodedLocation geocode(String query, String countryCode) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Hãy nhập địa chỉ cần tìm.");
        if (query.length() > 300) throw new IllegalArgumentException("Địa chỉ tối đa 300 ký tự.");
        String country = countryCode == null ? "" : countryCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!country.isEmpty() && !country.equals("vn")) throw new IllegalArgumentException("Mã quốc gia không hợp lệ.");

        List<NominatimResult> results;
        synchronized (this) {
            waitForRateLimit();
            lastRequestAt = Instant.now();
            try {
                results = client.get()
                        .uri(builder -> {
                            var uri = builder.path("/search")
                                    .queryParam("format", "jsonv2")
                                    .queryParam("limit", 1)
                                    .queryParam("accept-language", "vi")
                                    .queryParam("q", query.trim());
                            if (!country.isEmpty()) uri.queryParam("countrycodes", country);
                            return uri.build();
                        })
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() { });
            } catch (RestClientException exception) {
                throw new GeocodingUnavailableException("Không thể tra địa chỉ lúc này. Hãy thử lại sau.");
            }
        }
        if (results == null || results.isEmpty()) throw new IllegalArgumentException("Không tìm thấy địa chỉ. Hãy nhập chi tiết hơn.");
        NominatimResult result = results.get(0);
        try {
            double latitude = Double.parseDouble(result.lat());
            double longitude = Double.parseDouble(result.lon());
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) throw new NumberFormatException();
            return new GeocodedLocation(latitude, longitude, result.displayName());
        } catch (NumberFormatException exception) {
            throw new GeocodingUnavailableException("Dịch vụ bản đồ trả về tọa độ không hợp lệ.");
        }
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

    private record NominatimResult(String lat, String lon, @JsonProperty("display_name") String displayName) { }
    public record GeocodedLocation(double latitude, double longitude, String displayName) { }

    public static class GeocodingUnavailableException extends RuntimeException {
        public GeocodingUnavailableException(String message) { super(message); }
    }
}

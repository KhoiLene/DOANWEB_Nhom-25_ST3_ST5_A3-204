package com.shopchain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GeminiApiClient {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern VIETNAM_PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:[ .-]?\\d){8,10}(?!\\d)");

    private final RestClient restClient;
    private final String apiKey;
    private final String chatModel;
    private final String embeddingModel;
    private final int dimensions;

    public GeminiApiClient(@Value("${assistant.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                           @Value("${assistant.gemini.api-key:}") String apiKey,
                           @Value("${assistant.gemini.chat-model:gemini-3.1-flash-lite}") String chatModel,
                           @Value("${assistant.gemini.embedding-model:gemini-embedding-2}") String embeddingModel,
                           @Value("${assistant.gemini.embedding-dimensions:768}") int dimensions,
                           @Value("${assistant.gemini.timeout-seconds:30}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1, timeoutSeconds) * 1000);
        requestFactory.setReadTimeout(Math.max(1, timeoutSeconds) * 1000);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.dimensions = Math.max(128, Math.min(dimensions, 3072));
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String chatModel() {
        return chatModel;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public int dimensions() {
        return dimensions;
    }

    public String sanitize(String text) {
        if (text == null) return "";
        return VIETNAM_PHONE.matcher(EMAIL.matcher(text).replaceAll("[email đã ẩn]")).replaceAll("[số điện thoại đã ẩn]");
    }

    public float[] embed(String text) {
        requireConfigured();
        String input = sanitize(text);
        Map<String, Object> body = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", "Represent this text for retrieval:\n" + input))),
                "output_dimensionality", dimensions);
        Map<?, ?> response = post("/v1beta/models/{model}:embedContent", embeddingModel, body);
        Object rawEmbedding = response.get("embedding");
        if (!(rawEmbedding instanceof Map<?, ?> embedding) || !(embedding.get("values") instanceof List<?> values)
                || values.size() != dimensions) {
            throw new GeminiApiException("Gemini embedding trả về vector không đúng kích thước.");
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Number number)) throw new GeminiApiException("Gemini embedding trả về dữ liệu không hợp lệ.");
            vector[index] = number.floatValue();
        }
        return vector;
    }

    public String generate(String systemInstruction, String userPrompt) {
        requireConfigured();
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", sanitize(systemInstruction)))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", sanitize(userPrompt))))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 450));
        Map<?, ?> response = post("/v1beta/models/{model}:generateContent", chatModel, body);
        Object rawCandidates = response.get("candidates");
        if (!(rawCandidates instanceof List<?> candidates) || candidates.isEmpty()
                || !(candidates.get(0) instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            throw new GeminiApiException("Gemini không trả được câu trả lời văn bản.");
        }
        List<String> textParts = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> map && map.get("text") instanceof String value && !value.isBlank()) {
                textParts.add(value.trim());
            }
        }
        if (textParts.isEmpty()) throw new GeminiApiException("Gemini không trả được câu trả lời văn bản.");
        return String.join("\n", textParts);
    }

    private Map<?, ?> post(String path, String model, Map<String, Object> body) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Map<?, ?> result = restClient.post().uri(path, model)
                        .header("x-goog-api-key", apiKey)
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (result == null) throw new GeminiApiException("Gemini trả về phản hồi rỗng.");
                return result;
            } catch (RestClientResponseException exception) {
                HttpStatusCode status = exception.getStatusCode();
                if (status.value() == 503 && attempt < 2) {
                    try {
                        Thread.sleep(500L << attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new GeminiApiException("Yêu cầu Gemini bị gián đoạn. Vui lòng thử lại.");
                    }
                    continue;
                }
                if (status.value() == 429) throw new GeminiApiException("Gemini đã chạm hạn mức miễn phí. Vui lòng thử lại sau.");
                if (status.value() == 503) throw new GeminiApiException("Gemini đang quá tải tạm thời. Vui lòng thử lại sau ít phút.");
                throw new GeminiApiException("Gemini trả lỗi HTTP " + status.value() + ".");
            } catch (RestClientException exception) {
                throw new GeminiApiException("Không kết nối được tới Gemini API.");
            }
        }
        throw new GeminiApiException("Gemini đang quá tải tạm thời. Vui lòng thử lại sau ít phút.");
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new GeminiApiException("Chưa cấu hình GEMINI_API_KEY cho ứng dụng.");
    }

    public static class GeminiApiException extends RuntimeException {
        public GeminiApiException(String message) { super(message); }
    }
}

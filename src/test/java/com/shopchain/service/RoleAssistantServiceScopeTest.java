package com.shopchain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAssistantServiceScopeTest {
    private RoleAssistantService assistant;

    @BeforeEach
    void setUp() {
        GeminiApiClient gemini = new GeminiApiClient(
                "https://generativelanguage.googleapis.com", "", "test-model", "test-embedding", 768, 1);
        assistant = new RoleAssistantService(null, null, null, gemini, true, 6, 0.35);
    }

    @Test
    void relatedQuestionsForEveryRolePassTheScopeGateEvenWithOneCharacterTypo() {
        Map<String, List<String>> questionsByRole = Map.of(
                "customer", List.of("cách đặt sản phẩmn", "tôi ở Bình Dương nên chọn chi nhánh nào", "cách thanh toán giỏ hàng"),
                "store-staff", List.of("cách tạo hóa đơnn", "cách sử dụng chức năng bán hàng", "cập nhật đơn hàng thế nào"),
                "warehouse", List.of("cách nhập hàng vào khon", "làm sao điều chuyển hàng", "xử lý đơn giao từ kho"),
                "admin", List.of("cách sử dụng các chức năng ở đây", "làm sao thêm chi nhánh", "quản lý người dùng"));

        questionsByRole.forEach((role, questions) -> questions.forEach(question -> {
            GeminiApiClient.GeminiApiException exception = assertThrows(
                    GeminiApiClient.GeminiApiException.class,
                    () -> assistant.ask(role, question, List.of()),
                    () -> role + " should accept related question: " + question);
            assertEquals("Chưa cấu hình GEMINI_API_KEY cho ứng dụng.", exception.getMessage());
        }));
    }

    @Test
    void unrelatedQuestionsRemainBlockedForEveryRole() {
        for (String role : List.of("customer", "store-staff", "warehouse", "admin")) {
            RoleAssistantService.AssistantReply reply = assistant.ask(role, "giải toán phương trình", List.of());
            assertEquals("ROLE_GUIDE", reply.retrievalMode(), role);
            assertTrue(reply.answer().contains("Mình chỉ hỗ trợ"), role);
        }
    }

    @Test
    void aNormalAnswerAboutTheAdminKnowledgeAreaIsNotMistakenForAnUnavailableAnswer() throws Exception {
        Method detector = RoleAssistantService.class.getDeclaredMethod("refersToMissingKnowledge", String.class);
        detector.setAccessible(true);

        assertFalse((boolean) detector.invoke(assistant, "→ Mở Kho tri thức AI để quản lý tài liệu theo vai trò."));
        assertTrue((boolean) detector.invoke(assistant, "→ Mình chưa có thông tin để trả lời câu hỏi này."));
    }
}

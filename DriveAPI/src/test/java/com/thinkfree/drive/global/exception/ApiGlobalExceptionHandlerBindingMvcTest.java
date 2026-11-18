package com.thinkfree.drive.global.exception;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 컨텍스트(실제 컨트롤러·Boot Jackson/변환 설정·보안 필터)에서 바인딩 실패 응답 확인.
 * 단위 검증은 {@link ApiGlobalExceptionHandlerBindingTest}.
 */
@WithMockUserCustom(roles = "ROLE_USER")
class ApiGlobalExceptionHandlerBindingMvcTest extends AbstractMvcTest {

    private static final String NOT_ALLOWED = "Value is not allowed.";
    private static final String INVALID_FORMAT = "Invalid format.";

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        ResultActions actions = mockMvc.perform(withHost(builder).header(HttpHeaders.ACCEPT_LANGUAGE, "ko"));
        // 실제 응답 본문 기록(검토용)
        System.out.println("[BINDING-MVC] " + actions.andReturn().getRequest().getMethod() + " "
                + actions.andReturn().getRequest().getRequestURI() + "?" + actions.andReturn().getRequest().getQueryString()
                + " -> " + actions.andReturn().getResponse().getStatus() + " "
                + actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return actions;
    }

    private static void expectError(ResultActions actions, int status, String errorCode) throws Exception {
        actions.andExpect(status().is(status))
               .andExpect(jsonPath("$.result").value(false))
               .andExpect(jsonPath("$.code").value(status))
               .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    private static void expectFieldMessage(ResultActions actions, String field, String msg) throws Exception {
        actions.andExpect(jsonPath("$.message").value(anyOf(
                is("[{field=" + field + ", msg=" + msg + "}]"),
                is("[{msg=" + msg + ", field=" + field + "}]"))));
    }

    @Test
    @DisplayName("POST /documents body tt=hwp → 400 REQUEST_012")
    void documentCreate_invalidEnum() throws Exception {
        var actions = perform(MockMvcRequestBuilders.post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tt\":\"hwp\"}"));
        expectError(actions, 400, "REQUEST_012");
        expectFieldMessage(actions, "tt", NOT_ALLOWED);
    }

    @Test
    @DisplayName("POST /documents body pfs=abc → 400 REQUEST_001")
    void documentCreate_numberMismatch() throws Exception {
        var actions = perform(MockMvcRequestBuilders.post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tt\":\"WORD\",\"pfs\":\"abc\"}"));
        expectError(actions, 400, "REQUEST_001");
        expectFieldMessage(actions, "pfs", INVALID_FORMAT);
    }

    @Test
    @DisplayName("POST /documents 깨진 JSON → 400 REQUEST_001 기본 문구")
    void documentCreate_malformedJson() throws Exception {
        var actions = perform(MockMvcRequestBuilders.post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tt\": }"));
        expectError(actions, 400, "REQUEST_001");
        actions.andExpect(jsonPath("$.message").value(GlobalErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @Test
    @DisplayName("GET /resources @ModelAttribute st=hwp → 400 REQUEST_001, 클래스 경로 노출 없음")
    void resourceList_invalidEnum() throws Exception {
        var actions = perform(MockMvcRequestBuilders.get("/api/v1/resources").param("st", "hwp"));
        expectError(actions, 400, "REQUEST_001");
        expectFieldMessage(actions, "st", NOT_ALLOWED);
    }

    @Test
    @DisplayName("GET /resources @ModelAttribute sd=2026/11/13 + st 누락 → 400, 변환 실패만 (record 생성 실패 시 @NotNull 검증은 실행되지 않음)")
    void resourceList_invalidDateAndMissingRequired() throws Exception {
        var actions = perform(MockMvcRequestBuilders.get("/api/v1/resources").param("sd", "2026/11/13"));
        expectError(actions, 400, "REQUEST_001");
        expectFieldMessage(actions, "sd", INVALID_FORMAT);
    }

    @Test
    @DisplayName("GET /resources @ModelAttribute st 누락만 → 400, @NotNull 검증 메시지(Accept-Language ko) 그대로")
    void resourceList_missingRequiredOnly() throws Exception {
        var actions = perform(MockMvcRequestBuilders.get("/api/v1/resources"));
        expectError(actions, 400, "REQUEST_001");
        actions.andExpect(jsonPath("$.message").value(containsString("field=st")))
               .andExpect(jsonPath("$.message").value(containsString("필수 입력값 입니다.")));
    }

    @Test
    @DisplayName("GET /resources/{resourceSeq} 숫자 자리에 abc → 400 REQUEST_001")
    void resourceDetail_pathTypeMismatch() throws Exception {
        var actions = perform(MockMvcRequestBuilders.get("/api/v1/resources/abc"));
        expectError(actions, 400, "REQUEST_001");
        expectFieldMessage(actions, "resourceSeq", INVALID_FORMAT);
    }

    @Test
    @WithMockUserCustom
    @DisplayName("GET /admins/branding/images/{type} type=hwp → 400 REQUEST_012")
    void brandingImage_pathInvalidEnum() throws Exception {
        var actions = perform(MockMvcRequestBuilders.get("/api/v1/admins/branding/images/hwp"));
        expectError(actions, 400, "REQUEST_012");
        expectFieldMessage(actions, "type", NOT_ALLOWED);
    }

    @Test
    @DisplayName("없는 URL → 404 REQUEST_009 (유지)")
    void unknownUrl() throws Exception {
        var actions = perform(MockMvcRequestBuilders.get("/api/v1/no-such-endpoint"));
        expectError(actions, 404, "REQUEST_009");
    }
}

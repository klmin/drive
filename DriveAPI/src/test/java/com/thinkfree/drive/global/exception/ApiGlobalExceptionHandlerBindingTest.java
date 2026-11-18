package com.thinkfree.drive.global.exception;

import com.thinkfree.drive.document.enums.DocumentTemplateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 값을 객체로 바꾸지 못한 경우(바인딩 실패)의 응답 검증.
 * 기준: 없는 URL = 404, 라우트는 맞는데 클라이언트가 보낸 값(본문·쿼리·경로) 변환 실패 = 400 (enum 이면 REQUEST_012).
 * 필드 메시지는 내부 타입명을 노출하지 않는 고정 영어 문구.
 */
class ApiGlobalExceptionHandlerBindingTest {

    private static final String VALIDATION_ERROR = GlobalErrorCode.VALIDATION_ERROR.getErrorCode();
    private static final String INVALID_ENUM = GlobalErrorCode.INVALID_ENUM.getErrorCode();
    private static final String NOT_SUPPORTED_URI = GlobalErrorCode.NOT_SUPPORTED_URI_ERROR.getErrorCode();

    private static final String NOT_ALLOWED = "Value is not allowed.";
    private static final String INVALID_FORMAT = "Invalid format.";

    private MockMvc mockMvc;

    record BodyRequest(@NotNull DocumentTemplateType tt, Long pfs, LocalDate d) {
    }

    record QueryRequest(DocumentTemplateType tt, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate d) {
    }

    @RestController
    static class BindingTestController {

        @PostMapping("/test/body")
        String body(@RequestBody @Valid BodyRequest request) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam(required = false) DocumentTemplateType tt,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate d) {
            return "ok";
        }

        @GetMapping("/test/model")
        String model(@ModelAttribute QueryRequest request) {
            return "ok";
        }

        @GetMapping("/test/path/{id}")
        String path(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/test/path-enum/{tt}")
        String pathEnum(@PathVariable DocumentTemplateType tt) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BindingTestController())
                .setControllerAdvice(new ApiGlobalExceptionHandler())
                .build();
    }

    private ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void expectError(ResultActions actions, int status, String errorCode) throws Exception {
        actions.andExpect(status().is(status))
               .andExpect(jsonPath("$.result").value(false))
               .andExpect(jsonPath("$.code").value(status))
               .andExpect(jsonPath("$.errorCode").value(errorCode))
               .andExpect(jsonPath("$.errors").doesNotExist());
    }

    /** 필드 1개 메시지 전체 일치 — Map.of 순서가 고정이 아니므로 두 순서 모두 허용 */
    private static void expectFieldMessage(ResultActions actions, String field, String msg) throws Exception {
        actions.andExpect(jsonPath("$.message").value(anyOf(
                is("[{field=" + field + ", msg=" + msg + "}]"),
                is("[{msg=" + msg + ", field=" + field + "}]"))));
    }

    // ---------------- @RequestBody ----------------

    @Test
    @DisplayName("body: 정상 값(소문자 enum 포함)은 200")
    void body_ok() throws Exception {
        postBody("{\"tt\":\"word\",\"pfs\":1,\"d\":\"2026-11-13\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("body: 잘못된 enum 값 → 400 INVALID_ENUM")
    void body_invalidEnum() throws Exception {
        var actions = postBody("{\"tt\":\"hwp\"}");
        expectError(actions, 400, INVALID_ENUM);
        expectFieldMessage(actions, "tt", NOT_ALLOWED);
    }

    @Test
    @DisplayName("body: 숫자 자리에 문자열 → 400 VALIDATION_ERROR")
    void body_numberMismatch() throws Exception {
        var actions = postBody("{\"tt\":\"WORD\",\"pfs\":\"abc\"}");
        expectError(actions, 400, VALIDATION_ERROR);
        expectFieldMessage(actions, "pfs", INVALID_FORMAT);
    }

    @Test
    @DisplayName("body: 날짜 형식 오류 → 400 VALIDATION_ERROR")
    void body_invalidDate() throws Exception {
        var actions = postBody("{\"tt\":\"WORD\",\"d\":\"2026-13-45\"}");
        expectError(actions, 400, VALIDATION_ERROR);
        expectFieldMessage(actions, "d", INVALID_FORMAT);
    }

    @Test
    @DisplayName("body: JSON 문법 오류 → 400 VALIDATION_ERROR, 에러 코드 기본 문구")
    void body_malformedJson() throws Exception {
        var actions = postBody("{\"tt\": }");
        expectError(actions, 400, VALIDATION_ERROR);
        actions.andExpect(jsonPath("$.message").value(GlobalErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @Test
    @DisplayName("body: 빈 본문 → 400 VALIDATION_ERROR, 에러 코드 기본 문구")
    void body_empty() throws Exception {
        var actions = postBody("");
        expectError(actions, 400, VALIDATION_ERROR);
        actions.andExpect(jsonPath("$.message").value(GlobalErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @Test
    @DisplayName("body: @NotNull 필드 null → 400 VALIDATION_ERROR, 검증 메시지 그대로 (기존 동작 유지)")
    void body_nullRequired() throws Exception {
        var actions = postBody("{\"tt\":null}");
        expectError(actions, 400, VALIDATION_ERROR);
        actions.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("field=tt")))
               .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(INVALID_FORMAT))));
    }

    // ---------------- @RequestParam ----------------

    @Test
    @DisplayName("param: 정상 값은 200")
    void param_ok() throws Exception {
        mockMvc.perform(get("/test/param").param("tt", "WORD").param("d", "2026-11-13"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("param: 잘못된 enum 값 → 400 INVALID_ENUM")
    void param_invalidEnum() throws Exception {
        var actions = mockMvc.perform(get("/test/param").param("tt", "hwp"));
        expectError(actions, 400, INVALID_ENUM);
        expectFieldMessage(actions, "tt", NOT_ALLOWED);
    }

    @Test
    @DisplayName("param: 날짜 형식 오류 → 400 VALIDATION_ERROR")
    void param_invalidDate() throws Exception {
        var actions = mockMvc.perform(get("/test/param").param("d", "2026/11/13"));
        expectError(actions, 400, VALIDATION_ERROR);
        expectFieldMessage(actions, "d", INVALID_FORMAT);
    }

    // ---------------- @ModelAttribute ----------------

    @Test
    @DisplayName("model: 잘못된 enum 값 → 400 VALIDATION_ERROR, 내부 클래스 경로 노출 없음")
    void model_invalidEnum() throws Exception {
        var actions = mockMvc.perform(get("/test/model").param("tt", "hwp"));
        expectError(actions, 400, VALIDATION_ERROR);
        expectFieldMessage(actions, "tt", NOT_ALLOWED);
    }

    @Test
    @DisplayName("model: 날짜 형식 오류 → 400 VALIDATION_ERROR, 내부 클래스 경로 노출 없음")
    void model_invalidDate() throws Exception {
        var actions = mockMvc.perform(get("/test/model").param("d", "2026/11/13"));
        expectError(actions, 400, VALIDATION_ERROR);
        expectFieldMessage(actions, "d", INVALID_FORMAT);
    }

    // ---------------- @PathVariable · 없는 URL ----------------

    @Test
    @DisplayName("path: 정상 값은 200")
    void path_ok() throws Exception {
        mockMvc.perform(get("/test/path/1")).andExpect(status().isOk());
        mockMvc.perform(get("/test/path-enum/WORD")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("path: 숫자 자리에 문자열 → 400 VALIDATION_ERROR")
    void path_typeMismatch() throws Exception {
        var actions = mockMvc.perform(get("/test/path/abc"));
        expectError(actions, 400, VALIDATION_ERROR);
        expectFieldMessage(actions, "id", INVALID_FORMAT);
    }

    @Test
    @DisplayName("path: 잘못된 enum 값 → 400 INVALID_ENUM")
    void path_invalidEnum() throws Exception {
        var actions = mockMvc.perform(get("/test/path-enum/hwp"));
        expectError(actions, 400, INVALID_ENUM);
        expectFieldMessage(actions, "tt", NOT_ALLOWED);
    }

    @Test
    @DisplayName("없는 URL → 404 NOT_SUPPORTED_URI (유지)")
    void unknownUrl() throws Exception {
        var actions = mockMvc.perform(get("/test/unknown"));
        expectError(actions, 404, NOT_SUPPORTED_URI);
        actions.andExpect(jsonPath("$.message").value(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR.getMessage()));
    }
}

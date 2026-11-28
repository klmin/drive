package com.thinkfree.drive.api.v1.user.controller;

import com.thinkfree.drive.api.v1.user.usecase.UserUseCase;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 재설정 링크 {@code POST /api/v1/users/password/reset-link} 의 userId 검증({@code @NotBlank}·{@code @Email}).
 * 검증이 빠지면 빈 값·형식 오류가 서비스까지 내려가 USER_NOT_FOUND 로 응답됐다.
 */
@DisplayName("POST /api/v1/users/password/reset-link 검증")
class UserControllerPasswordResetValidationTest {

    private final UserService userService = mock(UserService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(mock(UserUseCase.class), userService, mock(com.thinkfree.drive.global.ratelimit.LoginRateLimitGuard.class))).build();
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/users/password/reset-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("userId 누락 → 400, 서비스 호출 안 함")
    void missingUserId() throws Exception {
        send("{}").andExpect(status().isBadRequest());
        verify(userService, never()).sendPasswordResetLink(anyString(), any());
    }

    @Test
    @DisplayName("userId 빈 문자열 → 400, 서비스 호출 안 함")
    void blankUserId() throws Exception {
        send("{\"userId\":\"\"}").andExpect(status().isBadRequest());
        verify(userService, never()).sendPasswordResetLink(anyString(), any());
    }

    @Test
    @DisplayName("userId 이메일 형식 아님 → 400, 서비스 호출 안 함")
    void invalidEmail() throws Exception {
        send("{\"userId\":\"not-an-email\"}").andExpect(status().isBadRequest());
        verify(userService, never()).sendPasswordResetLink(anyString(), any());
    }

    @Test
    @DisplayName("올바른 이메일 → 200, 서비스 호출")
    void validEmail() throws Exception {
        send("{\"userId\":\"user@thinkfree.com\"}").andExpect(status().isOk());
        verify(userService).sendPasswordResetLink(eq("user@thinkfree.com"), any());
    }
}

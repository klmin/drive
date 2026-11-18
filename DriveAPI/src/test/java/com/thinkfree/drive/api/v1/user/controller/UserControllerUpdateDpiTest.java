package com.thinkfree.drive.api.v1.user.controller;

import com.thinkfree.drive.api.v1.user.converter.DefaultLandingPageConverter;
import com.thinkfree.drive.api.v1.user.converter.UserLanguageConverter;
import com.thinkfree.drive.api.v1.user.usecase.UserUseCase;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.UserDetailDto;
import com.thinkfree.drive.user.dto.UserUpdateDto;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본인 정보 수정 {@code PATCH /api/v1/users} 의 {@code dpi} 바인딩 — 명세상 선택 필드라 생략하면 false(이미지 유지).
 * 원시 boolean 일 때는 생략 시 생성자 바인딩이 실패해 400 이었다.
 */
@DisplayName("PATCH /api/v1/users dpi 바인딩")
class UserControllerUpdateDpiTest {

    private static final int USER_SEQ = 7;

    private final UserService userService = mock(UserService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FormattingConversionService conversion = new WebMvcConfigurationSupport().mvcConversionService();
        conversion.addConverter(new UserLanguageConverter());
        conversion.addConverter(new DefaultLandingPageConverter());
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(mock(UserUseCase.class), userService))
                .setConversionService(conversion)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        var details = new CustomUserDetails(new UserDetailDto(USER_SEQ, "user@thinkfree.com", "사용자", "ROLE_USER",
                AuthSource.INTERNAL, 1, Set.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockMultipartHttpServletRequestBuilder patch() {
        var request = MockMvcRequestBuilders.multipart(HttpMethod.PATCH, "/api/v1/users");
        request.param("ul", "ko").param("dlp", "home");
        return request;
    }

    private boolean sentDeleteProfileImage() {
        ArgumentCaptor<UserUpdateDto> captor = ArgumentCaptor.forClass(UserUpdateDto.class);
        verify(userService).updateCurrentUser(eq(USER_SEQ), captor.capture());
        return captor.getValue().deleteProfileImage();
    }

    @Test
    @DisplayName("dpi 생략 → 200, deleteProfileImage=false")
    void dpiOmitted_defaultsToFalse() throws Exception {
        mockMvc.perform(patch()).andExpect(status().isOk());
        assertThat(sentDeleteProfileImage()).isFalse();
    }

    @Test
    @DisplayName("dpi=true → deleteProfileImage=true")
    void dpiTrue_passedThrough() throws Exception {
        mockMvc.perform(patch().param("dpi", "true")).andExpect(status().isOk());
        assertThat(sentDeleteProfileImage()).isTrue();
    }

    @Test
    @DisplayName("dpi=false → deleteProfileImage=false")
    void dpiFalse_passedThrough() throws Exception {
        mockMvc.perform(patch().param("dpi", "false")).andExpect(status().isOk());
        assertThat(sentDeleteProfileImage()).isFalse();
    }
}

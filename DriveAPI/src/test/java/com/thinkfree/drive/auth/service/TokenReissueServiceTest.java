package com.thinkfree.drive.auth.service;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.token.refresh.result.RefreshTokenReissueResult;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.UuidV7Generator;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Auth [Service Layer] -> TokenReissueService 테스트")
public class TokenReissueServiceTest extends ServiceTest {
    @Autowired
    private DefaultRefreshTokenService refreshTokenService;

    @Autowired
    private JwtProvider jwtProvider;

    private final Integer USER_SEQ = 1; // 시드 슈퍼어드민 — 재발급은 refresh 토큰 역할 보유를 다시 확인한다
    private String sessionId;
    private String refreshToken;

    @BeforeEach
    void setup() {
        sessionId = UuidV7Generator.generate();
        refreshToken = jwtProvider.createRefreshToken(USER_SEQ, sessionId, RoleType.SUPER_ADMIN);
    }

    @Nested
    @DisplayName("토큰 재발급")
    class reissueTokens {
        @Test
        @DisplayName("유효하지 않은 JWT 형식의 토큰이면 예외가 발생한다")
        void throwExceptionByAuthInvalidToken() {
            // when - then
            assertThatThrownBy(() -> refreshTokenService.reissueTokens(USER_SEQ, "fake_refreshToken"))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.AUTH_INVALID_TOKEN.getMessage());
        }

        @Test
        @DisplayName("DB에 존재하지 않는 세션의 토큰이면 예외가 발생한다")
        void throwExceptionByTokenNotInDb() {
            // refreshToken은 유효한 JWT지만 DB에 저장되지 않음
            assertThatThrownBy(() -> refreshTokenService.reissueTokens(USER_SEQ, refreshToken))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.AUTH_INVALID_TOKEN.getMessage());
        }

        @Test
        @DisplayName("RefreshToken을 통해서 AccessToken과 RefreshToken을 재발급받는데 성공한다")
        void success() {
            // given
            refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, sessionId, refreshToken));

            // when
            RefreshTokenReissueResult result = refreshTokenService.reissueTokens(USER_SEQ, refreshToken);

            // then
            assertAll(
                    () -> assertThat(result).isNotNull(),
                    () -> assertThat(result.accessToken()).isNotBlank(),
                    () -> assertThat(result.refreshToken()).isNotBlank()
            );
        }
    }
}

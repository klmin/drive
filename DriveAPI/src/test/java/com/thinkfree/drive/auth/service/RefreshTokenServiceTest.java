package com.thinkfree.drive.auth.service;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;
import com.thinkfree.drive.auth.token.refresh.result.RefreshTokenReissueResult;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.UuidV7Generator;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Auth [Service Layer] -> RefreshTokenService 테스트")
public class RefreshTokenServiceTest extends ServiceTest {
    @Autowired
    private DefaultRefreshTokenService refreshTokenService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbc;

    private final Integer USER_SEQ = 1;
    private final String REFRESH_TOKEN = "refresh_token_value";

    @Nested
    @DisplayName("Refresh Token 저장")
    class saveRefreshToken {
        @Test
        @DisplayName("새 세션 ID로 저장하면 DB에 토큰이 생성된다")
        void saveNewSession() {
            // given
            String sessionId = UuidV7Generator.generate();

            // when
            refreshTokenService.saveRefreshToken(USER_SEQ, sessionId, REFRESH_TOKEN);

            // then
            assertThat(refreshTokenValidInDb(sessionId, REFRESH_TOKEN)).isTrue();
            assertThat(jdbc.queryForObject("SELECT user_seq FROM tb_refresh_token WHERE session_id = ?", Integer.class, sessionId)).isEqualTo(USER_SEQ);
        }

        @Test
        @DisplayName("같은 userSeq로 두 번 저장하면 각각 독립적인 세션이 존재한다 (멀티세션)")
        void multipleSessionsPerUser() {
            // given
            String sessionId1 = UuidV7Generator.generate();
            String sessionId2 = UuidV7Generator.generate();

            // when
            refreshTokenService.saveRefreshToken(USER_SEQ, sessionId1, REFRESH_TOKEN + "_1");
            refreshTokenService.saveRefreshToken(USER_SEQ, sessionId2, REFRESH_TOKEN + "_2");

            // then
            assertThat(refreshTokenValidInDb(sessionId1, REFRESH_TOKEN + "_1")).isTrue();
            assertThat(refreshTokenValidInDb(sessionId2, REFRESH_TOKEN + "_2")).isTrue();
        }
    }

    @Test
    @DisplayName("토큰 재발급 시 RTR 정책으로 기존 세션의 RefreshToken이 새 값으로 교체된다")
    void reissueTokensAppliesRtrPolicy() {
        // given
        String sessionId = UuidV7Generator.generate();
        String refreshToken = jwtProvider.createRefreshToken(USER_SEQ, sessionId, RoleType.SUPER_ADMIN);
        refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, sessionId, refreshToken));

        // when
        RefreshTokenReissueResult result = refreshTokenService.reissueTokens(USER_SEQ, refreshToken);

        // then
        assertAll(
                () -> assertThat(refreshTokenValidInDb(sessionId, result.refreshToken())).isTrue(),
                () -> assertThat(refreshTokenValidInDb(sessionId, refreshToken)).as("이전 토큰은 무효").isFalse(),
                () -> assertThat(jwtProvider.getRole(result.accessToken())).as("같은 역할로 재발급").isEqualTo(RoleType.SUPER_ADMIN),
                () -> assertThat(jwtProvider.getRole(result.refreshToken())).isEqualTo(RoleType.SUPER_ADMIN)
        );
    }

    @Test
    @DisplayName("세션 ID로 특정 RefreshToken을 삭제한다")
    void deleteRefreshTokenBySessionId() {
        // given
        String sessionId = UuidV7Generator.generate();
        refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, sessionId, REFRESH_TOKEN));

        // when
        refreshTokenService.deleteRefreshTokenBySessionId(sessionId);

        // then
        assertThat(refreshTokenValidInDb(sessionId, REFRESH_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("userSeq로 해당 사용자의 모든 RefreshToken을 삭제한다")
    void deleteRefreshTokenByUserSeq() {
        // given
        String sessionId1 = UuidV7Generator.generate();
        String sessionId2 = UuidV7Generator.generate();
        refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, sessionId1, REFRESH_TOKEN + "_1"));
        refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, sessionId2, REFRESH_TOKEN + "_2"));

        // when
        refreshTokenService.deleteRefreshTokenByUserSeq(USER_SEQ);

        // then
        assertThat(refreshTokenValidInDb(sessionId1, REFRESH_TOKEN + "_1")).isFalse();
        assertThat(refreshTokenValidInDb(sessionId2, REFRESH_TOKEN + "_2")).isFalse();
    }

    @Test
    @DisplayName("재발급 판정(한 번 로드한 관리 엔티티에서) — 없는 세션·저장 값과 다른 토큰·만료된 토큰은 AUTH_INVALID_TOKEN, 저장 값은 그대로")
    void reissueTokens_invalid() {
        // 없는 세션
        String noSession = UuidV7Generator.generate();
        assertInvalid(jwtProvider.createRefreshToken(USER_SEQ, noSession, RoleType.SUPER_ADMIN));

        // 저장된 값과 다른 토큰(같은 세션)
        String mismatch = UuidV7Generator.generate();
        refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, mismatch, "stored-other-value"));
        assertInvalid(jwtProvider.createRefreshToken(USER_SEQ, mismatch, RoleType.SUPER_ADMIN));
        assertThat(refreshTokenValidInDb(mismatch, "stored-other-value")).as("판정 실패면 고치지 않음").isTrue();

        // 만료된 토큰(값은 일치)
        String expired = UuidV7Generator.generate();
        String expiredToken = jwtProvider.createRefreshToken(USER_SEQ, expired, RoleType.SUPER_ADMIN);
        refreshTokenRepository.save(RefreshToken.restore(null, USER_SEQ, expired, expiredToken, Instant.now().minusSeconds(1)));
        assertInvalid(expiredToken);
        assertThat(jdbc.queryForObject("SELECT refresh_token FROM tb_refresh_token WHERE session_id = ?", String.class, expired)).isEqualTo(expiredToken);

        // 토큰 역할을 보유하지 않음(시드 1 = SUPER_ADMIN 만) — 세션·값이 맞아도 재발급 거부, 저장 값 그대로
        String notHeld = UuidV7Generator.generate();
        String userRoleToken = jwtProvider.createRefreshToken(USER_SEQ, notHeld, RoleType.USER);
        refreshTokenRepository.save(RefreshToken.createRefreshToken(USER_SEQ, notHeld, userRoleToken));
        assertInvalid(userRoleToken);
        assertThat(refreshTokenValidInDb(notHeld, userRoleToken)).isTrue();
    }

    private void assertInvalid(String refreshToken) {
        assertThatThrownBy(() -> refreshTokenService.reissueTokens(USER_SEQ, refreshToken))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN));
    }
}

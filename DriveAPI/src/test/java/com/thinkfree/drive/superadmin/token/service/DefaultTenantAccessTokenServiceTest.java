package com.thinkfree.drive.superadmin.token.service;

import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.exception.SuperAdminErrorCode;
import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;
import com.thinkfree.drive.superadmin.token.repository.TenantAccessTokenRepository;
import com.thinkfree.drive.superadmin.token.result.TenantAccessTokenConsumeResult;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static com.thinkfree.drive.fixture.UserFixture.TEST_SUPER_ADMIN_01;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantAccessToken [Service Layer] -> DefaultTenantAccessTokenService 테스트")
class DefaultTenantAccessTokenServiceTest extends ServiceTest {

    @Autowired
    private TenantAccessTokenService tenantAccessTokenService;

    @Autowired
    private TenantAccessTokenRepository tenantAccessTokenRepository;

    private static final int TENANT_SEQ = 1;
    private int superAdminUserSeq;

    @BeforeEach
    void setup() {
        insertUserWithRole(TEST_SUPER_ADMIN_01.toUser(), TEST_SUPER_ADMIN_01.getRoleType());
        superAdminUserSeq = userQueryRepository.findActiveSuperAdminAuthByUserId(TEST_SUPER_ADMIN_01.getUserId()).getUserSeq();
    }

    @Nested
    @DisplayName("임시 접근 키 발급")
    class Issue {

        @Test
        @DisplayName("발급 성공 시 raw 토큰을 반환하고 DB에 해시가 저장된다")
        void success() {
            // when
            String rawToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // then
            assertThat(rawToken).isNotBlank();
            TenantAccessToken saved = tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(rawToken));
            assertThat(saved).isNotNull();
            assertThat(saved.getSuperAdminUserSeq()).isEqualTo(superAdminUserSeq);
            assertThat(saved.getTenantSeq()).isEqualTo(TENANT_SEQ);
            assertThat(saved.getExpirationDate()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("동일 테넌트에 재발급 시 기존 토큰이 삭제되고 새 토큰이 저장된다")
        void reissueDeletesPreviousToken() {
            // given
            String firstToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // when
            String secondToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // then
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(firstToken))).isNull();
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(secondToken))).isNotNull();
        }

        @Test
        @DisplayName("다른 테넌트에 재발급해도 기존 테넌트의 토큰은 삭제되지 않는다")
        void reissueForOtherTenantDoesNotAffectCurrentTenant() {
            // given
            int otherTenantSeq = 2;
            String tokenForTenant1 = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // when
            tenantAccessTokenService.issue(superAdminUserSeq, otherTenantSeq);

            // then
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(tokenForTenant1))).isNotNull();
        }

        @Test
        @DisplayName("발급된 raw 토큰은 DB에 해시값으로 저장된다 (plain text 저장 금지)")
        void rawTokenIsStoredAsHash() {
            // when
            String rawToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // then
            TenantAccessToken saved = tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(rawToken));
            assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
            assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.hash(rawToken));
        }
    }

    @Nested
    @DisplayName("임시 접근 키 검증 및 소비")
    class ConsumeAndGet {

        @Test
        @DisplayName("유효한 토큰 검증 성공 시 발급자·테넌트를 반환하고 DB에서 삭제된다")
        void success() {
            // given
            String rawToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // when
            TenantAccessTokenConsumeResult result = tenantAccessTokenService.consumeAndGet(rawToken);

            // then
            assertThat(result.superAdminUserSeq()).isEqualTo(superAdminUserSeq);
            assertThat(result.tenantSeq()).isEqualTo(TENANT_SEQ);
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 토큰이면 INVALID_TENANT_ACCESS_TOKEN 예외가 발생한다")
        void throwExceptionByInvalidToken() {
            assertThatThrownBy(() -> tenantAccessTokenService.consumeAndGet("nonexistenttoken"))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN.getMessage());
        }

        @Test
        @DisplayName("만료된 토큰이면 EXPIRED_TENANT_ACCESS_TOKEN 예외가 발생한다")
        void throwExceptionByExpiredToken() {
            // given
            String rawToken = "expiredtoken1234567890123456";
            Instant past = Instant.now().minusSeconds(10);
            tenantAccessTokenRepository.save(
                    TenantAccessToken.of(superAdminUserSeq, TENANT_SEQ, TokenHasher.hash(rawToken), past, past)
            );

            // when - then
            assertThatThrownBy(() -> tenantAccessTokenService.consumeAndGet(rawToken))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(SuperAdminErrorCode.EXPIRED_TENANT_ACCESS_TOKEN.getMessage());
        }

        @Test
        @DisplayName("이미 소비된 토큰을 재사용하면 INVALID_TENANT_ACCESS_TOKEN 예외가 발생한다")
        void throwExceptionByAlreadyConsumedToken() {
            // given
            String rawToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);
            tenantAccessTokenService.consumeAndGet(rawToken);

            // when - then
            assertThatThrownBy(() -> tenantAccessTokenService.consumeAndGet(rawToken))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN.getMessage());
        }
    }

    @Nested
    @DisplayName("만료 토큰 일괄 삭제")
    class DeleteExpiredTokens {

        @Test
        @DisplayName("만료 기준 시각 이전의 토큰만 삭제되고 유효한 토큰은 남는다")
        void deletesOnlyExpiredTokens() {
            // given
            Instant past = Instant.now().minusSeconds(60);
            Instant future = Instant.now().plusSeconds(300);

            tenantAccessTokenRepository.save(
                    TenantAccessToken.of(superAdminUserSeq, 1, TokenHasher.hash("expiredtoken111111111111111111"), past, past)
            );
            tenantAccessTokenRepository.save(
                    TenantAccessToken.of(superAdminUserSeq, 2, TokenHasher.hash("validtoken1111111111111111111"), past, future)
            );

            // when
            int deletedCount = tenantAccessTokenRepository.deleteExpiredTokens(Instant.now());

            // then
            assertThat(deletedCount).isEqualTo(1);
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash("expiredtoken111111111111111111"))).isNull();
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash("validtoken1111111111111111111"))).isNotNull();
        }

        @Test
        @DisplayName("만료된 토큰이 없으면 삭제 건수가 0이다")
        void returnsZeroWhenNoExpiredTokens() {
            // given
            String rawToken = tenantAccessTokenService.issue(superAdminUserSeq, TENANT_SEQ);

            // when
            int deletedCount = tenantAccessTokenRepository.deleteExpiredTokens(Instant.now().minusSeconds(10));

            // then
            assertThat(deletedCount).isEqualTo(0);
            assertThat(tenantAccessTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))).isNotNull();
        }
    }
}

package com.thinkfree.drive.sso.protocol.oidc.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import com.thinkfree.drive.sso.protocol.oidc.entity.SsoOidcConfig;
import com.thinkfree.drive.sso.protocol.oidc.query.SsoOidcConfigQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 SsoOidcConfig 이관 회귀 — 읽기({@link DefaultSsoOidcConfigQueryRepository}: findById·findAllByProviderSeqList — 전부 JPQL DTO)·쓰기({@link DefaultSsoOidcConfigRepository}: upsert(ON CONFLICT sso_provider_seq, JdbcClient)·IN 삭제(JPQL)).
 * 단건 save·update 는 호출부가 없어 W4(10-13) 에서 삭제. tb_sso_oidc_config 에 FK 는 없고 공급자 조인 조회도 없어 공급자 행은 만들지 않는다(seq 는 임의 큰 값). 시드 = user 1 superadmin·2 admin. 트랜잭션 롤백.
 * 검증 포인트 = 감사 컬럼(DB 값 — 컨텍스트 사용자·선언 값, 인자 엔티티 write-back 없음), allow_idp_initiated Y/N 왕복, upsert 충돌 시 register_* 유지·update_* = 컨텍스트/엔티티 값, 빈 IN 가드, NOT NULL 위반(allowIdpInitiated null)은 맨 끝.
 */
@DisplayName("DefaultSsoOidcConfigRepository / DefaultSsoOidcConfigQueryRepository — JPQL + JdbcClient upsert(PostgreSQL Testcontainers)")
class DefaultSsoOidcConfigRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int S1 = 910001;
    private static final int S2 = 910002;
    private static final int S3 = 910003;

    @Autowired
    private SsoOidcConfigRepository repository;
    @Autowired
    private SsoOidcConfigQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private static SsoOidcConfig.SsoOidcConfigBuilder<?, ?> oidc(int seq, String clientId, boolean allowIdpInitiated) {
        return SsoOidcConfig.builder().ssoProviderSeq(seq).issuerUrl("https://idp.example.test/" + seq).clientId(clientId).clientSecret("secret-" + clientId)
                .scopes("openid email").allowIdpInitiated(allowIdpInitiated);
    }

    private SsoOidcConfigData findById(int seq) {
        return queryRepository.findById(SsoOidcConfigQuery.Find.builder().ssoProviderSeq(seq).build());
    }

    private List<SsoOidcConfigData> findAllBySeqList(Integer... seqs) {
        return queryRepository.findAllByProviderSeqList(SsoProtocolBySeqListQuery.builder().ssoProviderSeqList(seqs == null ? null : Arrays.asList(seqs)).build());
    }

    private String column(String col, int seq) {
        return jdbc.queryForObject("SELECT CAST(" + col + " AS VARCHAR) FROM tb_sso_oidc_config WHERE sso_provider_seq = ?", String.class, seq);
    }

    private Instant dateColumn(String col, int seq) {
        OffsetDateTime value = jdbc.queryForObject("SELECT " + col + " FROM tb_sso_oidc_config WHERE sso_provider_seq = ?", OffsetDateTime.class, seq);
        return value == null ? null : value.toInstant();
    }

    @Test
    @DisplayName("읽기 — upsert 로 Y/N 저장 / findById(JPQL DTO) 6컬럼·findAllByProviderSeqList(IN·없는 seq 무시·빈/null 가드), allowIdpInitiated null(NOT NULL 위반)은 맨 끝")
    void read() {
        Instant before = Instant.now().minusSeconds(1);
        SsoOidcConfig c1 = oidc(S1, "client-1", true).build();
        assertThat(repository.upsert(c1)).isEqualTo(1);
        assertThat(c1.getRegisterUserSeq()).as("write-back 없음(W4)").isNull();
        SsoOidcConfig c2 = oidc(S2, "client-2", false).scopes(null).registerUserSeq(SUPER_ADMIN).build();
        assertThat(repository.upsert(c2)).isEqualTo(1);
        assertThat(column("allow_idp_initiated", S1)).isEqualTo("Y");
        assertThat(column("allow_idp_initiated", S2)).isEqualTo("N");
        assertThat(column("register_user_seq", S1)).as("컨텍스트 사용자").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("register_date", S1)).isNotNull().isAfter(before);
        assertThat(column("register_user_seq", S2)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(String.valueOf(ADMIN));
        assertThat(column("update_date", S1)).isNull();

        // findById — 6컬럼 매핑, Y/N → Boolean
        SsoOidcConfigData d1 = findById(S1);
        assertThat(d1.getSsoProviderSeq()).isEqualTo(S1);
        assertThat(d1.getIssuerUrl()).isEqualTo("https://idp.example.test/" + S1);
        assertThat(d1.getClientId()).isEqualTo("client-1");
        assertThat(d1.getClientSecret()).isEqualTo("secret-client-1");
        assertThat(d1.getScopes()).isEqualTo("openid email");
        assertThat(d1.getAllowIdpInitiated()).isTrue();
        SsoOidcConfigData d2 = findById(S2);
        assertThat(d2.getScopes()).isNull();
        assertThat(d2.getAllowIdpInitiated()).isFalse();
        assertThat(findById(999999)).isNull();

        // findAllByProviderSeqList
        assertThat(findAllBySeqList(S1, S2, 999999)).extracting(SsoOidcConfigData::getSsoProviderSeq).containsExactlyInAnyOrder(S1, S2);
        assertThat(findAllBySeqList(S2)).extracting(SsoOidcConfigData::getClientId).containsExactly("client-2");
        assertThat(findAllBySeqList()).isEmpty();
        assertThat(findAllBySeqList((Integer[]) null)).isEmpty();

        // allow_idp_initiated NOT NULL — null 바인딩은 DB 가 거부(MyBatis 때와 동일). PG 트랜잭션 abort 라 맨 끝
        assertThatThrownBy(() -> repository.upsert(oidc(S3, "client-3", false).allowIdpInitiated(null).build())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("upsert — 신규는 INSERT(update_* 없음), 같은 seq 재호출은 행 1개·EXCLUDED 갱신(Y/N 포함)·register_* 유지·update_* = 컨텍스트 사용자(엔티티 값 우선) / deleteByProviders·빈 목록 가드")
    void upsertAndDelete() {
        Instant before = Instant.now().minusSeconds(1);
        SsoOidcConfig created = oidc(S1, "client-1", false).build();
        assertThat(repository.upsert(created)).isEqualTo(1);
        assertThat(column("register_user_seq", S1)).as("컨텍스트 사용자").isEqualTo(String.valueOf(ADMIN));
        Instant registerDate = dateColumn("register_date", S1);
        assertThat(registerDate).isNotNull();
        assertThat(column("update_user_seq", S1)).as("신규는 update_* 없음").isNull();
        assertThat(column("update_date", S1)).isNull();

        // 재호출 — update_* 는 엔티티 값이 없으면 컨텍스트 사용자·현재 시각(인터셉터 규칙; 구 XML 은 컨버터가 안 채운 null 을 그대로 바인딩했음)
        SsoOidcConfig again = oidc(S1, "client-2", true).scopes("openid email profile").registerUserSeq(SUPER_ADMIN).build();
        assertThat(repository.upsert(again)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_sso_oidc_config WHERE sso_provider_seq = ?", Integer.class, S1)).isEqualTo(1);
        SsoOidcConfigData row = findById(S1);
        assertThat(row.getClientId()).isEqualTo("client-2");
        assertThat(row.getClientSecret()).isEqualTo("secret-client-2");
        assertThat(row.getScopes()).isEqualTo("openid email profile");
        assertThat(row.getAllowIdpInitiated()).isTrue();
        assertThat(column("allow_idp_initiated", S1)).isEqualTo("Y");
        assertThat(column("register_user_seq", S1)).as("충돌 시 register_* 는 유지").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("register_date", S1)).isEqualTo(registerDate);
        assertThat(column("update_user_seq", S1)).as("update_* = 컨텍스트 사용자").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("update_date", S1)).isNotNull().isAfter(before);

        // 엔티티에 update 감사 값이 있으면 그대로(PgRemaining 테스트와 같은 호출 꼴)
        Instant fixed = Instant.parse("2026-01-02T03:04:05Z");
        assertThat(repository.upsert(oidc(S1, "client-3", false).updateUserSeq(SUPER_ADMIN).updateDate(fixed).build())).isEqualTo(1);
        assertThat(column("update_user_seq", S1)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("update_date", S1)).isEqualTo(fixed);
        assertThat(findById(S1).getAllowIdpInitiated()).isFalse();

        // deleteByProviders
        assertThat(repository.upsert(oidc(S2, "client-x", false).build())).isEqualTo(1);
        assertThat(repository.deleteByProviders(List.of())).isZero();
        assertThat(repository.deleteByProviders(null)).isZero();
        assertThat(repository.deleteByProviders(List.of(S1, S2, 999999))).isEqualTo(2);
        assertThat(findById(S1)).isNull();
        assertThat(findById(S2)).isNull();
    }

    @Test
    @DisplayName("시큐리티 없음(스케줄러 등) — upsert 는 선언한 register_user_seq 그대로(시각은 선언 없으면 현재), 충돌 시 선언한 update_user_seq 그대로, 사용자 선언이 없으면 NOT NULL 위반")
    void auditWithoutSecurityContext() {
        SecurityContextHolder.clearContext();
        Instant before = Instant.now().minusSeconds(1);

        assertThat(repository.upsert(oidc(S1, "client-1", true).registerUserSeq(SUPER_ADMIN).build())).isEqualTo(1);
        assertThat(column("register_user_seq", S1)).as("선언 값 그대로").isEqualTo(String.valueOf(SUPER_ADMIN));
        assertThat(dateColumn("register_date", S1)).isAfter(before);

        assertThat(repository.upsert(oidc(S1, "client-1b", false).registerUserSeq(SUPER_ADMIN).updateUserSeq(SUPER_ADMIN).build())).isEqualTo(1);
        assertThat(column("update_user_seq", S1)).as("선언 값 그대로").isEqualTo(String.valueOf(SUPER_ADMIN));
        assertThat(dateColumn("update_date", S1)).isAfter(before);

        // 선언 값도 시큐리티도 없으면 register_user_seq(NOT NULL) 가 null — 충돌 행이어도 INSERT 값 검사에서 거부. PG 트랜잭션 abort 라 맨 끝
        assertThatThrownBy(() -> repository.upsert(oidc(S1, "client-1c", false).build())).isInstanceOf(DataIntegrityViolationException.class);
    }
}

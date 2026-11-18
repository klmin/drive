package com.thinkfree.drive.sso.protocol.jwt.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.jwt.entity.SsoJwtConfig;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import com.thinkfree.drive.sso.protocol.jwt.query.SsoJwtConfigQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.repository.SsoProviderRepository;
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
 * 2단계 SsoJwtConfig 이관 회귀 — 읽기({@link DefaultSsoJwtConfigQueryRepository}: findById·findByIssuer·findByIssuerAndScope(공급자 scope 조인)·findAllByProviderSeqList — 전부 JPQL DTO)·쓰기({@link DefaultSsoJwtConfigRepository}: upsert(ON CONFLICT sso_provider_seq, JdbcClient)·IN 삭제(JPQL)).
 * 단건 save·update 는 호출부가 없어 W4(10-13) 에서 삭제. 공급자 행은 {@link SsoProviderRepository#upsert} 로 만든다(findByIssuerAndScope 조인용 — tb_sso_jwt_config 에 FK 는 없음). 시드 = tenant 1, user 1 superadmin·2 admin. 트랜잭션 롤백.
 * 검증 포인트 = 감사 컬럼(DB 값 — 컨텍스트 사용자·선언 값, 인자 엔티티 write-back 없음), enum(JWKS/PUBLIC_KEY) name() 왕복, upsert 충돌 시 전체 EXCLUDED 갱신·register_* 유지·update_* = 등록 감사 값, 빈 IN 가드, 제약 위반(issuer UNIQUE·key_source_type CHECK)은 맨 끝.
 */
@DisplayName("DefaultSsoJwtConfigRepository / DefaultSsoJwtConfigQueryRepository — JPQL + JdbcClient upsert(PostgreSQL Testcontainers)")
class DefaultSsoJwtConfigRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int TENANT = 1;
    private static final String JWKS_URI = "https://idp.example.test/.well-known/jwks.json";
    private static final String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkq\n-----END PUBLIC KEY-----";

    @Autowired
    private SsoJwtConfigRepository repository;
    @Autowired
    private SsoJwtConfigQueryRepository queryRepository;
    @Autowired
    private SsoProviderRepository ssoProviderRepository;
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

    private int provider(SsoProviderScope scope, String name) {
        SsoProvider p = SsoProvider.builder()
                .tenantSeq(scope == SsoProviderScope.TENANT ? TENANT : null)
                .displayName(name).protocolType(SsoProviderProtocolType.JWT).status(SsoProviderStatus.ACTIVE).scope(scope).sortOrder(0)
                .build();
        return ssoProviderRepository.upsert(p);
    }

    private static SsoJwtConfig.SsoJwtConfigBuilder<?, ?> jwks(int seq, String issuer) {
        return SsoJwtConfig.builder().ssoProviderSeq(seq).issuer(issuer).keySourceType(KeySourceType.JWKS).jwksUri(JWKS_URI).emailClaimName("email");
    }

    private SsoJwtConfigData findById(int seq) {
        return queryRepository.findById(SsoJwtConfigQuery.Find.builder().ssoProviderSeq(seq).build());
    }

    private List<SsoJwtConfigData> findAllBySeqList(Integer... seqs) {
        return queryRepository.findAllByProviderSeqList(SsoProtocolBySeqListQuery.builder().ssoProviderSeqList(seqs == null ? null : Arrays.asList(seqs)).build());
    }

    private String column(String col, int seq) {
        return jdbc.queryForObject("SELECT CAST(" + col + " AS VARCHAR) FROM tb_sso_jwt_config WHERE sso_provider_seq = ?", String.class, seq);
    }

    private Instant dateColumn(String col, int seq) {
        OffsetDateTime value = jdbc.queryForObject("SELECT " + col + " FROM tb_sso_jwt_config WHERE sso_provider_seq = ?", OffsetDateTime.class, seq);
        return value == null ? null : value.toInstant();
    }

    @Test
    @DisplayName("읽기 — findById(JPQL DTO)·findByIssuer(대소문자 구분)·findByIssuerAndScope(공급자 scope 조인)·findAllByProviderSeqList(IN·빈 목록 가드), issuer 유니크 위반은 맨 끝")
    void read() {
        Instant before = Instant.now().minusSeconds(1);
        int t1 = provider(SsoProviderScope.TENANT, "jwt-t1");
        int t2 = provider(SsoProviderScope.TENANT, "jwt-t2");
        int g1 = provider(SsoProviderScope.GLOBAL, "jwt-g1");
        String issuerT1 = "https://idp.example.test/t1";
        String issuerT2 = "https://idp.example.test/t2";
        String issuerG1 = "https://idp.example.test/g1";

        SsoJwtConfig c1 = jwks(t1, issuerT1).audience("drive").errorRedirectUrl("https://drive.example.test/sso-error").build();
        assertThat(repository.upsert(c1)).isEqualTo(1);
        assertThat(c1.getRegisterUserSeq()).as("write-back 없음(W4)").isNull();
        SsoJwtConfig c2 = SsoJwtConfig.builder().ssoProviderSeq(t2).issuer(issuerT2).keySourceType(KeySourceType.PUBLIC_KEY).publicKey(PUBLIC_KEY).registerUserSeq(SUPER_ADMIN).build();
        assertThat(repository.upsert(c2)).isEqualTo(1);
        assertThat(repository.upsert(jwks(g1, issuerG1).build())).isEqualTo(1);
        assertThat(column("register_user_seq", t1)).as("컨텍스트 사용자").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("register_date", t1)).isNotNull().isAfter(before);
        assertThat(column("register_user_seq", t2)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(String.valueOf(ADMIN));
        assertThat(column("update_date", t1)).isNull();

        // findById — 8컬럼 매핑, enum 왕복
        SsoJwtConfigData d1 = findById(t1);
        assertThat(d1.getSsoProviderSeq()).isEqualTo(t1);
        assertThat(d1.getIssuer()).isEqualTo(issuerT1);
        assertThat(d1.getAudience()).isEqualTo("drive");
        assertThat(d1.getKeySourceType()).isEqualTo(KeySourceType.JWKS);
        assertThat(d1.getPublicKey()).isNull();
        assertThat(d1.getJwksUri()).isEqualTo(JWKS_URI);
        assertThat(d1.getEmailClaimName()).isEqualTo("email");
        assertThat(d1.getErrorRedirectUrl()).isEqualTo("https://drive.example.test/sso-error");
        SsoJwtConfigData d2 = findById(t2);
        assertThat(d2.getKeySourceType()).isEqualTo(KeySourceType.PUBLIC_KEY);
        assertThat(d2.getPublicKey()).isEqualTo(PUBLIC_KEY);
        assertThat(d2.getJwksUri()).isNull();
        assertThat(d2.getAudience()).isNull();
        assertThat(d2.getEmailClaimName()).isNull();
        assertThat(findById(999999)).isNull();

        // findByIssuer
        assertThat(queryRepository.findByIssuer(issuerT1).getSsoProviderSeq()).isEqualTo(t1);
        assertThat(queryRepository.findByIssuer(issuerG1).getSsoProviderSeq()).isEqualTo(g1);
        assertThat(queryRepository.findByIssuer("https://idp.example.test/none")).isNull();
        assertThat(queryRepository.findByIssuer(issuerT1.toUpperCase())).as("대소문자 구분(deterministic 콜레이션)").isNull();

        // findByIssuerAndScope — tb_sso_provider.scope INNER JOIN
        assertThat(queryRepository.findByIssuerAndScope(issuerT1, SsoProviderScope.TENANT).getSsoProviderSeq()).isEqualTo(t1);
        assertThat(queryRepository.findByIssuerAndScope(issuerT1, SsoProviderScope.GLOBAL)).as("TENANT 공급자의 issuer 를 GLOBAL 로").isNull();
        assertThat(queryRepository.findByIssuerAndScope(issuerG1, SsoProviderScope.GLOBAL).getSsoProviderSeq()).isEqualTo(g1);
        assertThat(queryRepository.findByIssuerAndScope(issuerG1, SsoProviderScope.TENANT)).isNull();
        assertThat(queryRepository.findByIssuerAndScope("https://idp.example.test/none", SsoProviderScope.TENANT)).isNull();

        // findAllByProviderSeqList — IN, 없는 seq 무시, 빈/null 목록은 SQL 없이 빈 목록
        assertThat(findAllBySeqList(t1, g1, 999999)).extracting(SsoJwtConfigData::getSsoProviderSeq).containsExactlyInAnyOrder(t1, g1);
        assertThat(findAllBySeqList(t2)).extracting(SsoJwtConfigData::getIssuer).containsExactly(issuerT2);
        assertThat(findAllBySeqList()).isEmpty();
        assertThat(findAllBySeqList((Integer[]) null)).isEmpty();

        // uniq_sso_jwt_issuer 위반(다른 공급자 seq 로 같은 issuer)은 PG 트랜잭션을 abort 시키므로 맨 끝
        assertThatThrownBy(() -> repository.upsert(jwks(t1 + 1000, issuerT2).build())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("upsert — 신규는 INSERT(update_* 없음), 같은 seq 재호출은 행 1개·값 갱신·register_* 유지·update_* = 바인딩한 등록 감사 값 / deleteByProviders·빈 목록 가드, key_source_type 체크 제약 위반은 맨 끝")
    void upsertAndDelete() {
        int t1 = provider(SsoProviderScope.TENANT, "up-1");
        int t2 = provider(SsoProviderScope.TENANT, "up-2");
        String issuer = "https://idp.example.test/up1";

        SsoJwtConfig created = jwks(t1, issuer).build();
        assertThat(repository.upsert(created)).isEqualTo(1);
        assertThat(column("register_user_seq", t1)).as("컨텍스트 사용자").isEqualTo(String.valueOf(ADMIN));
        Instant registerDate = dateColumn("register_date", t1);
        assertThat(registerDate).isNotNull();
        assertThat(column("update_user_seq", t1)).as("신규는 update_* 없음").isNull();
        assertThat(column("update_date", t1)).isNull();

        SsoJwtConfig again = jwks(t1, issuer + "-v2").audience("drive").emailClaimName("mail").errorRedirectUrl("https://drive.example.test/e").registerUserSeq(SUPER_ADMIN).build();
        assertThat(repository.upsert(again)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_sso_jwt_config WHERE sso_provider_seq = ?", Integer.class, t1)).isEqualTo(1);
        SsoJwtConfigData row = findById(t1);
        assertThat(row.getIssuer()).isEqualTo(issuer + "-v2");
        assertThat(row.getAudience()).isEqualTo("drive");
        assertThat(row.getEmailClaimName()).isEqualTo("mail");
        assertThat(row.getErrorRedirectUrl()).isEqualTo("https://drive.example.test/e");
        assertThat(row.getKeySourceType()).isEqualTo(KeySourceType.JWKS);
        assertThat(column("register_user_seq", t1)).as("충돌 시 register_* 는 유지").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("register_date", t1)).isEqualTo(registerDate);
        assertThat(column("update_user_seq", t1)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(String.valueOf(ADMIN));
        assertThat(dateColumn("update_date", t1)).isNotNull().isAfterOrEqualTo(registerDate);
        assertThat(queryRepository.findByIssuer(issuer)).isNull();
        assertThat(queryRepository.findByIssuer(issuer + "-v2").getSsoProviderSeq()).isEqualTo(t1);

        // 키 방식 전환(JWKS → PUBLIC_KEY) 도 EXCLUDED 로 전체 갱신
        assertThat(repository.upsert(SsoJwtConfig.builder().ssoProviderSeq(t1).issuer(issuer + "-v2").keySourceType(KeySourceType.PUBLIC_KEY).publicKey(PUBLIC_KEY).build())).isEqualTo(1);
        SsoJwtConfigData switched = findById(t1);
        assertThat(switched.getKeySourceType()).isEqualTo(KeySourceType.PUBLIC_KEY);
        assertThat(switched.getPublicKey()).isEqualTo(PUBLIC_KEY);
        assertThat(switched.getJwksUri()).isNull();
        assertThat(switched.getAudience()).as("EXCLUDED.audience = null").isNull();

        // deleteByProviders
        assertThat(repository.upsert(jwks(t2, "https://idp.example.test/up2").build())).isEqualTo(1);
        assertThat(repository.deleteByProviders(List.of())).isZero();
        assertThat(repository.deleteByProviders(null)).isZero();
        assertThat(repository.deleteByProviders(List.of(t1, t2, 999999))).isEqualTo(2);
        assertThat(findById(t1)).isNull();
        assertThat(findById(t2)).isNull();

        // check_key_source_type(JWKS 는 jwks_uri 필수·public_key 금지) 위반은 맨 끝
        assertThatThrownBy(() -> repository.upsert(SsoJwtConfig.builder().ssoProviderSeq(t1).issuer(issuer).keySourceType(KeySourceType.JWKS).publicKey(PUBLIC_KEY).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("시큐리티 없음(스케줄러 등) — upsert 는 선언한 register_user_seq·register_date 그대로(JdbcAudit), 충돌 시 update_* = 그 등록 감사 값(시각은 선언 없으면 현재), 사용자 선언이 없으면 NOT NULL 위반")
    void auditWithoutSecurityContext() {
        int t1 = provider(SsoProviderScope.TENANT, "nosec-1");
        String issuer = "https://idp.example.test/nosec1";
        SecurityContextHolder.clearContext();
        Instant before = Instant.now().minusSeconds(1);

        SsoJwtConfig created = jwks(t1, issuer).registerUserSeq(SUPER_ADMIN).registerDate(Instant.parse("2020-01-01T00:00:00Z")).build();
        assertThat(repository.upsert(created)).isEqualTo(1);
        assertThat(column("register_user_seq", t1)).as("선언 값 그대로").isEqualTo(String.valueOf(SUPER_ADMIN));
        assertThat(dateColumn("register_date", t1)).as("선언 값 그대로").isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(repository.upsert(jwks(t1, issuer + "/v2").registerUserSeq(SUPER_ADMIN).build())).isEqualTo(1);
        assertThat(column("update_user_seq", t1)).as("선언 값 그대로").isEqualTo(String.valueOf(SUPER_ADMIN));
        assertThat(dateColumn("update_date", t1)).as("선언 시각 없으면 현재").isAfter(before);
        assertThat(dateColumn("register_date", t1)).as("register_* 유지").isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));

        // 선언 값도 시큐리티도 없으면 register_user_seq(NOT NULL) 가 null — 충돌 행이어도 INSERT 값 검사에서 거부. PG 트랜잭션 abort 라 맨 끝
        assertThatThrownBy(() -> repository.upsert(jwks(t1, issuer + "/v3").build())).isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.thinkfree.drive.basedomain.repository;

import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.entity.BaseDomain;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 BaseDomain 이관(JdbcClient) 회귀. 시드 없음(tb_base_domain 빈 테이블), 트랜잭션 롤백.
 * 검증 포인트 = 싱글턴 PK(1) upsert 의 "INSERT 는 register_* 만, 충돌 시 base_domain + update_* 갱신·register 보존", 엔티티 seq 무시(항상 1),
 * 감사 컬럼(JdbcAudit: 엔티티 값 우선 → SecurityContext 사용자 → 없으면 NOT NULL 위반 — DB 직접 확인), 조회는 Query 리포 DTO(JPQL 생성자 식),
 * DB CHECK(소문자) 그대로 전파.
 */
@DisplayName("DefaultBaseDomainRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultBaseDomainRepositoryTest extends RepositoryTest {

    private static final int SINGLE_SEQ = 1;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private BaseDomainRepository repository;
    @Autowired
    private BaseDomainQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** 서비스(converter)가 넘기는 형태 — seq + 도메인만, 감사 필드 없음. */
    private static BaseDomain domain(Integer seq, String baseDomain) {
        return BaseDomain.builder().baseDomainSeq(seq).baseDomain(baseDomain).build();
    }

    /** 감사 컬럼은 DTO 에 없으므로 DB 에서 직접 */
    private Map<String, Object> audit() {
        return jdbc.queryForMap("SELECT register_user_seq, register_date, update_user_seq, update_date FROM tb_base_domain WHERE base_domain_seq = ?", SINGLE_SEQ);
    }

    private static Instant instant(Object ts) {
        return ts == null ? null : ((Timestamp) ts).toInstant();
    }

    private int rows() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tb_base_domain", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("빈 테이블 — findCurrent·findByBaseDomainSeq(1)·findByBaseDomainSeq(null) 전부 null(Optional 빈 결과, 예외 없음)")
    void find_onEmptyTable_returnsNull() {
        assertThat(rows()).isZero();
        assertThat(queryRepository.findCurrent()).isNull();
        assertThat(queryRepository.findByBaseDomainSeq(SINGLE_SEQ)).isNull();
        assertThat(queryRepository.findByBaseDomainSeq(null)).as("nullable Integer 는 Types.INTEGER 로 바인딩").isNull();
    }

    @Test
    @DisplayName("upsert 2회 — 행 1개, base_domain 갱신, register_* 는 첫 등록자 보존, update_* 는 첫 INSERT 때 NULL → 두 번째 사용자")
    void upsert_twice_singletonRowUpdated() {
        loginAs(SUPER_ADMIN);
        repository.upsert(domain(SINGLE_SEQ, "drive.thinkfree.com"));

        BaseDomainData first = queryRepository.findCurrent();
        assertThat(first).isNotNull();
        assertThat(first.getBaseDomainSeq()).isEqualTo(SINGLE_SEQ);
        assertThat(first.getBaseDomain()).isEqualTo("drive.thinkfree.com");
        Map<String, Object> firstAudit = audit();
        assertThat(firstAudit.get("register_user_seq")).isEqualTo(SUPER_ADMIN);
        assertThat(firstAudit.get("register_date")).isNotNull();
        assertThat(firstAudit.get("update_user_seq")).as("INSERT 는 register_* 만 채움").isNull();
        assertThat(firstAudit.get("update_date")).isNull();

        loginAs(ADMIN);
        repository.upsert(domain(SINGLE_SEQ, "drive.example.com"));

        assertThat(rows()).isEqualTo(1);
        assertThat(queryRepository.findCurrent().getBaseDomain()).as("같은 트랜잭션의 upsert 뒤에도 DB 현재 값").isEqualTo("drive.example.com");
        Map<String, Object> secondAudit = audit();
        assertThat(secondAudit.get("register_user_seq")).as("register 보존").isEqualTo(SUPER_ADMIN);
        assertThat(secondAudit.get("register_date")).isEqualTo(firstAudit.get("register_date"));
        assertThat(secondAudit.get("update_user_seq")).as("update 는 현재 사용자").isEqualTo(ADMIN);
        assertThat(instant(secondAudit.get("update_date"))).isNotNull().isAfterOrEqualTo(instant(firstAudit.get("register_date")));

        assertThat(queryRepository.findByBaseDomainSeq(SINGLE_SEQ).getBaseDomain()).isEqualTo("drive.example.com");
    }

    @Test
    @DisplayName("엔티티 seq 무시 — seq 5 로 넘겨도 항상 1 행(XML 의 고정 1 과 동일), CHECK singleton 위반 없음")
    void upsert_ignoresEntitySeq() {
        loginAs(SUPER_ADMIN);
        repository.upsert(domain(5, "drive.thinkfree.com"));

        assertThat(rows()).isEqualTo(1);
        assertThat(queryRepository.findCurrent().getBaseDomainSeq()).isEqualTo(SINGLE_SEQ);
        assertThat(queryRepository.findByBaseDomainSeq(5)).isNull();
    }

    @Test
    @DisplayName("감사 컬럼 — 엔티티 값 우선(초 절삭 없이 그대로), 충돌 갱신 때도 엔티티 register 값이 update_* 로")
    void upsert_entityAuditValuesWin() {
        Instant fixed = Instant.parse("2026-01-02T03:04:05.123456Z");
        repository.upsert(BaseDomain.builder().baseDomainSeq(SINGLE_SEQ).baseDomain("a.example.com")
                .registerUserSeq(ADMIN).registerDate(fixed).build());

        Map<String, Object> d = audit();
        assertThat(d.get("register_user_seq")).isEqualTo(ADMIN);
        assertThat(instant(d.get("register_date"))).isEqualTo(fixed);

        Instant later = fixed.plusSeconds(3600);
        repository.upsert(BaseDomain.builder().baseDomainSeq(SINGLE_SEQ).baseDomain("b.example.com")
                .registerUserSeq(SUPER_ADMIN).registerDate(later).build());

        Map<String, Object> u = audit();
        assertThat(u.get("register_user_seq")).isEqualTo(ADMIN);
        assertThat(instant(u.get("register_date"))).isEqualTo(fixed);
        assertThat(u.get("update_user_seq")).isEqualTo(SUPER_ADMIN);
        assertThat(instant(u.get("update_date"))).isEqualTo(later);
    }

    @Test
    @DisplayName("감사 자동 채움 — 날짜 없으면 현재 시각(초 절삭); SecurityContext 없으면 사용자 null → NOT NULL 위반(인터셉터 때와 동일)")
    void audit_fallbacks() {
        loginAs(ADMIN);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.upsert(domain(SINGLE_SEQ, "drive.thinkfree.com"));

        Map<String, Object> d = audit();
        assertThat(instant(d.get("register_date"))).isAfterOrEqualTo(before);
        assertThat(instant(d.get("register_date")).getNano()).as("초 단위 절삭").isZero();
        assertThat(d.get("register_user_seq")).isEqualTo(ADMIN);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> repository.upsert(domain(SINGLE_SEQ, "other.example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("scheme·port — 저장·조회되고, 다시 upsert 하면 null 로도 덮어쓴다(입력에서 지운 스킴·포트가 남지 않음)")
    void upsert_schemeAndPort() {
        loginAs(SUPER_ADMIN);
        repository.upsert(BaseDomain.builder().baseDomainSeq(SINGLE_SEQ).baseDomain("192.168.0.10").scheme("http").port(8080).build());

        BaseDomainData saved = queryRepository.findCurrent();
        assertThat(saved.getScheme()).isEqualTo("http");
        assertThat(saved.getPort()).isEqualTo(8080);
        assertThat(saved.getAddress()).isEqualTo("http://192.168.0.10:8080");

        repository.upsert(domain(SINGLE_SEQ, "drive.example.com"));

        BaseDomainData cleared = queryRepository.findCurrent();
        assertThat(cleared.getScheme()).isNull();
        assertThat(cleared.getPort()).isNull();
        assertThat(cleared.getAddress()).isEqualTo("drive.example.com");
    }

    @Test
    @DisplayName("DB CHECK 전파 — scheme 은 http·https 만, port 는 1~65535 만")
    void upsert_schemePortRejectedByCheck() {
        loginAs(SUPER_ADMIN);
        assertThatThrownBy(() -> repository.upsert(BaseDomain.builder().baseDomainSeq(SINGLE_SEQ).baseDomain("drive.example.com").scheme("ftp").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB CHECK 전파 — port 범위 밖(0)은 chk_base_domain_port 위반")
    void upsert_portOutOfRangeRejectedByCheck() {
        loginAs(SUPER_ADMIN);
        assertThatThrownBy(() -> repository.upsert(BaseDomain.builder().baseDomainSeq(SINGLE_SEQ).baseDomain("drive.example.com").port(0).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB CHECK 전파 — 대문자 도메인은 chk_base_domain_lowercase 위반(서비스 검증과 별개로 DB 가 마지막 방어)")
    void upsert_uppercaseRejectedByCheck() {
        loginAs(SUPER_ADMIN);
        assertThatThrownBy(() -> repository.upsert(domain(SINGLE_SEQ, "Drive.Example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

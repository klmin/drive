package com.thinkfree.drive.smtpconfig.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.entity.SmtpConfig;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
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
 * SmtpConfig 리포 회귀 — 읽기({@link DefaultSmtpConfigQueryRepository}: 단건 2 — JPQL 생성자 식 DTO, PK 단건은 테넌트 Java 판정)·쓰기({@link DefaultSmtpConfigRepository}: save, findById 관리 엔티티(테넌트 판정은 Service) → 변경 메서드 → 변경 감지).
 * tb_smtp_config 에 FK 없음(tenant_seq·user_seq 임의 값). 트랜잭션 롤백 — 변경 감지 반영은 {@code entityManager.flush()} 후 JdbcTemplate 으로 DB 값 확인.
 * 검증 포인트 = identity 키, enum 2종 `@Enumerated(STRING)`·{@code SmtpPasswordConverter}·Instant 왕복, register_user_seq 는 선언 값·register_date 는 Auditing, **update_date 는 insertable=false → INSERT 때 DB DEFAULT**,
 * 수정은 10컬럼 + 수정 감사(Auditing — 시큐리티 사용자·현재 시각)만 바뀌고 register_* 유지, 다른 테넌트·없는 행은 null, 값이 같으면 UPDATE 없음, 테넌트당 2건이면 findByTenant 가 IncorrectResultSize.
 */
@DisplayName("DefaultSmtpConfigRepository / DefaultSmtpConfigQueryRepository — JPA(PostgreSQL Testcontainers)")
class DefaultSmtpConfigRepositoryTest extends RepositoryTest {

    private static final int TENANT_A = 910001;
    private static final int TENANT_B = 910002;
    private static final int ADMIN_A = 920001;
    private static final int ADMIN_B = 920002;

    @Autowired
    private SmtpConfigRepository repository;
    @Autowired
    private SmtpConfigQueryRepository queryRepository;
    @Autowired
    private EntityManager entityManager;
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

    private SmtpConfig insert(int tenantSeq, String clientName, SmtpPassword encPwd) {
        SmtpConfig e = SmtpConfig.create(tenantSeq, ADMIN_A);
        e.changeSettings(clientName, "smtp.example.com", 587, EncryptionType.TLS, AuthType.IDPW, "mailer@example.com",
                encPwd, null, "noreply@example.com", "qa@example.com");
        repository.save(e);
        return e;
    }

    private Map<String, Object> row(int smtpConfigSeq) {
        return jdbc.queryForMap("SELECT client_name, host, port, encryption_type, auth_type, username, enc_pwd, oauth_info, from_address, test_receiver_email, "
                + "register_user_seq, register_date, update_user_seq, update_date FROM tb_smtp_config WHERE smtp_config_seq = ?", smtpConfigSeq);
    }

    @Test
    @DisplayName("save(identity·enum·비밀번호 컨버터·register_user_seq 선언 값·register_date Auditing·update_date DB DEFAULT) → findBySmtpConfigSeq(다른 테넌트·없음 null) / findByTenant(2건이면 IncorrectResultSize)")
    void saveAndFind() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1);
        SmtpConfig saved = insert(TENANT_A, "pg-smtp-a", SmtpPassword.of("enc-a"));
        insert(TENANT_B, "pg-smtp-b", null);
        assertThat(saved.getSmtpConfigSeq()).as("IDENTITY — save 순간 INSERT·id 채움").isNotNull().isPositive();

        SmtpConfigData a = queryRepository.findByTenant(TENANT_A);
        assertThat(a).isNotNull();
        assertThat(a.getSmtpConfigSeq()).isEqualTo(saved.getSmtpConfigSeq());
        assertThat(a.getTenantSeq()).isEqualTo(TENANT_A);
        assertThat(a.getClientName()).isEqualTo("pg-smtp-a");
        assertThat(a.getHost()).isEqualTo("smtp.example.com");
        assertThat(a.getPort()).isEqualTo(587);
        assertThat(a.getEncryptionType()).isEqualTo(EncryptionType.TLS);
        assertThat(a.getAuthType()).isEqualTo(AuthType.IDPW);
        assertThat(a.getUsername()).isEqualTo("mailer@example.com");
        assertThat(a.getEncPwd().getValue()).isEqualTo("enc-a");
        assertThat(a.getOauthInfo()).isNull();
        assertThat(a.getFromAddress()).isEqualTo("noreply@example.com");
        assertThat(a.getTestReceiverEmail()).isEqualTo("qa@example.com");
        assertThat(a.getRegisterUserSeq()).as("시큐리티 없으면 선언 값").isEqualTo(ADMIN_A);
        assertThat(a.getRegisterDate()).as("Auditing").isNotNull().isAfterOrEqualTo(before);
        assertThat(a.getUpdateUserSeq()).isNull();
        assertThat(a.getUpdateDate()).as("insertable=false → DB DEFAULT CURRENT_TIMESTAMP").isNotNull().isAfterOrEqualTo(before);

        Map<String, Object> dbA = row(a.getSmtpConfigSeq());
        assertThat(dbA.get("encryption_type")).isEqualTo("TLS");
        assertThat(dbA.get("auth_type")).isEqualTo("IDPW");
        assertThat(dbA.get("enc_pwd")).isEqualTo("enc-a");
        assertThat(dbA.get("register_user_seq")).isEqualTo(ADMIN_A);
        assertThat(dbA.get("update_user_seq")).isNull();

        SmtpConfigData b = queryRepository.findByTenant(TENANT_B);
        assertThat(b.getEncPwd()).as("DB NULL → null(Hibernate 는 NULL 에 컨버터를 안 부름)").isNull();
        assertThat(row(b.getSmtpConfigSeq()).get("enc_pwd")).isNull();

        // PK 단건 — 테넌트가 다르면 없음과 같음
        assertThat(queryRepository.findBySmtpConfigSeq(a.getSmtpConfigSeq(), TENANT_A).getClientName()).isEqualTo("pg-smtp-a");
        assertThat(queryRepository.findBySmtpConfigSeq(a.getSmtpConfigSeq(), TENANT_B)).isNull();
        assertThat(queryRepository.findBySmtpConfigSeq(999999, TENANT_A)).isNull();
        assertThat(queryRepository.findBySmtpConfigSeq(null, TENANT_A)).isNull();
        assertThat(queryRepository.findBySmtpConfigSeq(a.getSmtpConfigSeq(), null)).isNull();
        assertThat(queryRepository.findByTenant(999999)).isNull();

        // 테넌트당 2건 → 단건 계약 위반(구 selectOne TooManyResults 동형)
        insert(TENANT_A, "pg-smtp-a2", SmtpPassword.of("enc-a2"));
        assertThatThrownBy(() -> queryRepository.findByTenant(TENANT_A)).isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }

    @Test
    @DisplayName("findById(관리 엔티티 — 테넌트 판정은 Service) → changeSettings → flush: 10컬럼 + update_user_seq(시큐리티 사용자)·update_date(현재 시각) 갱신, register_* 유지, 없는 행은 null")
    void findById_changeSettings_dirtyChecking() {
        int seqA = insert(TENANT_A, "pg-smtp-a", SmtpPassword.of("enc-a")).getSmtpConfigSeq();
        int seqB = insert(TENANT_B, "pg-smtp-b", SmtpPassword.of("enc-b")).getSmtpConfigSeq();
        entityManager.flush();
        entityManager.clear();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(repository.findById(999999)).isNull();
        assertThat(repository.findById(seqB).getTenantSeq()).as("테넌트 조건 없음 — 판정은 Service").isEqualTo(TENANT_B);

        loginAs(ADMIN_B);
        SmtpConfig managed = repository.findById(seqA);
        assertThat(entityManager.contains(managed)).as("관리 엔티티").isTrue();
        managed.changeSettings("pg-smtp-a-new", "smtp2.example.com", 465, EncryptionType.SSL, AuthType.OAUTH, "oauth@example.com",
                SmtpPassword.of("enc-new"), "{\"clientId\":\"x\"}", "from2@example.com", "qa2@example.com");
        assertThat(row(seqA).get("client_name")).as("flush 전 — 메모리만 바뀜").isEqualTo("pg-smtp-a");

        entityManager.flush();   // 커밋 때 일어날 변경 감지 UPDATE 를 테스트 트랜잭션 안에서 앞당김
        Map<String, Object> db = row(seqA);
        assertThat(db.get("client_name")).isEqualTo("pg-smtp-a-new");
        assertThat(db.get("host")).isEqualTo("smtp2.example.com");
        assertThat(db.get("port")).isEqualTo(465);
        assertThat(db.get("encryption_type")).isEqualTo("SSL");
        assertThat(db.get("auth_type")).isEqualTo("OAUTH");
        assertThat(db.get("username")).isEqualTo("oauth@example.com");
        assertThat(db.get("enc_pwd")).isEqualTo("enc-new");
        assertThat(db.get("oauth_info")).isEqualTo("{\"clientId\":\"x\"}");
        assertThat(db.get("from_address")).isEqualTo("from2@example.com");
        assertThat(db.get("test_receiver_email")).isEqualTo("qa2@example.com");
        assertThat(db.get("update_user_seq")).as("Auditing — 시큐리티 사용자").isEqualTo(ADMIN_B);
        assertThat(((Timestamp) db.get("update_date")).toInstant()).as("Auditing — 현재 시각").isAfterOrEqualTo(before);
        assertThat(db.get("register_user_seq")).as("register_* 유지").isEqualTo(ADMIN_A);

        // 다른 테넌트 행 무영향
        Map<String, Object> dbB = row(seqB);
        assertThat(dbB.get("client_name")).isEqualTo("pg-smtp-b");
        assertThat(dbB.get("update_user_seq")).isNull();
    }

    @Test
    @DisplayName("값이 전부 같으면 변경 감지 UPDATE 없음 — SmtpPassword 는 값 비교(equals), 새 인스턴스여도 같은 값이면 변경 아님")
    void changeSettings_sameValues_noUpdate() {
        int seq = insert(TENANT_A, "pg-smtp-a", SmtpPassword.of("enc-a")).getSmtpConfigSeq();
        entityManager.flush();
        entityManager.clear();
        Object updateDateBefore = row(seq).get("update_date");

        loginAs(ADMIN_B);
        SmtpConfig managed = repository.findById(seq);
        managed.changeSettings("pg-smtp-a", "smtp.example.com", 587, EncryptionType.TLS, AuthType.IDPW, "mailer@example.com",
                SmtpPassword.of("enc-a"), null, "noreply@example.com", "qa@example.com");
        entityManager.flush();

        Map<String, Object> db = row(seq);
        assertThat(db.get("update_user_seq")).as("UPDATE 가 안 나가 수정자 그대로").isNull();
        assertThat(db.get("update_date")).isEqualTo(updateDateBefore);
    }
}

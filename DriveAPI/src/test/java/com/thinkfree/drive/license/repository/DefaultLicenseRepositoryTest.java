package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * License 어노테이션 매퍼 2개 이관 회귀 — {@link DefaultLicenseRepository}(JdbcClient upsert)·{@link DefaultLicenseQueryRepository}(JPA 읽기 3 + JdbcClient DB 시각)·{@link DefaultSeatLicenseRepository}(JPA count).
 * tb_license 는 FK 없음(tenant_seq 임의 값, UNIQUE tenant_seq). 트랜잭션 롤백.
 * 검증 포인트 = upsert INSERT(1, created_at/updated_at NOW()) → 충돌 UPDATE(1, data 교체·updated_at 갱신·created_at 유지), findLicense Map 4키(byte[]·Instant)·없는 테넌트 null,
 * findUpdatedAt 과 Map 의 updated_at 동등(서비스 캐시 check 3), 등록 테넌트 목록, DB 시각 ≈ now, ACTIVE 사용자 수(INACTIVE·다른 테넌트 제외).
 */
@DisplayName("DefaultLicenseRepository / DefaultLicenseQueryRepository / DefaultSeatLicenseRepository — JdbcClient upsert + JPA(PostgreSQL Testcontainers)")
class DefaultLicenseRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int TENANT_A = 930001;
    private static final int TENANT_B = 930002;

    @Autowired
    private LicenseRepository repository;
    @Autowired
    private LicenseQueryRepository queryRepository;
    @Autowired
    private SeatLicenseRepository seatRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private int user(String userId, Integer tenantSeq, UserStatus status) {
        User u = User.createUser(userId, tenantSeq, null, SignupType.LOCAL, userId, status, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u.getUserSeq();
    }

    @Test
    @DisplayName("upsert — INSERT 1 → 같은 테넌트 2회 = UPDATE 1(data 교체·updated_at 갱신·created_at 유지), findLicense Map·findUpdatedAt·목록")
    void upsert_thenRead() {
        assertThat(queryRepository.findLicense(TENANT_A)).isNull();
        assertThat(queryRepository.findUpdatedAt(TENANT_A)).isNull();
        assertThat(queryRepository.findAllLicensedTenantSeqs()).doesNotContain(TENANT_A, TENANT_B);

        assertThat(repository.upsertLicense(TENANT_A, bytes("blob-v1"))).isEqualTo(1);
        assertThat(repository.upsertLicense(TENANT_B, bytes("blob-b"))).isEqualTo(1);

        Map<String, Object> first = queryRepository.findLicense(TENANT_A);
        assertThat(first).containsOnlyKeys(Constants.DB.FIELD.TENANT_SEQ, Constants.DB.FIELD.DATA, Constants.DB.FIELD.CREATED_AT, Constants.DB.FIELD.UPDATED_AT);
        assertThat(first.get(Constants.DB.FIELD.TENANT_SEQ)).isEqualTo(TENANT_A);
        assertThat((byte[]) first.get(Constants.DB.FIELD.DATA)).isEqualTo(bytes("blob-v1"));
        assertThat(first.get(Constants.DB.FIELD.CREATED_AT)).isInstanceOf(Instant.class);
        Instant createdAt = (Instant) first.get(Constants.DB.FIELD.CREATED_AT);
        Instant updatedAt1 = (Instant) first.get(Constants.DB.FIELD.UPDATED_AT);
        assertThat(createdAt).isCloseTo(Instant.now(), within(60, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(queryRepository.findUpdatedAt(TENANT_A)).as("서비스 캐시 check 3 의 equals 비교").isEqualTo(updatedAt1);
        assertThat(queryRepository.findAllLicensedTenantSeqs()).contains(TENANT_A, TENANT_B);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_license WHERE tenant_seq = ?", Integer.class, TENANT_A)).isEqualTo(1);

        // NOW() 는 트랜잭션 시작 시각(같은 @Transactional 테스트 안에서는 두 upsert 가 같은 값) → 하루 전으로 되돌려 두고 갱신을 본다
        jdbc.update("UPDATE tb_license SET updated_at = updated_at - INTERVAL '1 day' WHERE tenant_seq = ?", TENANT_A);
        Instant backdated = queryRepository.findUpdatedAt(TENANT_A);
        assertThat(backdated).isBefore(updatedAt1);
        assertThat(repository.upsertLicense(TENANT_A, bytes("blob-v2"))).as("ON CONFLICT DO UPDATE 도 1").isEqualTo(1);
        Map<String, Object> second = queryRepository.findLicense(TENANT_A);
        assertThat((byte[]) second.get(Constants.DB.FIELD.DATA)).isEqualTo(bytes("blob-v2"));
        assertThat(second.get(Constants.DB.FIELD.CREATED_AT)).isEqualTo(createdAt);
        assertThat((Instant) second.get(Constants.DB.FIELD.UPDATED_AT)).isAfter(backdated);
        assertThat(queryRepository.findUpdatedAt(TENANT_A)).isEqualTo(second.get(Constants.DB.FIELD.UPDATED_AT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_license WHERE tenant_seq = ?", Integer.class, TENANT_A)).isEqualTo(1);
        assertThat((byte[]) queryRepository.findLicense(TENANT_B).get(Constants.DB.FIELD.DATA)).as("테넌트 격리").isEqualTo(bytes("blob-b"));

        jdbc.update("UPDATE tb_license SET updated_at = NULL WHERE tenant_seq = ?", TENANT_B);
        assertThat(queryRepository.findUpdatedAt(TENANT_B)).as("행은 있으나 updated_at NULL → null").isNull();
    }

    @Test
    @DisplayName("getServerTimeMS — DB clock_timestamp epoch millis ≈ JVM now")
    void serverTime() {
        Long ms = queryRepository.getServerTimeMS();
        assertThat(ms).isNotNull();
        assertThat(Math.abs(ms - System.currentTimeMillis())).isLessThan(60_000L);
    }

    @Test
    @DisplayName("countActiveUsers — 테넌트의 ACTIVE 만(INACTIVE·다른 테넌트 제외), 사용자 없는 테넌트 0")
    void countActiveUsers() {
        int tenant = 930010;
        assertThat(seatRepository.countActiveUsers(tenant)).isEqualTo(0);
        user("lic-a1", tenant, UserStatus.ACTIVE);
        user("lic-a2", tenant, UserStatus.ACTIVE);
        user("lic-i1", tenant, UserStatus.INACTIVE);
        user("lic-o1", 930011, UserStatus.ACTIVE);
        assertThat(seatRepository.countActiveUsers(tenant)).isEqualTo(2);
        assertThat(seatRepository.countActiveUsers(930011)).isEqualTo(1);
    }
}

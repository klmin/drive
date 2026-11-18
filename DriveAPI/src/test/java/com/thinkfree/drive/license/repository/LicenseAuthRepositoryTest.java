package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.license.data.LicenseAuthResultData;
import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.data.LicenseVersion;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import com.thinkfree.drive.systemconfig.repository.SystemConfigQueryRepository;
import com.thinkfree.drive.systemconfig.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 온라인 인증 컬럼 쓰기(JdbcClient upsert·JPQL @Modifying)와 읽기(JPQL 생성자 식)를 Testcontainers PG 에서 직접 실행한다.
 * tb_license 는 FK 없음(tenant_seq 임의 값). 트랜잭션 롤백.
 * 검증 포인트 = 인증 결과 기록이 updated_at 을 건드리지 않는지, 재등록 시 인증 컬럼이 비워지는지,
 * null 로 넘긴 값이 기존 값을 지키는지. 시드(initialize.sql): user 1(superadmin).
 */
@DisplayName("라이선스 온라인 인증 컬럼 — JdbcClient upsert + JPQL(PostgreSQL Testcontainers)")
class LicenseAuthRepositoryTest extends RepositoryTest {

    private static final int TENANT_A = 940001;
    private static final int TENANT_B = 940002;
    private static final int SUPER_ADMIN_SEQ = 1;
    private static final byte[] BLOB = "TR.license".getBytes(StandardCharsets.UTF_8);

    @Autowired LicenseRepository licenseRepository;
    @Autowired LicenseQueryRepository licenseQueryRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired SystemConfigQueryRepository systemConfigQueryRepository;
    @Autowired JdbcTemplate jdbc;

    private static Instant at(int day, int hour) {
        return Instant.parse(String.format("2026-10-%02dT%02d:00:00Z", day, hour));
    }

    private Map<String, Object> authRow(int tenantSeq) {
        return jdbc.queryForMap("SELECT auth_status, auth_fail_count, auth_checked_at, auth_success_at, auth_init_at, auth_signed_data, updated_at "
                + "FROM tb_license WHERE tenant_seq = ?", tenantSeq);
    }

    private Instant instant(int tenantSeq, String column) {
        OffsetDateTime value = jdbc.queryForObject("SELECT " + column + " FROM tb_license WHERE tenant_seq = ?", OffsetDateTime.class, tenantSeq);
        return value == null ? null : value.toInstant();
    }

    @Test
    @DisplayName("인증 결과 기록 → auth_* 만 바뀌고 updated_at 은 그대로")
    void updateAuthResult_keepsUpdatedAt() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        Instant before = licenseQueryRepository.findUpdatedAt(TENANT_A);

        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "SUCCESS", 0, at(13, 3), at(13, 3), at(1, 9), "signed.jwt"));

        Map<String, Object> row = authRow(TENANT_A);
        assertThat(row.get("auth_status")).isEqualTo("SUCCESS");
        assertThat(row.get("auth_fail_count")).isEqualTo(0);
        assertThat(row.get("auth_signed_data")).isEqualTo("signed.jwt");
        assertThat(licenseQueryRepository.findUpdatedAt(TENANT_A)).isEqualTo(before);
    }

    @Test
    @DisplayName("null 로 넘긴 성공·최초 시각·서명 값은 기존 값을 지킨다")
    void updateAuthResult_nullKeepsPrevious() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "SUCCESS", 0, at(13, 3), at(13, 3), at(1, 9), "signed.jwt"));

        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "GRACE_PERIOD", 1, at(14, 3), null, null, null));

        Map<String, Object> row = authRow(TENANT_A);
        assertThat(row.get("auth_status")).isEqualTo("GRACE_PERIOD");
        assertThat(row.get("auth_fail_count")).isEqualTo(1);
        assertThat(instant(TENANT_A, "auth_checked_at")).isEqualTo(at(14, 3));
        assertThat(instant(TENANT_A, "auth_success_at")).isEqualTo(at(13, 3));
        assertThat(instant(TENANT_A, "auth_init_at")).isEqualTo(at(1, 9));
        assertThat(row.get("auth_signed_data")).isEqualTo("signed.jwt");
    }

    @Test
    @DisplayName("재등록 → 인증 컬럼이 모두 비워진다")
    void upsertLicense_resetsAuthColumns() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "GRACE_PERIOD", 3, at(13, 3), at(10, 3), at(1, 9), "signed.jwt"));

        licenseRepository.upsertLicense(TENANT_A, "TR.other".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> row = authRow(TENANT_A);
        assertThat(row.get("auth_status")).isNull();
        assertThat(row.get("auth_fail_count")).isEqualTo(0);
        assertThat(row.get("auth_checked_at")).isNull();
        assertThat(row.get("auth_success_at")).isNull();
        assertThat(row.get("auth_init_at")).isNull();
        assertThat(row.get("auth_signed_data")).isNull();
    }

    @Test
    @DisplayName("등록 → 파일과 인증 결과를 함께 저장한다")
    void replaceLicense_savesBoth() {
        licenseRepository.replaceLicense(TENANT_A, BLOB, new LicenseAuthResultData(
                TENANT_A, "GRACE_PERIOD", 1, at(13, 3), null, null, null), false);

        Map<String, Object> row = authRow(TENANT_A);
        assertThat(row.get("auth_status")).isEqualTo("GRACE_PERIOD");
        assertThat(row.get("auth_fail_count")).isEqualTo(1);
        assertThat(licenseQueryRepository.findAuthState(TENANT_A).data()).isEqualTo(BLOB);
    }

    @Test
    @DisplayName("같은 라이선스 재등록 → 인증 이력을 비우지 않고 그 위에 기록한다")
    void replaceLicense_keepAuth_keepsHistory() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "INACTIVE", 3, at(13, 3), at(10, 3), at(1, 9), "signed.jwt"));
        byte[] redownloaded = "TR.redownloaded".getBytes(StandardCharsets.UTF_8);

        licenseRepository.replaceLicense(TENANT_A, redownloaded, new LicenseAuthResultData(
                TENANT_A, "INACTIVE", 3, at(14, 3), null, null, null), true);

        Map<String, Object> row = authRow(TENANT_A);
        assertThat(row.get("auth_status")).isEqualTo("INACTIVE");
        assertThat(row.get("auth_fail_count")).isEqualTo(3);
        assertThat(instant(TENANT_A, "auth_checked_at")).isEqualTo(at(14, 3));
        assertThat(instant(TENANT_A, "auth_success_at")).isEqualTo(at(10, 3));
        assertThat(instant(TENANT_A, "auth_init_at")).isEqualTo(at(1, 9));
        assertThat(row.get("auth_signed_data")).isEqualTo("signed.jwt");
        assertThat(licenseQueryRepository.findAuthState(TENANT_A).data()).isEqualTo(redownloaded);
    }

    /** 테스트 트랜잭션 밖에서 돌려 replaceLicense 자신의 트랜잭션이 롤백되는지 본다. 남은 행은 직접 지운다. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("등록 중 인증 결과 기록이 실패하면 파일도 저장되지 않는다")
    void replaceLicense_authWriteFails_rollsBackFile() {
        try {
            // auth_status 는 varchar(20) — 넘치는 값으로 두 번째 쓰기를 실패시킨다
            assertThatThrownBy(() -> licenseRepository.replaceLicense(TENANT_A, BLOB, new LicenseAuthResultData(
                    TENANT_A, "X".repeat(21), 1, at(13, 3), null, null, null), false))
                    .isInstanceOf(DataAccessException.class);

            assertThat(licenseQueryRepository.findAuthState(TENANT_A)).isNull();
        } finally {
            jdbc.update("DELETE FROM tb_license WHERE tenant_seq = ?", TENANT_A);
        }
    }

    @Test
    @DisplayName("버전 조회 → updated_at 과 auth_checked_at 을 함께 돌려준다")
    void findLicenseVersion_returnsBoth() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "SUCCESS", 0, at(13, 3), at(13, 3), null, null));

        LicenseVersion version = licenseQueryRepository.findLicenseVersion(TENANT_A);

        assertThat(version.updatedAt()).isEqualTo(licenseQueryRepository.findUpdatedAt(TENANT_A));
        assertThat(version.authCheckedAt()).isEqualTo(at(13, 3));
        assertThat(licenseQueryRepository.findLicenseVersion(TENANT_B)).isNull();
    }

    @Test
    @DisplayName("인증 상태 조회 → 파일·상태·시각을 타입대로 돌려준다")
    void findAuthState_typed() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "GRACE_PERIOD", 2, at(13, 3), null, at(1, 9), null));

        LicenseAuthStateData state = licenseQueryRepository.findAuthState(TENANT_A);

        assertThat(state.data()).isEqualTo(BLOB);
        assertThat(state.authStatus()).isEqualTo("GRACE_PERIOD");
        assertThat(state.authFailCount()).isEqualTo(2);
        assertThat(state.authCheckedAt()).isEqualTo(at(13, 3));
        assertThat(state.authInitAt()).isEqualTo(at(1, 9));
        assertThat(state.updatedAt()).isEqualTo(licenseQueryRepository.findUpdatedAt(TENANT_A));
    }

    @Test
    @DisplayName("조건부 기록 → 읽어 둔 updated_at 그대로면 기록한다")
    void updateAuthResultIfUnchanged_sameFile_written() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        Instant readAt = licenseQueryRepository.findAuthState(TENANT_A).updatedAt();

        int updated = licenseRepository.updateAuthResultIfUnchanged(new LicenseAuthResultData(
                TENANT_A, "SUCCESS", 0, at(13, 3), at(13, 3), null, null), readAt);

        assertThat(updated).isEqualTo(1);
        assertThat(authRow(TENANT_A).get("auth_status")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("조건부 기록 → 그 사이 다른 파일이 등록됐으면 새 파일의 상태를 덮어쓰지 않는다")
    void updateAuthResultIfUnchanged_fileReplaced_skipped() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        Instant readAt = licenseQueryRepository.findAuthState(TENANT_A).updatedAt();
        // 다른 파일 등록 — updated_at 을 확실히 다르게 둔다
        licenseRepository.replaceLicense(TENANT_A, "TR.other".getBytes(StandardCharsets.UTF_8), new LicenseAuthResultData(
                TENANT_A, "SUCCESS", 0, at(14, 3), at(14, 3), null, null), false);
        jdbc.update("UPDATE tb_license SET updated_at = updated_at + interval '1 second' WHERE tenant_seq = ?", TENANT_A);

        int updated = licenseRepository.updateAuthResultIfUnchanged(new LicenseAuthResultData(
                TENANT_A, "INACTIVE", 0, at(14, 4), null, null, null), readAt);

        assertThat(updated).isZero();
        assertThat(authRow(TENANT_A).get("auth_status")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("온라인 라이선스 테넌트 조회 → 인증 상태가 있는 테넌트만")
    void findOnlineLicensedTenantSeqs_onlyWithStatus() {
        licenseRepository.upsertLicense(TENANT_A, BLOB);
        licenseRepository.upsertLicense(TENANT_B, BLOB);
        licenseRepository.updateAuthResult(new LicenseAuthResultData(
                TENANT_A, "SUCCESS", 0, at(13, 3), at(13, 3), null, null));

        assertThat(licenseQueryRepository.findOnlineLicensedTenantSeqs()).contains(TENANT_A).doesNotContain(TENANT_B);
    }

    @Test
    @DisplayName("설치 식별자는 처음 넣은 값이 남는다")
    void insertGlobalIfAbsent_keepsFirst() {
        String key = SystemConfigKey.LICENSE_DEVICE_ID.getDbKey();
        systemConfigRepository.insertGlobalIfAbsent(SystemConfig.createSystemConfig(null, key, "first", SUPER_ADMIN_SEQ));
        systemConfigRepository.insertGlobalIfAbsent(SystemConfig.createSystemConfig(null, key, "second", SUPER_ADMIN_SEQ));

        assertThat(systemConfigQueryRepository.findGlobalBySettingKey(key).getValueText()).isEqualTo("first");
    }
}

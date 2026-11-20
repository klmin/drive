package com.thinkfree.drive.common;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 통합 테스트 데이터 만들기 — 테스트 컨테이너 DB 는 V1 시드(테넌트 1, superadmin·admin·anonymous)만 있다.
 * <p>
 * 그 밖의 데이터는 테스트마다 이 클래스로 만든다. 같은 컨테이너를 모든 테스트 클래스가 함께 쓰고
 * 리포지토리 테스트들이 "V1 시드만 있음" 을 전제로 정확한 값을 단언하므로, 공용 기준 데이터를 두지 않고
 * <b>반드시 테스트 트랜잭션(@Transactional) 안에서 불러 롤백</b>되게 한다 — 트랜잭션 밖이면 즉시 실패한다.
 * MockMvc 요청은 같은 스레드·같은 트랜잭션에서 돌아 아직 커밋하지 않은 데이터를 본다.
 * seq 는 IDENTITY 로 받으므로 다른 테스트 데이터와 부딪히지 않는다.
 */
public final class DriveFixtures {

    /** 기본 테넌트(V1 시드, SELF_MANAGED). DOMAIN·SUBDOMAIN 은 비어 있다 — 주소가 필요하면 {@link #tenantAddresses()} */
    public static final int DEFAULT_TENANT = 1;

    /** 슈퍼어드민(V1 시드, 테넌트 없음) */
    public static final int SUPER_ADMIN = 1;
    /** 기본 테넌트 관리자(V1 시드, ADMIN) */
    public static final int ADMIN = 2;
    /** 익명 사용자(V1 시드, ANONYMOUS) */
    public static final int ANONYMOUS = 3;

    /** {@link #tenantAddresses()} 가 기본 테넌트에 주는 주소 — AbstractMvcTest.DEFAULT_TEST_HOST 와 같다 */
    public static final String DEFAULT_SLUG = "default";
    public static final String DEFAULT_DOMAIN = "default.drive.thinkfree.com";
    /** {@link #tenantAddresses()} 가 만드는 두 번째 테넌트 — AbstractMvcTest.DEFAULT_ADD_TEST_HOST 와 같다 */
    public static final String DEFAULT_ADD_SLUG = "default-add";
    public static final String DEFAULT_ADD_DOMAIN = "default-add.drive.thinkfree.com";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final HostTenantCache hostTenantCache;

    private DriveFixtures(JdbcTemplate jdbc, HostTenantCache hostTenantCache) {
        this.jdbc = jdbc;
        this.hostTenantCache = hostTenantCache;
    }

    public static DriveFixtures of(JdbcTemplate jdbc) {
        return new DriveFixtures(jdbc, null);
    }

    /** 테넌트 주소를 바꾸는 픽스처({@link #tenantAddresses()})를 쓸 때 — 주소 → 테넌트 캐시를 비워야 한다 */
    public static DriveFixtures of(JdbcTemplate jdbc, HostTenantCache hostTenantCache) {
        return new DriveFixtures(jdbc, hostTenantCache);
    }

    // ---------------------------------------------------------------- 테넌트

    /**
     * 기본 테넌트(1)에 SUBDOMAIN default·DOMAIN default.drive.thinkfree.com 을 주고,
     * 두 번째 테넌트 default-add(DOMAIN default-add.drive.thinkfree.com)를 만든다. 두 번째 테넌트 seq 를 돌려준다.
     * <p>
     * 두 번째 테넌트는 SYSTEM_MANAGED 로 둔다: 주소가 맞지 않는 요청의 fallback 은 "활성 SELF_MANAGED 테넌트가 하나뿐일 때" 만
     * 동작하므로(TenantSeqResolver) SELF_MANAGED 를 하나 더 만들면 테스트 안의 fallback 요청이 달라진다.
     * <p>
     * 주소 → 테넌트 캐시(HostTenantCache)는 "없음" 결과도 담아 두므로 앞뒤로 비운다.
     * invalidateAll 은 트랜잭션이 끝날 때(롤백 포함) 한 번 더 비우므로 롤백된 주소가 다음 테스트에 남지 않는다.
     */
    public int tenantAddresses() {
        requireTransaction();
        if (hostTenantCache == null) {
            throw new IllegalStateException("tenantAddresses 는 DriveFixtures.of(jdbc, hostTenantCache) 로 만든다");
        }
        jdbc.update("UPDATE tb_tenant SET subdomain = ?, domain = ? WHERE tenant_seq = ?", DEFAULT_SLUG, DEFAULT_DOMAIN, DEFAULT_TENANT);
        Integer tenantSeq = jdbc.queryForObject(
                "INSERT INTO tb_tenant (tenant_name, tenant_type, user_mode, status, login_type, domain, subdomain, register_user_seq) "
                        + "VALUES ('테스트 추가 테넌트', 'SYSTEM_MANAGED', 'MULTI', 'ACTIVE', 'LOCAL', ?, ?, ?) RETURNING tenant_seq",
                Integer.class, DEFAULT_ADD_DOMAIN, DEFAULT_ADD_SLUG, SUPER_ADMIN);
        hostTenantCache.invalidateAll();
        return tenantSeq;
    }

    // ---------------------------------------------------------------- 사용자

    /** 활성 사용자 하나와 역할 행. userId 는 겹치지 않는 임의 값 */
    public int user(int tenantSeq, RoleType role, String name) {
        requireTransaction();
        Integer userSeq = jdbc.queryForObject(
                "INSERT INTO tb_user (tenant_seq, user_id, user_name, status, register_user_seq) "
                        + "VALUES (?, 'fixture-' || gen_random_uuid() || '@drive.test', ?, 'ACTIVE', ?) "
                        + "RETURNING user_seq",
                Integer.class, tenantSeq, name, SUPER_ADMIN);
        jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, ?, ?)",
                userSeq, role.name(), SUPER_ADMIN);
        return userSeq;
    }

    // ---------------------------------------------------------------- API 키

    /** 만료 1년 뒤의 ACTIVE 키. 키 문자열(평문)을 돌려준다 — {@code Authorization: Bearer <키>} */
    public String apiKey(int tenantSeq, int userSeq, RoleType role, Set<ApiKeyScope> scopes) {
        requireTransaction();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO tb_api_key (tenant_seq, key_name, key_purpose, api_key, status, user_seq, role_type, scope, expire_date, register_user_seq) "
                        + "VALUES (?, 'fixture', 'test', ?, 'ACTIVE', ?, ?, ?, CURRENT_TIMESTAMP + interval '1 year', ?)",
                tenantSeq, key, userSeq, role.name(), ApiKeyScope.join(EnumSet.copyOf(scopes)), userSeq);
        return key;
    }

    // ---------------------------------------------------------------- 리소스

    /** 폴더. parentSeq 가 null 이면 최상위. full_path_seq·full_path 는 앱과 같은 규칙(부모 경로 + 자기 seq/이름 + "/") */
    public long folder(int ownerSeq, Long parentSeq, String name) {
        requireTransaction();
        long seq = insertResource(ownerSeq, "FOLDER", parentSeq, name);
        Map<String, Object> parent = parentPath(ownerSeq, parentSeq);
        jdbc.update("UPDATE tb_resource SET full_path_seq = ?, full_path = ? WHERE user_seq = ? AND resource_seq = ?",
                parent.get("full_path_seq") + String.valueOf(seq) + "/", parent.get("full_path") + name + "/", ownerSeq, seq);
        return seq;
    }

    /** 파일(내용 객체 없음 — DB 행만). 파일의 full_path_seq·full_path 는 앱처럼 비워 둔다 */
    public long file(int ownerSeq, Long parentSeq, String name) {
        requireTransaction();
        return insertResource(ownerSeq, "FILE", parentSeq, name);
    }

    /** 잠긴 상태로 바꾼다(is_locked='Y', locked_date=now) */
    public void lock(int ownerSeq, long resourceSeq) {
        requireTransaction();
        int updated = jdbc.update("UPDATE tb_resource SET is_locked = 'Y', locked_date = CURRENT_TIMESTAMP WHERE user_seq = ? AND resource_seq = ?",
                ownerSeq, resourceSeq);
        if (updated != 1) {
            throw new IllegalStateException("잠글 리소스가 없음: (" + ownerSeq + ", " + resourceSeq + ")");
        }
    }

    private long insertResource(int ownerSeq, String type, Long parentSeq, String name) {
        int dot = name.lastIndexOf('.');
        String ext = "FILE".equals(type) && dot > 0 ? name.substring(dot + 1) : null;
        return jdbc.queryForObject(
                "INSERT INTO tb_resource (user_seq, owner_seq, latest_owner_seq, resource_type, parent_folder_seq, resource_name, original_file_name, "
                        + "ext, size_byte, register_user_seq) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING resource_seq",
                Long.class, ownerSeq, ownerSeq, ownerSeq, type, parentSeq, name, name,
                ext, "FILE".equals(type) ? 10L : null, ownerSeq);
    }

    private Map<String, Object> parentPath(int ownerSeq, Long parentSeq) {
        if (parentSeq == null) {
            return Map.of("full_path_seq", "", "full_path", "");
        }
        return jdbc.queryForMap("SELECT COALESCE(full_path_seq, '') AS full_path_seq, COALESCE(full_path, '') AS full_path "
                + "FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", ownerSeq, parentSeq);
    }

    // ---------------------------------------------------------------- 공유

    /** 공유 권한 묶음. 공유(tb_share)·대상(tb_share_target) 모두 같은 열을 쓴다 */
    public record Perm(boolean view, boolean edit, boolean download, boolean upload, boolean delete, boolean share) {
        public static final Perm VIEW = new Perm(true, false, false, false, false, false);
        public static final Perm EDIT = new Perm(true, true, true, false, false, false);
    }

    /** 리소스 공유 행(소유자 파티션). shareSeq 를 돌려준다 */
    public long share(int ownerSeq, long resourceSeq, ShareType type, Perm perm) {
        requireTransaction();
        return jdbc.queryForObject(
                "INSERT INTO tb_share (user_seq, resource_seq, share_type, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING share_seq",
                Long.class, ownerSeq, resourceSeq, type.name(),
                yn(perm.view()), yn(perm.edit()), yn(perm.download()), yn(perm.upload()), yn(perm.delete()), yn(perm.share()), ownerSeq);
    }

    /** 공유 대상 행(받는 사람 파티션). shareTargetSeq 를 돌려준다 */
    public long shareTarget(int ownerSeq, long shareSeq, long resourceSeq, int receiverSeq, Perm perm) {
        requireTransaction();
        return jdbc.queryForObject(
                "INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, "
                        + "can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?) RETURNING share_target_seq",
                Long.class, receiverSeq, shareSeq, resourceSeq, ownerSeq,
                yn(perm.view()), yn(perm.edit()), yn(perm.download()), yn(perm.upload()), yn(perm.delete()), yn(perm.share()), ownerSeq);
    }

    /** 특정 사용자 공유 한 번에: 공유 + 대상 행. shareSeq 를 돌려준다 */
    public long shareWith(int ownerSeq, long resourceSeq, int receiverSeq, Perm perm) {
        long shareSeq = share(ownerSeq, resourceSeq, ShareType.SPECIFIC_USERS, perm);
        shareTarget(ownerSeq, shareSeq, resourceSeq, receiverSeq, perm);
        return shareSeq;
    }

    // ---------------------------------------------------------------- 라이선스

    /**
     * 테넌트 라이선스 파일(클래스패스, 예: {@code files/TFD_...tfl})을 검증 없이 그대로 넣는다(등록 API 의 서명·온라인 인증을 거치지 않음).
     * LicenseService 의 캐시는 DB 의 updated_at 으로 버전을 보므로 롤백 뒤에는 "라이선스 없음" 으로 돌아간다.
     * 주의: 지금 저장소의 .tfl 은 모두 운영 공개키 서명 검증에 실패하므로 "검증 실패" 상태만 만들 수 있다.
     */
    public void license(int tenantSeq, String classpathFile) {
        requireTransaction();
        byte[] data;
        try {
            data = new ClassPathResource(classpathFile).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(classpathFile, e);
        }
        jdbc.update("INSERT INTO tb_license (tenant_seq, data, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (tenant_seq) DO UPDATE SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at", tenantSeq, data);
    }

    // ----------------------------------------------------------------

    private static String yn(boolean b) {
        return b ? "Y" : "N";
    }

    /** 공유 컨테이너에 테스트 데이터가 커밋돼 남지 않도록 트랜잭션 밖 호출을 막는다 */
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("DriveFixtures 는 테스트 트랜잭션(@Transactional) 안에서만 쓴다 — 롤백되지 않으면 공유 DB 에 남는다");
        }
    }
}

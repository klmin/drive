package com.thinkfree.drive.queryrepository;

import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;
import com.thinkfree.drive.cleanup.trash.repository.TrashCleanUpQueryRepository;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.oauth2.connection.repository.OAuth2ClientConnectionQueryRepository;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;

/**
 * 작은 조회 리포 2종(jOOQ) 기대값 단언 — 10-30 작업 B P4 에서 전환 전/후 비교 테스트를 대신한다(기대값은 비교 테스트로 구 JdbcClient 구현과 같음을 확인한 값). 트랜잭션 롤백.
 * DB 현재 시각(License.getServerTimeMS)은 {@code LicenseServerTimeMsTest}.
 * <p>
 * 휴지통 영구삭제 대상(날짜·사용자 분기, 루트 + delete_parent 하위 + 버전 3단 묶음)은 SQL 에 ORDER BY 가 없고 호출부(DefaultTrashCleanUpService)가
 * 합계·IN 삭제·감사/알림 목록으로만 써서 정렬한 문자열로 비교한다. OAuth2 연결 목록(SAS 클라이언트 LEFT JOIN — 지워진 클라이언트 포함)은 순서까지.
 */
@DisplayName("작은 조회 리포 2종 — 휴지통 영구삭제 대상·OAuth2 연결 기대값(PostgreSQL Testcontainers)")
class SmallQueryRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;

    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TrashCleanUpQueryRepository trashCleanUp;
    @Autowired
    private OAuth2ClientConnectionQueryRepository oauth2Connection;

    private int other;

    @BeforeEach
    void seed() {
        loginAs(ADMIN);

        Integer otherTenant = jdbc.queryForObject("INSERT INTO tb_tenant (tenant_name, register_user_seq) VALUES ('비교 테넌트', ?) RETURNING tenant_seq", Integer.class, SUPER_ADMIN);
        User u = User.createUser("cmp-other", otherTenant, null, SignupType.LOCAL, "다른 사용자", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        other = u.getUserSeq();

        // 휴지통: ADMIN 폴더(하위 폴더·버전 든 파일), ADMIN 파일(버전 2), 하위인데 is_delete 가 다른 행, 다른 사용자 파일 — 삭제일 다름
        Resource tRoot = folder(ADMIN, "t-root", null);
        Resource tChild = folder(ADMIN, "t-child", tRoot);
        Resource tChildFile = file(ADMIN, "t-child.txt", tChild, 7L);
        Resource tAlive = file(ADMIN, "t-alive.txt", tRoot, 3L);
        Resource tFile = file(ADMIN, "t-file.txt", null, 11L);
        Resource oFile = file(other, "o-file.txt", null, 13L);
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq IN (?, ?, ?, ?, ?)", seq(tRoot), seq(tChild), seq(tChildFile), seq(tFile), seq(oFile));
        jdbc.update("UPDATE tb_resource SET delete_parent_folder_seq = ? WHERE resource_seq IN (?, ?, ?)", seq(tRoot), seq(tChild), seq(tChildFile), seq(tAlive));
        trash(ADMIN, tRoot, "2030-01-01 00:00:00");
        trash(ADMIN, tFile, "2030-02-01 00:00:00");
        trash(other, oFile, "2030-03-01 00:00:00");
        version(ADMIN, tChildFile, 1, 2L);
        version(ADMIN, tFile, 1, 4L);
        version(ADMIN, tFile, 2, 5L);
        version(other, oFile, 1, 6L);

        // OAuth2 연결: 등록 클라이언트 2개, ADMIN 연결 3개(그중 1개는 클라이언트 행 없음), 다른 사용자 1개 — 등록일 동률 포함
        client("cmp-client-a", "클라이언트 A");
        client("cmp-client-b", "클라이언트 B");
        connection(ADMIN, "cmp-client-a", "2030-01-01 00:00:00", "2030-01-05 00:00:00");
        connection(ADMIN, "cmp-client-b", "2030-01-02 00:00:00", null);
        connection(ADMIN, "cmp-client-gone", "2030-01-02 00:00:00", null);
        connection(other, "cmp-client-a", "2030-01-03 00:00:00", null);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TrashCleanUp — 날짜·사용자 분기 × isDelete — 사용자 → 리소스(크기) → 버전 크기")
    void trashCleanUpTargets() {
        Snapshot s = snapshot();
        for (String at : Arrays.asList("2029-12-31 00:00:00", "2030-01-01 00:00:00", "2030-02-15 00:00:00", "2031-01-01 00:00:00")) {
            for (Boolean isDelete : Arrays.asList(true, false, null)) {
                s.put("byDate " + at + "/" + isDelete, () -> targets(trashCleanUp.findTrashCleanUpTargets(new TrashCleanUpByDateQuery(Timestamp.valueOf(at).toInstant(), isDelete))));
            }
        }
        s.put("byDate null", () -> targets(trashCleanUp.findTrashCleanUpTargets(new TrashCleanUpByDateQuery(null, true))));
        for (Integer user : Arrays.asList(ADMIN, other, SUPER_ADMIN, null)) {
            for (Boolean isDelete : Arrays.asList(true, false, null)) {
                s.put("byUser " + who(user) + "/" + isDelete, () -> targets(trashCleanUp.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(user, isDelete))));
            }
        }
        s.assertEquals("trashCleanUpTargets", Map.ofEntries(
                Map.entry("byDate 2029-12-31 00:00:00/true", "[]"),
                Map.entry("byDate 2029-12-31 00:00:00/false", "[]"),
                Map.entry("byDate 2029-12-31 00:00:00/null", "[]"),
                Map.entry("byDate 2030-01-01 00:00:00/true", "[admin=[t-child.txt:FILE:7[2], t-child:FOLDER:null[], t-root:FOLDER:null[]]]"),
                Map.entry("byDate 2030-01-01 00:00:00/false", "[admin=[t-alive.txt:FILE:3[], t-root:FOLDER:null[]]]"),
                Map.entry("byDate 2030-01-01 00:00:00/null", "[admin=[t-root:FOLDER:null[]]]"),
                Map.entry("byDate 2030-02-15 00:00:00/true", "[admin=[t-child.txt:FILE:7[2], t-child:FOLDER:null[], t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]]]"),
                Map.entry("byDate 2030-02-15 00:00:00/false", "[admin=[t-alive.txt:FILE:3[], t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]]]"),
                Map.entry("byDate 2030-02-15 00:00:00/null", "[admin=[t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]]]"),
                Map.entry("byDate 2031-01-01 00:00:00/true", "[admin=[t-child.txt:FILE:7[2], t-child:FOLDER:null[], t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]], other=[o-file.txt:FILE:13[6]]]"),
                Map.entry("byDate 2031-01-01 00:00:00/false", "[admin=[t-alive.txt:FILE:3[], t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]], other=[o-file.txt:FILE:13[6]]]"),
                Map.entry("byDate 2031-01-01 00:00:00/null", "[admin=[t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]], other=[o-file.txt:FILE:13[6]]]"),
                Map.entry("byDate null", "[]"),
                Map.entry("byUser admin/true", "[admin=[t-child.txt:FILE:7[2], t-child:FOLDER:null[], t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]]]"),
                Map.entry("byUser admin/false", "[admin=[t-alive.txt:FILE:3[], t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]]]"),
                Map.entry("byUser admin/null", "[admin=[t-file.txt:FILE:11[4, 5], t-root:FOLDER:null[]]]"),
                Map.entry("byUser other/true", "[other=[o-file.txt:FILE:13[6]]]"),
                Map.entry("byUser other/false", "[other=[o-file.txt:FILE:13[6]]]"),
                Map.entry("byUser other/null", "[other=[o-file.txt:FILE:13[6]]]"),
                Map.entry("byUser superadmin/true", "[]"),
                Map.entry("byUser superadmin/false", "[]"),
                Map.entry("byUser superadmin/null", "[]"),
                Map.entry("byUser null/true", "[]"),
                Map.entry("byUser null/false", "[]"),
                Map.entry("byUser null/null", "[]")));
    }

    @Test
    @DisplayName("OAuth2ClientConnection — 사용자별 연결 목록(등록일 DESC, 클라이언트 없음·등록일 동률)")
    void oauth2Connections() {
        Snapshot s = snapshot();
        for (int user : new int[]{ADMIN, other, SUPER_ADMIN, 999_999}) {
            s.put("connections " + who(user), () -> oauth2Connection.findAllWithClientNameByUserSeq(user).stream()
                    .map(c -> c.getClientId() + "/" + c.getClientName() + "/" + c.getScopes() + "/" + local(c.getRegisterDate()) + "/" + local(c.getLastUsedDate())).toList());
        }
        s.assertEquals("oauth2Connections", Map.ofEntries(
                Map.entry("connections admin", "[cmp-client-b/클라이언트 B/drive/2030-01-02T00:00/null, cmp-client-gone/null/drive/2030-01-02T00:00/null, cmp-client-a/클라이언트 A/drive/2030-01-01T00:00/2030-01-05T00:00]"),
                Map.entry("connections other", "[cmp-client-a/클라이언트 A/drive/2030-01-03T00:00/null]"),
                Map.entry("connections superadmin", "[]"),
                Map.entry("connections 999999", "[]")));
    }

    // ------------------------------------------------------------------ 헬퍼

    private Snapshot snapshot() {
        return new Snapshot(new Render(field -> null));
    }

    /** 사용자 → 리소스(이름:타입:크기, 버전 크기) — 순서 무관이라 정렬. */
    private List<String> targets(List<TrashCleanUpTargetsData> list) {
        return list.stream().map(u -> who(u.getUserSeq()) + "=" + u.getResourceTargets().stream()
                        .map(r -> r.getResourceName() + ":" + r.getResourceType() + ":" + r.getSizeByte()
                                + (r.getResourceVersionTargets() == null ? "" : r.getResourceVersionTargets().stream().map(v -> String.valueOf(v.getSizeByte())).sorted().toList()))
                        .sorted().toList())
                .sorted().toList();
    }

    private static String local(Instant t) {
        return t == null ? null : DriveTime.toLocal(t).toString();
    }

    private String who(Integer userSeq) {
        if (userSeq == null) {
            return "null";
        }
        return userSeq == ADMIN ? "admin" : userSeq == other ? "other" : userSeq == SUPER_ADMIN ? "superadmin" : String.valueOf(userSeq);
    }

    private static Long seq(Resource r) {
        return r.getResourceSeq();
    }

    // ------------------------------------------------------------------ 픽스처

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private Resource folder(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).resourceType(ResourceType.FOLDER)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .fullPathSeq(parent == null ? "" : parent.getFullPathSeq()).fullPath(parent == null ? "" : parent.getFullPath())
                .copyNumber(0).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initFolderPath();
        resourceRepository.flush();
        return r;
    }

    private Resource file(int userSeq, String name, Resource parent, long size) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext("txt").contentType("text/plain").sizeByte(size)
                .versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    private void trash(int userSeq, Resource r, String at) {
        jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq, register_date) VALUES (?, ?, ?, ?)", userSeq, seq(r), userSeq, Timestamp.valueOf(at));
    }

    private void version(int userSeq, Resource r, int number, long size) {
        jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, status, register_user_seq) VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', ?)",
                userSeq, seq(r), userSeq, "k/" + seq(r) + "/v" + number, number, size, userSeq);
    }

    private void client(String clientId, String name) {
        jdbc.update("INSERT INTO oauth2_registered_client (id, client_id, client_name, client_authentication_methods, authorization_grant_types, scopes, client_settings, token_settings) "
                + "VALUES (?, ?, ?, 'none', 'authorization_code', 'drive', '{}', '{}')", clientId + "-id", clientId, name);
    }

    private void connection(int userSeq, String clientId, String registeredAt, String lastUsedAt) {
        jdbc.update("INSERT INTO tb_oauth2_client_connection (user_seq, client_id, scopes, register_date, token_issued_date, last_used_date) VALUES (?, ?, 'drive', ?, ?, ?)",
                userSeq, clientId, Timestamp.valueOf(registeredAt), Timestamp.valueOf(registeredAt), lastUsedAt == null ? null : Timestamp.valueOf(lastUsedAt));
    }
}

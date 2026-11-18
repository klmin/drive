package com.thinkfree.drive.resource.service.impl;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.dto.ResourceRenameDto;
import com.thinkfree.drive.resource.dto.ResourceRestoreDto;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.query.ResourceDeleteStateQuery;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.resource.share.service.ResourceShareLinkService;
import com.thinkfree.drive.share.service.ShareDomainService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

/**
 * W10b — DefaultResourceService 의 단건 변경이 관리 엔티티 + 변경 감지로 바뀐 뒤의 DB 값·감사·flush 시점(PostgreSQL Testcontainers, 트랜잭션 롤백).
 * <ul>
 *   <li>flush 를 남긴 경로(이동·복원·이름 변경 — 결정 R3): 서비스 반환 직후 <b>JdbcTemplate(자동 flush 없음)</b>이 새 값을 본다 = 같은 tx 의 감사 공유자 경로(JdbcClient 재귀 CTE)·이동 후처리가 새 값을 읽는 근거</li>
 *   <li>flush 를 뺀 경로(파일 휴지통·잠금·공유 토큰·저장 이름·폴더 경로): 반환 직후 JdbcTemplate 은 옛 값, flush(커밋) 뒤 새 값 — UPDATE 가 커밋 때 나간다</li>
 *   <li>다른 소유자로 이동 = 파티션 키(user_seq) 변경 감지(구 moveToOtherUser JdbcClient)</li>
 * </ul>
 */
@DisplayName("DefaultResourceService 변경 감지 — 이동·복원·이름 변경·휴지통·잠금·공유 토큰·저장")
class DefaultResourceServiceChangeDetectionTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;

    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ResourceLockUnlockService resourceLockUnlockService;
    @Autowired
    private ResourceShareLinkService resourceShareLinkService;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    /** 링크 기본 URL 은 테넌트 베이스 도메인 설정이 필요해(테스트 DB 없음) 목 — 이 테스트는 토큰 저장만 본다 */
    @MockitoBean
    private ShareDomainService shareDomainService;

    @BeforeEach
    void login() {
        loginAs(ADMIN);
        given(shareDomainService.getLinkBaseUrl(anyInt())).willReturn("https://link.test/");
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

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

    private Resource file(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext("txt").contentType("text/plain").sizeByte(3L)
                .versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    /** 서비스가 다른 요청(새 트랜잭션)에서 로드하는 것처럼 — 픽스처를 내보내고 캐시를 비운다 */
    private void newRequest() {
        entityManager.flush();
        entityManager.clear();
        jdbc.update("UPDATE tb_resource SET update_user_seq = NULL, update_date = NULL WHERE user_seq IN (?, ?)", ADMIN, SUPER_ADMIN);
    }

    /** JdbcTemplate — 자동 flush 없음(JdbcClient 와 같음) */
    private <T> T col(long resourceSeq, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM tb_resource WHERE resource_seq = ?", type, resourceSeq);
    }

    private static void assertCode(Runnable r, ResourceErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    // ------------------------------------------------------------------ flush 를 남긴 경로(R3)

    @Test
    @DisplayName("fileMove(같은 소유자) — 반환 직후 JdbcClient 가 새 부모를 본다(flush 유지), 수정자 = 시큐리티 사용자")
    void fileMove_sameOwner_flushedForSameTxJdbcReaders() {
        Resource a = folder(ADMIN, "a", null);
        Resource b = folder(ADMIN, "b", null);
        Resource f = file(ADMIN, "m.txt", a);
        newRequest();

        var result = resourceService.fileMove(ResourceMoveDto.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).targetUserSeq(ADMIN)
                .parentFolderSeq(b.getResourceSeq()).resourceType(ResourceType.FILE).isDelete(false).build());

        assertThat(col(f.getResourceSeq(), "parent_folder_seq", Long.class)).isEqualTo(b.getResourceSeq());
        assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);
        assertThat(result.userChanged()).isFalse();
        assertThat(result.occurredDatetime()).isNotNull();
    }

    @Test
    @DisplayName("fileMove(다른 소유자) — 본 행 user_seq·부모가 변경 감지로 바뀜(파티션 이동), 반환 직후 JdbcClient 가 새 파티션에서 본다")
    void fileMove_otherOwner_partitionKeyChangedByDirtyChecking() {
        Resource f = file(ADMIN, "x.txt", null);
        Resource target = folder(SUPER_ADMIN, "dest", null);
        newRequest();

        var result = resourceService.fileMove(ResourceMoveDto.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).targetUserSeq(SUPER_ADMIN)
                .parentFolderSeq(target.getResourceSeq()).resourceType(ResourceType.FILE).isDelete(false).build());

        assertThat(result.userChanged()).isTrue();
        assertThat(jdbc.queryForObject("SELECT user_seq FROM tb_resource WHERE resource_seq = ?", Integer.class, f.getResourceSeq())).isEqualTo(SUPER_ADMIN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", Integer.class, SUPER_ADMIN, f.getResourceSeq())).isEqualTo(1);
        assertThat(col(f.getResourceSeq(), "parent_folder_seq", Long.class)).isEqualTo(target.getResourceSeq());
    }

    @Test
    @DisplayName("folderMove(다른 소유자) — 폴더 본 행(변경 감지)·하위 폴더(JdbcClient 치환)·하위 파일(벌크 JPQL) 모두 대상 소유자, 경로 치환")
    void folderMove_otherOwner() {
        Resource top = folder(ADMIN, "top", null);
        Resource child = folder(ADMIN, "child", top);
        Resource f = file(ADMIN, "in.txt", child);
        Resource target = folder(SUPER_ADMIN, "dest", null);
        newRequest();

        var result = resourceService.folderMove(ResourceMoveDto.builder().resourceSeq(top.getResourceSeq()).userSeq(ADMIN).targetUserSeq(SUPER_ADMIN)
                .parentFolderSeq(target.getResourceSeq()).resourceType(ResourceType.FOLDER).isDelete(false).build());

        assertThat(result.userChanged()).isTrue();
        assertThat(col(top.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
        assertThat(col(top.getResourceSeq(), "full_path", String.class)).isEqualTo("dest/top/");
        assertThat(col(child.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
        assertThat(col(child.getResourceSeq(), "full_path", String.class)).isEqualTo("dest/top/child/");
        assertThat(col(f.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
    }

    @Test
    @DisplayName("folderRenameWithChildFolders — 자기 이름·경로(변경 감지)와 하위 경로(JdbcClient), 반환 직후 JdbcClient 가 새 값을 본다")
    void folderRename_flushed() {
        Resource top = folder(ADMIN, "old", null);
        Resource child = folder(ADMIN, "c", top);
        newRequest();

        resourceService.folderRenameWithChildFolders(ResourceRenameDto.builder().resourceSeq(top.getResourceSeq()).userSeq(ADMIN)
                .resourceType(ResourceType.FOLDER).isDelete(false).resourceName("new").originalFileName("new").copyNumber(0).build());

        assertThat(col(top.getResourceSeq(), "resource_name", String.class)).isEqualTo("new");
        assertThat(col(top.getResourceSeq(), "full_path", String.class)).isEqualTo("new/");
        assertThat(col(child.getResourceSeq(), "full_path", String.class)).isEqualTo("new/c/");
    }

    @Test
    @DisplayName("resolveName — NONE: 요청 이름 그대로 / AUTO: 같은 이름 있으면 번호, 반환 직후 JdbcClient 가 새 이름을 본다. 없는 리소스는 NOT_FOUND")
    void resolveName() {
        Resource f = file(ADMIN, "r.txt", null);
        file(ADMIN, "taken.txt", null);
        newRequest();

        var none = resourceService.resolveName(ResourceRenameDto.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).resourceType(ResourceType.FILE)
                .isDelete(false).resourceName("r2.txt").originalFileName("r2.txt").nameConflictPolicy(NameConflictPolicy.NONE).build());
        assertThat(none.name()).isEqualTo("r2");
        assertThat(col(f.getResourceSeq(), "resource_name", String.class)).isEqualTo("r2.txt");

        var auto = resourceService.resolveName(ResourceRenameDto.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).resourceType(ResourceType.FILE)
                .isDelete(false).resourceName("taken.txt").originalFileName("taken.txt").baseFileName("taken.txt").originalFileNameWithoutExt("taken").ext("txt").copyNumber(0)
                .nameConflictPolicy(NameConflictPolicy.AUTO).build());
        assertThat(col(f.getResourceSeq(), "resource_name", String.class)).isEqualTo(auto.fullName()).isNotEqualTo("taken.txt");

        assertCode(() -> resourceService.resolveName(ResourceRenameDto.builder().resourceSeq(999999L).userSeq(ADMIN).resourceName("z.txt")
                .originalFileName("z.txt").nameConflictPolicy(NameConflictPolicy.NONE).build()), ResourceErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("restoreFile — 잠금 로드 + 변경 감지: 삭제 N·삭제 부모 NULL·부모 유지, 반환 직후 JdbcClient 가 본다. 삭제 상태가 아니면 NOT_FOUND")
    void restoreFile() {
        Resource a = folder(ADMIN, "ra", null);
        Resource f = file(ADMIN, "rf.txt", a);
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y', delete_parent_folder_seq = NULL WHERE resource_seq = ?", f.getResourceSeq());
        newRequest();

        resourceService.restoreFile(ResourceRestoreDto.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).resourceType(ResourceType.FILE).parentExists(true).build());

        assertThat(col(f.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
        assertThat(col(f.getResourceSeq(), "parent_folder_seq", Long.class)).isEqualTo(a.getResourceSeq());
        assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);
        assertCode(() -> resourceService.restoreFile(ResourceRestoreDto.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN)
                .resourceType(ResourceType.FILE).parentExists(true).build()), ResourceErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("restoreFolder — 자기(변경 감지)·하위(JdbcClient) 복원, 부모 없으면 루트로, 반환 직후 JdbcClient 가 본다")
    void restoreFolder() {
        Resource top = folder(ADMIN, "rt", null);
        Resource child = folder(ADMIN, "rc", top);
        resourceService.updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery.builder().resourceSeq(top.getResourceSeq()).userSeq(ADMIN)
                .isDelete(true).fullPathSeq(top.getFullPathSeq()).build());
        newRequest();

        resourceService.restoreFolder(ResourceRestoreDto.builder().resourceSeq(top.getResourceSeq()).userSeq(ADMIN).resourceType(ResourceType.FOLDER).parentExists(false).build());

        assertThat(col(top.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
        assertThat(col(top.getResourceSeq(), "parent_folder_seq", Long.class)).isNull();
        assertThat(col(child.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
        assertThat(col(child.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isNull();
        assertThat(col(child.getResourceSeq(), "full_path", String.class)).isEqualTo("rt/rc/");
    }

    // ------------------------------------------------------------------ flush 를 뺀 경로 — 커밋(flush) 때 UPDATE

    @Test
    @DisplayName("updateIsDeleteByFile·잠금/해제 — 반환 직후엔 DB 옛 값(UPDATE 는 커밋 때), flush 뒤 새 값(공유 토큰은 단건 JPQL 이라 즉시). 없는 행은 각 오류 코드")
    void noFlushPaths_writtenAtCommit() {
        Resource f = file(ADMIN, "t.txt", null);
        Resource g = file(ADMIN, "lk.txt", null);
        Resource h = file(ADMIN, "tok.txt", null);
        jdbc.update("UPDATE tb_resource SET share_token = NULL WHERE resource_seq = ?", h.getResourceSeq());
        newRequest();

        // 각 호출 직후 확인 — 다음 호출의 JPQL 로드가 같은 테이블의 미반영 변경을 자동 flush 하므로(Hibernate AUTO, 정상)
        resourceService.updateIsDeleteByFile(f.getResourceSeq(), ADMIN);
        assertThat(col(f.getResourceSeq(), "is_delete", String.class)).as("커밋 전(flush 전)").isEqualTo("N");
        entityManager.flush();
        assertThat(col(f.getResourceSeq(), "is_delete", String.class)).isEqualTo("Y");

        resourceLockUnlockService.lockResource(g.getResourceSeq(), ADMIN, ADMIN, Instant.now());
        assertThat(col(g.getResourceSeq(), "is_locked", String.class)).as("커밋 전").isEqualTo("N");
        entityManager.flush();
        assertThat(col(g.getResourceSeq(), "is_locked", String.class)).isEqualTo("Y");
        assertThat(col(g.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);

        // 공유 토큰은 변경 감지가 아니라 단건 JPQL(수정 감사를 건드리지 않음) — 호출 즉시 DB 에 반영
        var link = resourceShareLinkService.getOrCreateLinkUrl(ADMIN, h.getResourceSeq());
        String token = col(h.getResourceSeq(), "share_token", String.class);
        assertThat(token).isNotBlank();
        assertThat(link).endsWith(token);

        resourceLockUnlockService.unlockResource(g.getResourceSeq(), ADMIN, ADMIN, Instant.now());
        entityManager.flush();
        assertThat(col(g.getResourceSeq(), "is_locked", String.class)).isEqualTo("N");

        assertCode(() -> resourceService.updateIsDeleteByFile(999999L, ADMIN), ResourceErrorCode.DELETE_FAIL);
        assertCode(() -> resourceLockUnlockService.lockResource(999999L, ADMIN, ADMIN, Instant.now()), ResourceErrorCode.LOCK_FAIL);
        assertCode(() -> resourceLockUnlockService.unlockResource(999999L, ADMIN, ADMIN, Instant.now()), ResourceErrorCode.UNLOCK_FAIL);
    }

    @Test
    @DisplayName("공유 링크 발급(토큰 없을 때) — share_token 만 바뀌고 수정자·수정일은 그대로(구 updateShareToken = SHARE_TOKEN 만 UPDATE), 다른 사용자가 발급해도")
    void shareToken_doesNotTouchUpdateAudit() {
        Resource h = file(ADMIN, "tok2.txt", null);
        jdbc.update("UPDATE tb_resource SET share_token = NULL WHERE resource_seq = ?", h.getResourceSeq());
        newRequest();
        jdbc.update("UPDATE tb_resource SET update_user_seq = ?, update_date = ? WHERE resource_seq = ?",
                ADMIN, java.sql.Timestamp.from(Instant.parse("2026-01-02T03:04:05Z")), h.getResourceSeq());
        loginAs(SUPER_ADMIN);

        resourceShareLinkService.getOrCreateLinkUrl(ADMIN, h.getResourceSeq());
        entityManager.flush();

        assertThat(col(h.getResourceSeq(), "share_token", String.class)).isNotBlank();
        assertThat(col(h.getResourceSeq(), "update_user_seq", Integer.class)).as("수정자 그대로").isEqualTo(ADMIN);
        assertThat(col(h.getResourceSeq(), "update_date", java.sql.Timestamp.class).toInstant()).as("수정일 그대로").isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
    }

    @Test
    @DisplayName("잠금 — 시큐리티 없음(웹오피스)이면 로그인 사용자가 수정자")
    void lock_withoutSecurity_loginUserIsUpdater() {
        Resource g = file(ADMIN, "wo.txt", null);
        newRequest();
        SecurityContextHolder.clearContext();

        resourceLockUnlockService.lockResource(g.getResourceSeq(), ADMIN, SUPER_ADMIN, Instant.now());
        entityManager.flush();

        assertThat(col(g.getResourceSeq(), "is_locked", String.class)).isEqualTo("Y");
        assertThat(col(g.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
    }

    @Test
    @DisplayName("saveFolderWithCopyCheck — 저장(즉시 INSERT) 뒤 경로는 변경 감지 + 메서드 안 flush(W10d D1 — 경로 저장 오류를 재시도·INSERT_FAIL 변환 안에서): 반환 직후 DB 에 부모 경로 + 이름")
    void saveFolder_pathByDirtyChecking() {
        newRequest();
        var parent = resourceService.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(ADMIN).ownerSeq(ADMIN)
                .baseFolderName("p").originalFolderName("p").build());
        assertThat(col(parent.getResourceSeq(), "full_path", String.class)).as("메서드 안에서 flush — 커밋 전에도 반영").isEqualTo("p/");
        assertThat(parent.fullPath()).as("반환 Result(감사 FOLDER_CREATE 인자) = 최종 경로").isEqualTo("p/");
        assertThat(parent.resourceName()).isEqualTo("p");

        var child = resourceService.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(ADMIN).ownerSeq(ADMIN).parentFolderSeq(parent.getResourceSeq())
                .baseFolderName("q").originalFolderName("q").build());
        entityManager.flush();

        assertThat(col(parent.getResourceSeq(), "full_path", String.class)).isEqualTo("p/");
        assertThat(col(child.getResourceSeq(), "full_path", String.class)).as("부모 경로를 DTO 로 읽어 이어 붙임").isEqualTo("p/q/");
        assertThat(child.fullPath()).isEqualTo("p/q/");
        assertThat(child.parentFolderSeq()).isEqualTo(parent.resourceSeq());
        assertThat(col(child.getResourceSeq(), "full_path_seq", String.class)).isEqualTo(parent.getResourceSeq() + "/" + child.getResourceSeq() + "/");
    }
}

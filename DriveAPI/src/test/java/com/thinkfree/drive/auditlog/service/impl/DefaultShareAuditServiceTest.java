package com.thinkfree.drive.auditlog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.support.ShareAuditSupport;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.user.data.UserData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * ShareAuditService가 각 이벤트에 대해 ThreadLocal 홀더에 채우는 context 내용, 그리고 순수 계산 헬퍼(권한 스냅샷 JSON·diff)의
 * 결과를 검증한다. 호출부(ShareUseCase/ShareTargetUseCase)가 <b>어떤 인자로 이 서비스를 호출하는지</b>는 각 UseCaseAuditTest에서 다룬다.
 */
@DisplayName("DefaultShareAuditService context 기록 내용 테스트")
class DefaultShareAuditServiceTest extends AuditMockTest {

    private static final int ACTOR_TENANT = 1;

    // 델타/스냅샷 JSON 직렬화가 필요해 실제 구현 주입(mock이면 값이 비어 검증 불가)
    @Spy private ObjectMapperUtil objectMapperUtil = new ObjectMapperUtil(new ObjectMapper());

    @InjectMocks
    private DefaultShareAuditService shareAuditService;

    private UserData userData(int userSeq, int tenantSeq, String tenantName) {
        UserData u = Mockito.mock(UserData.class);
        given(u.getUserSeq()).willReturn(userSeq);
        given(u.getUserId()).willReturn("user" + userSeq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + userSeq);
        given(u.getTenantSeq()).willReturn(tenantSeq);
        given(u.getTenantName()).willReturn(tenantName);
        return u;
    }

    private ShareWithTargetsData.ShareTargetItem targetItem(int userSeq) {
        var item = Mockito.mock(ShareWithTargetsData.ShareTargetItem.class);
        given(item.getUserSeq()).willReturn(userSeq);
        given(item.getUserId()).willReturn("user" + userSeq + "@thinkfree.com");
        given(item.getUserName()).willReturn("사용자" + userSeq);
        given(item.getCanView()).willReturn(true);
        given(item.getCanEdit()).willReturn(false);
        given(item.getCanDownload()).willReturn(true);
        given(item.getCanUpload()).willReturn(false);
        given(item.getCanShare()).willReturn(false);
        return item;
    }

    private ShareTargetUpsertDto upsertDto(int userSeq, boolean canEdit) {
        return ShareTargetUpsertDto.builder()
                .userSeq(userSeq)
                .canView(true)
                .canEdit(canEdit)
                .canDownload(true)
                .canUpload(false)
                .canShare(false)
                .build();
    }

    // EventName: SHARE_CREATE (링크 레벨 row)
    @Test
    @DisplayName("recordLinkCreated - 대상자 없는 링크 레벨 row, afterValue에 범위/기본권한 스냅샷")
    void recordLinkCreated_writesContext() {
        shareAuditService.recordLinkCreated(100L, "문서.docx", "/개발팀",
                "SPECIFIC_USERS", true, false, true, false, false);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SHARE_CREATE);
        assertThat(ctx.getObjectSeq()).isEqualTo(100L);
        assertThat(ctx.getObjectName()).isEqualTo("문서.docx");
        assertThat(ctx.getResourcePath()).isEqualTo("/개발팀");
        assertThat(ctx.getTargetUserSeq()).isNull();
        assertThat(ctx.getBeforeValue()).isNull();
        assertThat(ctx.getAfterValue()).contains("\"shareType\":\"SPECIFIC_USERS\"").contains("\"canView\":true");
    }

    // EventName: SHARE_REMOVE (링크 레벨 row)
    @Test
    @DisplayName("recordLinkRemoved - 대상자 없는 링크 레벨 row, beforeValue에 해제 전 범위/기본권한 스냅샷")
    void recordLinkRemoved_writesContext() {
        shareAuditService.recordLinkRemoved(100L, "문서.docx", "/개발팀",
                "SPECIFIC_USERS", true, false, true, false, false);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SHARE_REMOVE);
        assertThat(ctx.getTargetUserSeq()).isNull();
        assertThat(ctx.getBeforeValue()).contains("\"shareType\":\"SPECIFIC_USERS\"");
        assertThat(ctx.getAfterValue()).isNull();
    }

    // EventName: SHARE_CREATE (대상자별 fan-out)
    @Test
    @DisplayName("recordAll(SHARE_CREATE) - 대상자 수만큼 fan-out, 외부 테넌트 대상자만 targetTenant 채움")
    void recordAll_shareCreate_fanOutPerTarget() {
        UserData internal = userData(20, ACTOR_TENANT, "우리회사");
        UserData external = userData(21, 2, "HANCOM");
        var valueByUserSeq = java.util.Map.of(20, "{\"canView\":true}", 21, "{\"canView\":true}");

        shareAuditService.recordAll(EventName.SHARE_CREATE, Set.of(20, 21), List.of(internal, external),
                100L, "문서.docx", "/개발팀", ACTOR_TENANT, valueByUserSeq);

        var contexts = AuditLogContextHolder.getAll();
        assertThat(contexts).hasSize(2);

        AuditLogContext t20 = contexts.stream().filter(c -> Integer.valueOf(20).equals(c.getTargetUserSeq())).findFirst().orElseThrow();
        assertThat(t20.getEventName()).isEqualTo(EventName.SHARE_CREATE);
        assertThat(t20.getTargetTenantSeq()).isNull();
        assertThat(t20.getAfterValue()).isEqualTo("{\"canView\":true}");
        assertThat(t20.getBeforeValue()).isNull();

        AuditLogContext t21 = contexts.stream().filter(c -> Integer.valueOf(21).equals(c.getTargetUserSeq())).findFirst().orElseThrow();
        assertThat(t21.getTargetTenantSeq()).isEqualTo(2);
        assertThat(t21.getTargetTenantName()).isEqualTo("HANCOM");
    }

    // EventName: SHARE_REMOVE (대상자별 fan-out) - targetUserSeqSet == null이면 조용히 무기록
    @Test
    @DisplayName("recordAll - targetUserSeqSet이 null이면(링크만 공유) 아무것도 기록하지 않는다")
    void recordAll_nullTargetSet_recordsNothing() {
        shareAuditService.recordAll(EventName.SHARE_CREATE, null, List.of(), 100L, "문서.docx", "/개발팀", ACTOR_TENANT, null);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // EventName: SHARE_PERMISSION_CHANGE (링크 레벨)
    @Test
    @DisplayName("recordPermissionChange - 대상자 없는 범위 변경 row, before/after 둘 다 채워짐")
    void recordPermissionChange_writesContext() {
        shareAuditService.recordPermissionChange(100L, "문서.docx", "/개발팀", "{\"shareType\":\"SPECIFIC_USERS\"}", "{\"shareType\":\"PUBLIC_LINK\"}");

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SHARE_PERMISSION_CHANGE);
        assertThat(ctx.getTargetUserSeq()).isNull();
        assertThat(ctx.getBeforeValue()).isEqualTo("{\"shareType\":\"SPECIFIC_USERS\"}");
        assertThat(ctx.getAfterValue()).isEqualTo("{\"shareType\":\"PUBLIC_LINK\"}");
    }

    // EventName: SHARE_PERMISSION_CHANGE (대상자 1명)
    @Test
    @DisplayName("recordTargetPermissionChange - 외부 테넌트 대상자면 targetTenant를 채운다")
    void recordTargetPermissionChange_externalTenant_writesContext() {
        UserData external = userData(21, 2, "HANCOM");
        var change = new ShareAuditSupport.TargetPermissionChange(21, "user21@thinkfree.com", "사용자21", "{\"canEdit\":false}", "{\"canEdit\":true}");

        shareAuditService.recordTargetPermissionChange(100L, "문서.docx", "/개발팀", change, List.of(external), ACTOR_TENANT);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SHARE_PERMISSION_CHANGE);
        assertThat(ctx.getTargetUserSeq()).isEqualTo(21);
        assertThat(ctx.getTargetTenantSeq()).isEqualTo(2);
        assertThat(ctx.getBeforeValue()).isEqualTo("{\"canEdit\":false}");
        assertThat(ctx.getAfterValue()).isEqualTo("{\"canEdit\":true}");
    }

    // SHARE_CREATE afterValue 계산 헬퍼
    @Test
    @DisplayName("grantedPermissionByUserSeq - 대상자별로 요청이 부여한 권한 스냅샷 JSON을 만든다")
    void grantedPermissionByUserSeq_buildsSnapshotJson() {
        var items = List.of(new ShareTargetItemCommand(20, true, true, false, false, false, false));

        var result = shareAuditService.grantedPermissionByUserSeq(items);

        assertThat(result).containsKey(20);
        assertThat(result.get(20)).contains("\"canView\":true").contains("\"canEdit\":true").contains("\"canDownload\":false");
    }

    // SHARE_REMOVE beforeValue 계산 헬퍼
    @Test
    @DisplayName("currentPermissionByUserSeq - 대상자별 해제 전 권한 스냅샷 JSON을 만든다")
    void currentPermissionByUserSeq_buildsSnapshotJson() {
        var result = shareAuditService.currentPermissionByUserSeq(List.of(targetItem(20)));

        assertThat(result).containsKey(20);
        assertThat(result.get(20)).contains("\"canView\":true").contains("\"canDownload\":true");
    }

    // 유지 대상자 권한 diff 계산 헬퍼
    @Test
    @DisplayName("targetPermissionChanges - 유지되는 대상자 중 권한이 바뀐 건만 diff로 만든다")
    void targetPermissionChanges_onlyChangedRetainedTargets() {
        // 20: canEdit false→true(변경), 21: 변경 없음(canEdit false→false), 22: after에만 있음(신규, 대상 아님)
        var beforeTargets = List.of(targetItem(20), targetItem(21));
        var afterTargets = List.of(upsertDto(20, true), upsertDto(21, false), upsertDto(22, false));

        var changes = shareAuditService.targetPermissionChanges(beforeTargets, afterTargets);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).userSeq()).isEqualTo(20);
        assertThat(changes.get(0).beforeJson()).contains("\"canEdit\":false");
        assertThat(changes.get(0).afterJson()).contains("\"canEdit\":true");
    }

    // 공유 범위(shareType)/링크 기본 권한 변경 여부 판정 헬퍼
    @Test
    @DisplayName("computeScopeChange - shareType이 바뀌면 before/after 전체 스냅샷 diff를 만든다")
    void computeScopeChange_changed_returnsDiff() {
        var result = shareAuditService.computeScopeChange(
                "SPECIFIC_USERS", true, false, true, false, false,
                "PUBLIC_LINK", true, false, true, false, false);

        assertThat(result).isNotNull();
        assertThat(result.beforeJson()).contains("\"shareType\":\"SPECIFIC_USERS\"");
        assertThat(result.afterJson()).contains("\"shareType\":\"PUBLIC_LINK\"");
    }

    @Test
    @DisplayName("computeScopeChange - 변경이 없으면 null을 반환한다")
    void computeScopeChange_unchanged_returnsNull() {
        var result = shareAuditService.computeScopeChange(
                "SPECIFIC_USERS", true, false, true, false, false,
                "SPECIFIC_USERS", true, false, true, false, false);

        assertThat(result).isNull();
    }
}

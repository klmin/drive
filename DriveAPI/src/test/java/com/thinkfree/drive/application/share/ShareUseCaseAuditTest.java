package com.thinkfree.drive.application.share;

import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.share.command.ShareSaveCommand;
import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.application.share.command.ShareUpdateCommand;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.share.ShareAuditLookupService;
import com.thinkfree.drive.auditlog.service.ShareAuditService;
import com.thinkfree.drive.auditlog.support.ShareAuditSupport;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.data.SharedResourceData;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.result.ShareDeleteResult;
import com.thinkfree.drive.share.result.ShareSaveResult;
import com.thinkfree.drive.share.result.ShareUnshareResult;
import com.thinkfree.drive.share.result.ShareUpdateResult;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.share.target.dto.ShareTargetsReplaceDto;
import com.thinkfree.drive.application.share.validation.ShareWriteValidator;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ShareUseCase가 <b>ShareAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * SHARE_CREATE/SHARE_REMOVE/SHARE_PERMISSION_CHANGE가 채우는 context 내용, 권한 스냅샷 JSON,
 * 범위 변경 diff 계산 결과는 {@code DefaultShareAuditServiceTest}에서 검증한다.
 */
@DisplayName("ShareUseCase 감사 로그 기록 흐름 테스트")
class ShareUseCaseAuditTest extends AuditMockTest {

    private static final int ACTOR_TENANT = 1;
    private static final int ACTOR_USER_SEQ = 10;
    private static final String ACTOR_USER_ID = "user10@thinkfree.com";
    private static final String ACTOR_USER_NAME = "사용자10";

    @Mock private ResourceFindService resourceFindService;
    @Mock private ShareFindService shareFindService;
    @Mock private ShareService shareService;
    @Mock private ShareWriteValidator shareWriteValidator;
    @Mock private ShareAuditLookupService shareAuditLookupService;
    @Mock private ShareAuditService shareAuditService;
    @Mock private UserFindService userFindService;

    // 나머지 협력자는 감사 로그와 무관 - 기본 mock으로 주입만 됨
    @Mock private com.thinkfree.drive.application.share.converter.ShareCommandConverter shareCommandConverter;
    @Mock private com.thinkfree.drive.application.share.converter.ShareInfoConverter shareInfoConverter;
    @Mock private com.thinkfree.drive.application.share.converter.ShareTargetCommandConverter shareTargetCommandConverter;
    @Mock private com.thinkfree.drive.resource.converter.ResourceConverter resourceConverter;
    @Mock private com.thinkfree.drive.cleanup.share.service.ShareCleanUpService shareCleanUpService;
    @Mock private com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    @Mock private org.springframework.context.ApplicationEventPublisher publisher;

    @InjectMocks
    private ShareUseCase shareUseCase;

    private UserData userData(int userSeq, int tenantSeq, String tenantName) {
        UserData u = Mockito.mock(UserData.class);
        given(u.getUserSeq()).willReturn(userSeq);
        given(u.getUserId()).willReturn("user" + userSeq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + userSeq);
        given(u.getTenantSeq()).willReturn(tenantSeq);
        given(u.getTenantName()).willReturn(tenantName);
        return u;
    }

    // EventName: SHARE_CREATE
    @Test
    @DisplayName("공유 생성 - recordLinkCreated + 대상자 집합/권한 스냅샷으로 recordAll(SHARE_CREATE)를 호출한다")
    void save_recordsLinkRowAndPerTargetFanOut() {
        TenantContext.set(ACTOR_TENANT);

        ResourceDetailData resource = Mockito.mock(ResourceDetailData.class);
        given(resource.getResourceSeq()).willReturn(100L);
        given(resource.getResourceName()).willReturn("문서.docx");
        given(resource.getResourceType()).willReturn(ResourceType.FILE);
        given(resourceFindService.findDetail(any())).willReturn(resource);
        // resourcePath는 resource.getFullPath()가 아니라 부모 폴더 경로를 별도 조회해서 쓴다
        given(shareAuditLookupService.findResourcePath(any(), anyInt())).willReturn("/개발팀");

        UserData internal = userData(20, ACTOR_TENANT, "우리회사");
        UserData external = userData(21, 2, "HANCOM");
        var userDataList = List.of(internal, external);
        given(shareWriteValidator.validateAndResolveTargets(any(), anyInt(), any(), any())).willReturn(userDataList);

        // sendEmail=false 로 메일 발송 경로를 타지 않게 함
        var targets = List.of(
                new ShareTargetItemCommand(20, true, false, true, false, false, false),
                new ShareTargetItemCommand(21, true, true, false, false, false, false)
        );
        var command = new ShareSaveCommand(100L, ShareType.SPECIFIC_USERS, true, false, true, false, false, targets);

        given(shareService.saveShareWithTargets(any()))
                .willReturn(new ShareSaveResult(1L, 100L, 10, true, false, true, false, false, false,
                        Set.of(20, 21), 10, Instant.now()));

        var grantedMap = Map.of(20, "granted20", 21, "granted21");
        given(shareAuditService.grantedPermissionByUserSeq(targets)).willReturn(grantedMap);

        shareUseCase.save(command, ACTOR_USER_SEQ, ACTOR_USER_ID, ACTOR_USER_NAME);

        // 링크 레벨 row: command의 범위/기본 권한을 그대로 전달
        verify(shareAuditService).recordLinkCreated(100L, "문서.docx", "/개발팀",
                "SPECIFIC_USERS", true, false, true, false, false);
        // 대상자 fan-out: 대상자 집합 + grantedPermissionByUserSeq 결과를 그대로 전달
        verify(shareAuditService).recordAll(EventName.SHARE_CREATE, Set.of(20, 21), userDataList,
                100L, "문서.docx", "/개발팀", ACTOR_TENANT, grantedMap);
    }

    // EventName: SHARE_REMOVE
    @Test
    @DisplayName("공유 해제 - recordLinkRemoved(해제 전 범위 스냅샷) + 제거된 대상자 집합으로 recordAll(SHARE_REMOVE)를 호출한다")
    void delete_recordsLinkRowAndPerTargetRemoveWithBeforeValue() {
        TenantContext.set(ACTOR_TENANT);

        var sharedResource = Mockito.mock(SharedResourceData.class);
        given(sharedResource.getShareType()).willReturn(ShareType.SPECIFIC_USERS);
        given(shareFindService.findSharedResourceByShare(any(), anyInt(), anyBoolean()))
                .willReturn(sharedResource);

        // 해제 전 상태(범위 권한) - 삭제 후엔 조회 불가라 미리 캡처
        var beforeState = Mockito.mock(ShareWithTargetsData.class);
        given(beforeState.getCanView()).willReturn(true);
        given(beforeState.getCanEdit()).willReturn(false);
        given(beforeState.getCanDownload()).willReturn(true);
        given(beforeState.getCanUpload()).willReturn(false);
        given(beforeState.getCanShare()).willReturn(false);
        var beforeTargets = List.of(Mockito.mock(ShareWithTargetsData.ShareTargetItem.class));
        given(beforeState.getTargets()).willReturn(beforeTargets);
        given(shareAuditLookupService.findCurrentShareState(any(), anyInt())).willReturn(beforeState);

        var targetDeleteResult = new ShareDeleteResult(
                10, ShareType.SPECIFIC_USERS, ResourceType.FILE, null, null, false,
                100L, "문서.docx", Instant.now(), Set.of(20), Set.of());
        given(shareService.deleteShare(sharedResource)) // W9 D4 — UseCase 가 읽은 판단용 DTO 를 그대로 넘긴다
                .willReturn(new ShareUnshareResult(targetDeleteResult, null));

        given(shareAuditLookupService.findResourcePath(any(), anyInt())).willReturn("/개발팀");
        var removed20 = userData(20, ACTOR_TENANT, "우리회사");
        var removedUsers = List.of(removed20);
        given(userFindService.findVisibleUsers(any())).willReturn(removedUsers);

        var currentMap = Map.of(20, "current20");
        given(shareAuditService.currentPermissionByUserSeq(beforeTargets)).willReturn(currentMap);

        shareUseCase.delete(1L, ACTOR_USER_SEQ, ACTOR_USER_NAME);

        // 링크 레벨 해제 row: 해제 전(beforeState) 범위/권한을 그대로 전달
        verify(shareAuditService).recordLinkRemoved(100L, "문서.docx", "/개발팀",
                "SPECIFIC_USERS", true, false, true, false, false);
        // 제거된 대상자 fan-out: currentPermissionByUserSeq(해제 전 권한) 결과를 그대로 전달
        verify(shareAuditService).recordAll(EventName.SHARE_REMOVE, Set.of(20), removedUsers,
                100L, "문서.docx", "/개발팀", ACTOR_TENANT, currentMap);
    }

    // EventName: SHARE_PERMISSION_CHANGE
    @Test
    @DisplayName("공유 수정 - computeScopeChange가 변경을 반환하면 그 결과로 recordPermissionChange를 호출하고 fan-out은 호출하지 않는다")
    void update_scopeChange_recordsPermissionChange() {
        TenantContext.set(ACTOR_TENANT);

        var sharedResource = Mockito.mock(SharedResourceData.class);
        given(sharedResource.getShareType()).willReturn(ShareType.SPECIFIC_USERS); // 변경 전 범위
        given(sharedResource.getResourceSeq()).willReturn(100L);
        given(sharedResource.getResourceName()).willReturn("문서.docx");
        given(shareFindService.findSharedResourceByShare(any(), anyInt(), anyBoolean())).willReturn(sharedResource);

        given(shareWriteValidator.validateAndResolveTargets(any(), anyInt(), any(), any())).willReturn(List.of());

        // 변경 전 상태(대상자 없음 - 대상자 권한 변경분 없음)
        var beforeState = Mockito.mock(ShareWithTargetsData.class);
        given(beforeState.getCanView()).willReturn(true);
        given(beforeState.getCanEdit()).willReturn(false);
        given(beforeState.getCanDownload()).willReturn(true);
        given(beforeState.getCanUpload()).willReturn(false);
        given(beforeState.getCanShare()).willReturn(false);
        given(beforeState.getTargets()).willReturn(List.of());
        given(shareAuditLookupService.findCurrentShareState(any(), anyInt())).willReturn(beforeState);

        // 변경 후 dto - 범위(shareType)가 PUBLIC_LINK로 바뀜(scope 변경 트리거), 대상자 교체 없음
        var replaceDto = Mockito.mock(ShareTargetsReplaceDto.class);
        given(replaceDto.getTargets()).willReturn(List.of());
        var dto = Mockito.mock(ShareUpdateDto.class);
        given(dto.getShareType()).willReturn(ShareType.PUBLIC_LINK);
        given(dto.getCanView()).willReturn(true);
        given(dto.getCanEdit()).willReturn(false);
        given(dto.getCanDownload()).willReturn(true);
        given(dto.getCanUpload()).willReturn(false);
        given(dto.getCanShare()).willReturn(false);
        given(dto.getTargetsReplaceDto()).willReturn(replaceDto);
        given(shareCommandConverter.toShareUpdateDto(any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(dto);

        // 추가/제거 대상자 없음
        var deleteResult = new ShareDeleteResult(
                10, ShareType.SPECIFIC_USERS, ResourceType.FILE, null, null, false,
                100L, "문서.docx", Instant.now(), Set.of(), Set.of());
        given(shareService.updateShareWithTargets(any()))
                .willReturn(new ShareUpdateResult(10, Set.of(), deleteResult, null, null));

        given(shareAuditLookupService.findResourcePath(any(), anyInt())).willReturn("/개발팀");

        var scopeChange = new ShareAuditSupport.ScopeChange("before-json", "after-json");
        given(shareAuditService.computeScopeChange(
                "SPECIFIC_USERS", true, false, true, false, false,
                "PUBLIC_LINK", true, false, true, false, false))
                .willReturn(scopeChange);

        var command = new ShareUpdateCommand(ShareType.PUBLIC_LINK, true, false, true, false, false, List.of());
        shareUseCase.update(1L, command, ACTOR_USER_SEQ, ACTOR_USER_ID, ACTOR_USER_NAME);

        verify(shareAuditService).recordPermissionChange(100L, "문서.docx", "/개발팀", "before-json", "after-json");
        verify(shareAuditService, never()).recordAll(any(), any(), any(), any(), any(), any(), any(), any());
        verify(shareAuditService, never()).recordTargetPermissionChange(any(), any(), any(), any(), any(), any());
    }
}

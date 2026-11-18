package com.thinkfree.drive.application.share;

import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.application.share.command.ShareTargetReplaceCommand;
import com.thinkfree.drive.application.share.validation.ShareWriteValidator;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.share.ShareAuditLookupService;
import com.thinkfree.drive.auditlog.service.ShareAuditService;
import com.thinkfree.drive.auditlog.support.ShareAuditSupport;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.data.SharedResourceData;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.result.ShareTargetDeletionResult;
import com.thinkfree.drive.share.target.result.ShareTargetReplaceResult;
import com.thinkfree.drive.share.target.service.ShareTargetService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 공유 문서함(공유받은 문서) 경로의 대상 교체 API가 <b>ShareAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * 이 usecase는 공유 범위(링크 자체)를 건드리지 않으므로 링크 레벨 recordLinkCreated/Removed 호출이 없다.
 * 기록 내용(context 필드)과 권한 스냅샷 JSON은 {@code DefaultShareAuditServiceTest}에서 검증한다.
 */
@DisplayName("ShareTargetUseCase 감사 로그 기록 흐름 테스트")
class ShareTargetUseCaseAuditTest extends AuditMockTest {

    private static final int ACTOR_TENANT = 1;
    private static final int ACTOR_USER_SEQ = 10;
    private static final String ACTOR_USER_ID = "user10@thinkfree.com";
    private static final String ACTOR_USER_NAME = "사용자10";
    private static final long SHARE_SEQ = 1L;
    private static final long RESOURCE_SEQ = 100L;

    @Mock private ShareFindService shareFindService;
    @Mock private ShareTargetService shareTargetService;
    @Mock private ShareWriteValidator shareWriteValidator;
    @Mock private ShareAuditLookupService shareAuditLookupService;
    @Mock private ShareAuditService shareAuditService;
    @Mock private UserFindService userFindService;
    @Mock private com.thinkfree.drive.application.share.converter.ShareTargetCommandConverter shareTargetCommandConverter;

    // 나머지 협력자는 감사 로그와 무관 - 기본 mock으로 주입만 됨
    @Mock private com.thinkfree.drive.application.share.converter.SharedCommandConverter sharedCommandConverter;
    @Mock private com.thinkfree.drive.application.share.converter.SharedInfoConverter sharedInfoConverter;
    @Mock private com.thinkfree.drive.share.target.service.ShareTargetFindService shareTargetFindService;
    @Mock private com.thinkfree.drive.cleanup.share.service.ShareCleanUpService shareCleanUpService;
    @Mock private com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    @Mock private org.springframework.context.ApplicationEventPublisher publisher;

    @InjectMocks
    private ShareTargetUseCase shareTargetUseCase;

    private UserData userData(int userSeq, int tenantSeq, String tenantName) {
        UserData u = Mockito.mock(UserData.class);
        given(u.getUserSeq()).willReturn(userSeq);
        given(u.getUserId()).willReturn("user" + userSeq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + userSeq);
        given(u.getTenantSeq()).willReturn(tenantSeq);
        given(u.getTenantName()).willReturn(tenantName);
        return u;
    }

    private ShareTargetUpsertDto upsertDto(int userSeq, boolean canEdit) {
        return ShareTargetUpsertDto.builder()
                .userSeq(userSeq)
                .shareSeq(SHARE_SEQ)
                .resourceSeq(RESOURCE_SEQ)
                .sharedByUserSeq(ACTOR_USER_SEQ)
                .canView(true)
                .canEdit(canEdit)
                .canDownload(true)
                .canUpload(false)
                .canShare(false)
                .build();
    }

    @Test
    @DisplayName("대상 교체 - 추가/제거/유지 대상자에 맞춰 ShareAuditService의 CREATE/REMOVE/PERMISSION_CHANGE 기록 메서드를 호출한다")
    void replaceShareTargets_recordsCreateRemoveAndPermissionChange() {
        TenantContext.set(ACTOR_TENANT);

        var sharedResource = Mockito.mock(SharedResourceData.class);
        given(sharedResource.getShareSeq()).willReturn(SHARE_SEQ);
        given(sharedResource.getResourceSeq()).willReturn(RESOURCE_SEQ);
        given(sharedResource.getResourceName()).willReturn("문서.docx");
        given(sharedResource.getResourceType()).willReturn(ResourceType.FILE);
        given(sharedResource.getShareType()).willReturn(ShareType.SPECIFIC_USERS);
        given(sharedResource.getUserSeq()).willReturn(ACTOR_USER_SEQ);
        given(shareFindService.findSharedResourceByShare(any(), anyInt(), anyBoolean())).willReturn(sharedResource);

        // 변경 전 대상자(내용은 opaque - targetPermissionChanges 호출 시 같은 참조로 전달되는지만 확인)
        var beforeTargets = List.of(
                Mockito.mock(ShareWithTargetsData.ShareTargetItem.class),
                Mockito.mock(ShareWithTargetsData.ShareTargetItem.class));
        var beforeState = Mockito.mock(ShareWithTargetsData.class);
        given(beforeState.getTargets()).willReturn(beforeTargets);
        given(shareAuditLookupService.findCurrentShareState(any(), anyInt())).willReturn(beforeState);
        given(shareAuditLookupService.findResourcePath(any(), anyInt())).willReturn("SHARE/개발팀");

        // 요청 후 대상자: 20(유지), 22(신규·외부 테넌트)
        UserData retained = userData(20, ACTOR_TENANT, "우리회사");
        UserData added = userData(22, 2, "HANCOM");
        var userDataList = List.of(retained, added);
        given(shareWriteValidator.validateTargetWrite(anyInt(), any(), any(), any())).willReturn(userDataList);
        var replacedTargets = List.of(upsertDto(20, true), upsertDto(22, false));
        given(shareTargetCommandConverter.toShareTargetUpsertDtoList(any(), any(), any(), anyInt()))
                .willReturn(replacedTargets);

        // 제거된 21은 새 대상 목록에 없어 따로 조회한다
        var removedUsers = List.of(userData(21, ACTOR_TENANT, "우리회사"));
        given(userFindService.findVisibleUsers(any())).willReturn(removedUsers);

        var deletionResult = ShareTargetDeletionResult.targetFile(List.of(), List.of(new ShareTargetDeleteKey(21, 500L)));
        given(shareTargetService.replaceShareTargets(any()))
                .willReturn(new ShareTargetReplaceResult(ACTOR_USER_SEQ, Set.of(22), deletionResult, Instant.now()));

        var permissionChange = new ShareAuditSupport.TargetPermissionChange(20, "user20@thinkfree.com", "사용자20", "before20", "after20");
        given(shareAuditService.targetPermissionChanges(beforeTargets, replacedTargets)).willReturn(List.of(permissionChange));

        var targets = List.of(
                new ShareTargetItemCommand(20, true, true, true, false, false, false),
                new ShareTargetItemCommand(22, true, false, true, false, false, false)
        );
        var grantedMap = Map.of(22, "granted22");
        given(shareAuditService.grantedPermissionByUserSeq(targets)).willReturn(grantedMap);

        var currentMap = Map.of(21, "current21");
        given(shareAuditService.currentPermissionByUserSeq(beforeTargets)).willReturn(currentMap);

        var command = new ShareTargetReplaceCommand(ACTOR_USER_SEQ, targets);

        shareTargetUseCase.replaceShareTargets(SHARE_SEQ, command, ACTOR_USER_SEQ, ACTOR_USER_ID, ACTOR_USER_NAME);

        // 추가된 대상자(22) - grantedPermissionByUserSeq 결과를 그대로 전달
        verify(shareAuditService).recordAll(EventName.SHARE_CREATE, Set.of(22), userDataList,
                RESOURCE_SEQ, "문서.docx", "SHARE/개발팀", ACTOR_TENANT, grantedMap);
        // 제거된 대상자(21) - currentPermissionByUserSeq(해제 전 권한) 결과를 그대로 전달
        verify(shareAuditService).recordAll(EventName.SHARE_REMOVE, Set.of(21), removedUsers,
                RESOURCE_SEQ, "문서.docx", "SHARE/개발팀", ACTOR_TENANT, currentMap);
        // 유지되며 권한만 바뀐 대상자(20) - targetPermissionChanges가 돌려준 변경분을 그대로 전달
        verify(shareAuditService).recordTargetPermissionChange(RESOURCE_SEQ, "문서.docx", "SHARE/개발팀", permissionChange, userDataList, ACTOR_TENANT);
    }

    @Test
    @DisplayName("대상 교체 - 실제 변화가 없으면 ShareAuditService의 기록 메서드를 하나도 호출하지 않는다(경로 조회도 하지 않음)")
    void replaceShareTargets_noChange_recordsNothing() {
        TenantContext.set(ACTOR_TENANT);

        var sharedResource = Mockito.mock(SharedResourceData.class);
        given(sharedResource.getShareSeq()).willReturn(SHARE_SEQ);
        given(sharedResource.getResourceSeq()).willReturn(RESOURCE_SEQ);
        given(sharedResource.getShareType()).willReturn(ShareType.SPECIFIC_USERS);
        given(sharedResource.getUserSeq()).willReturn(ACTOR_USER_SEQ);
        given(shareFindService.findSharedResourceByShare(any(), anyInt(), anyBoolean())).willReturn(sharedResource);

        var beforeTargets = List.of(Mockito.mock(ShareWithTargetsData.ShareTargetItem.class));
        var beforeState = Mockito.mock(ShareWithTargetsData.class);
        given(beforeState.getTargets()).willReturn(beforeTargets);
        given(shareAuditLookupService.findCurrentShareState(any(), anyInt())).willReturn(beforeState);

        var retainedOnly = List.of(userData(20, ACTOR_TENANT, "우리회사"));
        given(shareWriteValidator.validateTargetWrite(anyInt(), any(), any(), any())).willReturn(retainedOnly);
        given(shareTargetCommandConverter.toShareTargetUpsertDtoList(any(), any(), any(), anyInt()))
                .willReturn(List.of(upsertDto(20, false)));

        given(shareTargetService.replaceShareTargets(any()))
                .willReturn(new ShareTargetReplaceResult(ACTOR_USER_SEQ, Set.of(), ShareTargetDeletionResult.targetFile(List.of(), List.of()), Instant.now()));

        var command = new ShareTargetReplaceCommand(ACTOR_USER_SEQ,
                List.of(new ShareTargetItemCommand(20, true, false, true, false, false, false)));

        shareTargetUseCase.replaceShareTargets(SHARE_SEQ, command, ACTOR_USER_SEQ, ACTOR_USER_ID, ACTOR_USER_NAME);

        verify(shareAuditService, never()).recordAll(any(), any(), any(), any(), any(), any(), any(), any());
        verify(shareAuditService, never()).recordTargetPermissionChange(any(), any(), any(), any(), any(), any());
        verify(shareAuditLookupService, never()).findResourcePath(any(), anyInt());
    }
}

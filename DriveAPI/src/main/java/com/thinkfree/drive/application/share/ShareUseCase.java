package com.thinkfree.drive.application.share;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.application.share.command.*;
import com.thinkfree.drive.application.share.converter.ShareCommandConverter;
import com.thinkfree.drive.application.share.converter.ShareInfoConverter;
import com.thinkfree.drive.application.share.converter.ShareTargetCommandConverter;
import com.thinkfree.drive.application.share.info.*;
import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import com.thinkfree.drive.application.share.validation.ShareWriteValidator;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.share.ShareAuditLookupService;
import com.thinkfree.drive.auditlog.service.ShareAuditService;
import com.thinkfree.drive.cleanup.share.dto.ShareCleanUpDto;
import com.thinkfree.drive.cleanup.share.dto.ShareExternalCleanUpDto;
import com.thinkfree.drive.cleanup.share.service.ShareCleanUpService;
import com.thinkfree.drive.event.share.target.ShareTargetReplacedEvent;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.TenantGuard;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessShareDto;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessShareTargetChangeDto;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessUnshareDto;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.share.dto.ShareSaveDto;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.result.ShareDeleteResult;
import com.thinkfree.drive.share.result.ShareUpdateResult;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.util.UserUtil;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

/**
 * 진입점(내부 API·MCP) 공용 공유(share 행) usecase — 등록/수정/해제/상세 + 조회(대상 유저 목록·후보 검색·
 * 링크 접속·공유유형). 구 api.v1 ShareUseCase 전체 이관 + ShareDetailUseCase 흡수(과도기 분리분).
 * 대상만 교체하는 PUT targets는 {@link ShareTargetUseCase} 소속.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 권한 검사는 어댑터 책임 — 웹은 controller aspect(@CheckSharePermission 등), MCP는 툴의 명시 호출.
 * 단 링크 접속은 익명 접근 경로라 검사가 usecase 안에 있다(웹 시절과 동일).
 * CustomUserDetails는 (requestUserSeq, userId, userName)으로 해체해 받고, 미인증 허용인 링크 접속만
 * (userSeq/anonymous/tenantSeq nullable) 해체를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ShareUseCase {

    private final ShareCommandConverter shareCommandConverter;
    private final ShareInfoConverter shareInfoConverter;
    private final ShareTargetCommandConverter shareTargetCommandConverter;
    private final ResourceConverter resourceConverter;
    private final ResourceFindService resourceFindService;
    private final UserFindService userFindService;
    private final ShareFindService shareFindService;
    private final ShareService shareService;
    private final ShareCleanUpService shareCleanUpService;
    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    private final ApplicationEventPublisher publisher;
    private final ShareWriteValidator shareWriteValidator;
    private final ShareAuditLookupService shareAuditLookupService;
    private final ShareAuditService shareAuditService;
    private final ResourcePermissionService resourcePermissionService;
    private final ClientDomainService clientDomainService;
    private final ShareSettingFacade shareSettingFacade;

    @TenantFeatureRequired(TenantFeature.SHARE)
    public ShareDetailInfo detail(ShareDetailCommand command, int requestUserSeq) {
        var userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var shareWithTargetsData = shareFindService.findShareWithTargets(command.shareSeq(), userSeq, false);
        var sharedResourceNearestData = shareFindService.findNearestShareByShare(command.shareSeq(), userSeq, false);
        boolean modifiable = sharedResourceNearestData == null;
        return shareInfoConverter.toDetailInfo(shareWithTargetsData, modifiable);
    }

    @TenantFeatureRequired(TenantFeature.SHARE)
    public List<ShareUserInfo> findShareUsers(ShareDetailCommand command, int requestUserSeq) {
        var userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var shareUsers = shareFindService.findUsersByShare(command.shareSeq(), userSeq, false);
        return shareInfoConverter.toShareUserInfos(shareUsers);
    }

    @TenantFeatureRequired(TenantFeature.SHARE)
    public List<ShareUserInfo> searchUsers(ShareUserSearchCommand command, int requestUserSeq) {

        var ownerUserSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var resolved = shareSettingFacade.resolveByUserSeq(ownerUserSeq);
        var effective = resolved.effective();

        DriveAssert.throwIfFalse(effective.shareEnabled(), ShareErrorCode.SHARE_DISABLED);
        DriveAssert.throwIfFalse(effective.specificUsersAllowed(), ShareErrorCode.SPECIFIC_USERS_NOT_ALLOWED);

        Integer scopeTenantSeq = effective.externalShareAllowed() ? null : resolved.tenantSeq();

        List<UserData> userDataList = userFindService.findShareTargetCandidates(
                command.searchKeyword(), RoleType.USER, List.of(UserStatus.ACTIVE), scopeTenantSeq);

        return userDataList.stream()
                .filter(u -> (!Objects.equals(ownerUserSeq, u.getUserSeq())))
                .map(d -> new ShareUserInfo(
                        d.getUserSeq(), d.getUserId(), d.getUserName(), d.getStatus(), d.getTenantSeq(), d.getTenantName()
                ))
                .toList();
    }

    /**
     * 링크 접속 — 미인증(익명) 접근이 허용되는 유일한 경로라 CustomUserDetails 해체가 nullable 3종이다:
     * requestUserSeq/anonymous는 미인증이면 null, requestTenantSeq는 미인증이거나 테넌트 미보유면 null.
     */
    @TenantFeatureRequired(TenantFeature.SHARE)
    public ShareLinkAccessInfo accessShareLinkByToken(String token, Integer requestUserSeq, Boolean anonymous, Integer requestTenantSeq) {

        var data = resourceFindService.findResourceByShareToken(token, false);
        DriveAssert.throwIfNull(data, ShareErrorCode.INVALID_TOKEN);

        TenantGuard.verifyOwnership(data.getTenantSeq(), ShareErrorCode.INVALID_TOKEN);

        var shareData = shareFindService.findSharedResourceByResource(data.getResourceSeq(), data.getUserSeq(), false);
        boolean isPublicLink = shareData != null && shareData.getShareType().isPublicLink();

        if (requestUserSeq != null) {
            RequestActor actor = RequestActor.of(requestUserSeq, Boolean.TRUE.equals(anonymous));
            resourcePermissionService.checkPermission(SharePermissionType.VIEW, data.getResourceSeq(), data.getUserSeq(), actor, false);
        } else {
            DriveAssert.throwIfFalse(isPublicLink, PermissionErrorCode.ACCESS_DENIED);
        }

        int targetTenantSeq = requestTenantSeq == null ? data.getTenantSeq() : requestTenantSeq;

        var redirectUrl = clientDomainService.getDomain(targetTenantSeq);
        return new ShareLinkAccessInfo(data.getUserSeq(), data.getResourceSeq(), data.getResourceType(), redirectUrl, isPublicLink);
    }

    @TenantFeatureRequired(TenantFeature.SHARE)
    public ShareTypesInfo availableShareTypes(ShareTypesCommand command, int requestUserSeq){

        var userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);

        var resourceFindQuery = resourceConverter.toFindQuery(command.resourceSeq(), userSeq, null, false);
        var resource = resourceFindService.findDetail(resourceFindQuery);
        var resourceType = resource.getResourceType();

        var sharedResourceNearestData = shareFindService.findNearestShareByResource(command.resourceSeq(), userSeq, false);
        var effective = shareSettingFacade.resolveByUserSeq(resource.getUserSeq()).effective();

        boolean editable = sharedResourceNearestData == null;

        List<ShareTypesInfo.ShareTypesItemInfo> shareTypes = editable
                ? Arrays.stream(ShareType.values())
                        .map(type -> ShareTypesInfo.ShareTypesItemInfo.from(type, resourceType))
                        .toList()
                : List.of(ShareTypesInfo.ShareTypesItemInfo.from(sharedResourceNearestData.getShareType(), resourceType));

        return new ShareTypesInfo(
                editable,
                effective.specificUsersAllowed(),
                effective.orgLinkAllowed(),
                effective.publicAllowed(),
                shareTypes
        );
    }

    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.SHARE)
    public ShareSaveInfo save(ShareSaveCommand command, int requestUserSeq, String userId, String userName) {

        var resourceFindQuery = resourceConverter.toFindQuery(command.resourceSeq(), requestUserSeq, null, false);
        var resource = resourceFindService.findDetail(resourceFindQuery);
        var resourceSeq = resource.getResourceSeq();
        var sharedResourceNearestData = shareFindService.findNearestShareByResource(resourceSeq, requestUserSeq, false);

        var userDataList = shareWriteValidator.validateAndResolveTargets(sharedResourceNearestData, resource.getUserSeq(), command, ShareErrorCode.INSERT_FAIL);

        ShareSaveDto dto = buildShareSaveDto(command, requestUserSeq, resource.getResourceType());

        var result = shareService.saveShareWithTargets(dto);
        // 대상자 없는 공유(링크만 공유)는 addedUserSeqSet이 빈 Set이 아니라 null로 내려옴
        var addUserSeqSet = Objects.requireNonNullElse(result.addedUserSeqSet(), Set.<Integer>of());
        var mailUserSeqSet = ShareTargetValidationItem.resolveMailUserSeqSet(command.targets(), addUserSeqSet);

        var actorTenantSeq = TenantContext.getOrThrow();

        // resource.getFullPath()는 FILE이면 항상 null, FOLDER면 자기 이름까지 포함돼 다른 이벤트와 포맷이 어긋난다
        // — 다른 기록 지점과 동일하게 조회한 부모 폴더 경로를 쓴다(1회 조회분을 대상자 N건에 재사용)
        var resourcePath = shareAuditLookupService.findResourcePath(resourceSeq, requestUserSeq);

        shareAuditService.recordLinkCreated(resourceSeq, resource.getResourceName(), resourcePath,
                command.shareType().name(), command.canView(), command.canEdit(), command.canDownload(), command.canUpload(), command.canShare());
        shareAuditService.recordAll(EventName.SHARE_CREATE, addUserSeqSet, userDataList, resourceSeq, resource.getResourceName(), resourcePath, actorTenantSeq,
                shareAuditService.grantedPermissionByUserSeq(command.targets()));

        var postProcessShareDto = new PostProcessShareDto(requestUserSeq, userName, resourceSeq, resource.getResourceName(), addUserSeqSet, result.actorUserSeq(), result.occurredDatetime(), actorTenantSeq);
        postProcessingOrchestratorRetry.processSharedAndNotify(postProcessShareDto);

        if(!mailUserSeqSet.isEmpty()){
            var shareTargetUserEmails = UserUtil.extractUserIds(mailUserSeqSet, userDataList);
            var locale = LocaleContextHolder.getLocale();
            ShareTargetReplacedEvent shareTargetReplacedEvent = new ShareTargetReplacedEvent(TenantContext.getOrThrow(), userId, userName, resource.getResourceType(), resource.getResourceName(), shareTargetUserEmails, locale);
            publisher.publishEvent(shareTargetReplacedEvent);
        }

        return shareInfoConverter.toSaveInfo(result);
    }

    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.SHARE)
    public void update(Long shareSeq, ShareUpdateCommand command, int requestUserSeq, String userId, String userName){

        var sharedResource = shareFindService.findSharedResourceByShare(shareSeq, requestUserSeq, false);
        var sharedResourceNearestData = shareFindService.findNearestShareByShare(shareSeq, requestUserSeq, false);
        var resourceSeq = sharedResource.getResourceSeq();
        var resourceName = sharedResource.getResourceName();

        var userDataList = shareWriteValidator.validateAndResolveTargets(sharedResourceNearestData, sharedResource.getUserSeq(), command, ShareErrorCode.UPDATE_FAIL);

        ShareUpdateDto dto = buildShareUpdateDto(command, sharedResource.getShareType(), shareSeq, sharedResource.getResourceType(), resourceSeq, resourceName, requestUserSeq, sharedResource.getTenantSeq());

        var beforeShareState = shareAuditLookupService.findCurrentShareState(shareSeq, requestUserSeq);

        var shareUpdateResult = shareService.updateShareWithTargets(dto);
        // save()와 동일하게 대상자 없는 케이스는 addedUserSeqSet이 null로 내려올 수 있음
        var addedUserSeqSet = Objects.requireNonNullElse(shareUpdateResult.addedUserSeqSet(), Set.<Integer>of());
        var deleteResult = shareUpdateResult.deleteResult();
        var removedUserSeqSet = deleteResult.deleteShareTargetUserSeqList();

        var actorTenantSeq = TenantContext.getOrThrow();

        var scopeChange = shareAuditService.computeScopeChange(
                sharedResource.getShareType().name(), beforeShareState.getCanView(), beforeShareState.getCanEdit(), beforeShareState.getCanDownload(), beforeShareState.getCanUpload(), beforeShareState.getCanShare(),
                dto.getShareType().name(), dto.getCanView(), dto.getCanEdit(), dto.getCanDownload(), dto.getCanUpload(), dto.getCanShare());
        var targetPermissionChanges = shareAuditService.targetPermissionChanges(beforeShareState.getTargets(), dto.getTargetsReplaceDto().getTargets());

        if (!addedUserSeqSet.isEmpty() || !removedUserSeqSet.isEmpty() || scopeChange != null || !targetPermissionChanges.isEmpty()) {
            var resourcePath = shareAuditLookupService.findResourcePath(resourceSeq, requestUserSeq);

            if (!addedUserSeqSet.isEmpty() || !removedUserSeqSet.isEmpty()) {
                shareAuditService.recordAll(EventName.SHARE_CREATE, addedUserSeqSet, userDataList, resourceSeq, resourceName, resourcePath, actorTenantSeq,
                        shareAuditService.grantedPermissionByUserSeq(command.targets()));

                if (!removedUserSeqSet.isEmpty()) {
                    var removedUserDataList = userFindService.findVisibleUsers(new ArrayList<>(removedUserSeqSet));
                    shareAuditService.recordAll(EventName.SHARE_REMOVE, removedUserSeqSet, removedUserDataList, resourceSeq, resourceName, resourcePath, actorTenantSeq,
                            shareAuditService.currentPermissionByUserSeq(beforeShareState.getTargets()));
                }
            }

            if (scopeChange != null) {
                shareAuditService.recordPermissionChange(resourceSeq, resourceName, resourcePath, scopeChange.beforeJson(), scopeChange.afterJson());
            }

            // 권한만 바뀐 대상자는 새 대상 목록(userDataList)에 그대로 있어 거기서 테넌트를 찾는다.
            for (var change : targetPermissionChanges) {
                shareAuditService.recordTargetPermissionChange(resourceSeq, resourceName, resourcePath, change, userDataList, actorTenantSeq);
            }
        }

        var postProcessShareTargetChangeNotifyDto = new PostProcessShareTargetChangeDto(requestUserSeq, userName, resourceSeq, resourceName, addedUserSeqSet, removedUserSeqSet,
                requestUserSeq, deleteResult.occurredDatetime(), actorTenantSeq);
        postProcessingOrchestratorRetry.processShareTargetChangedAndNotify(postProcessShareTargetChangeNotifyDto);

        dispatchUpdateCleanUp(shareUpdateResult);

        var mailUserSeqSet = ShareTargetValidationItem.resolveMailUserSeqSet(command.targets(), addedUserSeqSet);

        if(mailUserSeqSet.isEmpty()){
            return;
        }

        var shareTargetUserEmails = UserUtil.extractUserIds(mailUserSeqSet, userDataList);
        var locale = LocaleContextHolder.getLocale();
        ShareTargetReplacedEvent shareTargetReplacedEvent = new ShareTargetReplacedEvent(actorTenantSeq, userId, userName, sharedResource.getResourceType(), sharedResource.getResourceName(), shareTargetUserEmails, locale);
        publisher.publishEvent(shareTargetReplacedEvent);

    }

    private ShareSaveDto buildShareSaveDto(ShareSaveCommand command, int userSeq, ResourceType resourceType) {

        // 공유 유형과 무관하게 대상이 있으면 함께 저장 (링크 공유에도 개별 대상 허용)
        var targets = (command.targets() == null || command.targets().isEmpty())
                ? null
                : shareTargetCommandConverter.toShareTargetSaveDtoList(command.targets(), command.resourceSeq(), userSeq);
        return shareCommandConverter.toShareSaveDto(command, userSeq, resourceType, targets);
    }

    private ShareUpdateDto buildShareUpdateDto(ShareUpdateCommand command, ShareType previousShareType, Long shareSeq, ResourceType resourceType, Long resourceSeq, String resourceName, int userSeq, Integer ownerTenantSeq) {

        // 대상 전체 교체(replace). 대상이 없으면(null/empty) 빈 목록으로 교체 → 기존 대상 전부 삭제(+해제 알림).
        // 모든 유형(제한됨 포함)에서 빈 대상 허용 — 제한됨 + 빈 대상이면 소유자만 접근 가능한 공유 행만 남는다.
        List<ShareTargetItemCommand> items = command.targets() == null ? List.of() : command.targets();
        var targets = shareTargetCommandConverter.toShareTargetUpsertDtoList(items, shareSeq, resourceSeq, userSeq);
        var targetsReplaceDto = shareTargetCommandConverter.toShareTargetsReplaceDto(shareSeq, userSeq, command.shareType(), resourceType, resourceSeq, targets);
        return shareCommandConverter.toShareUpdateDto(command, previousShareType, shareSeq, userSeq, ownerTenantSeq, resourceType, resourceSeq, resourceName, targetsReplaceDto);
    }

    /**
     * # org_link 해제시
     * 1. 해당 shareSeq로 상향 탐색.
     *     1-1. 데이터가 있다면 share 만 삭제. 최근 중요 데이터 삭제 X
     * <p>
     *     1-2. 데이터가 없다면.
     *         1-2-1. 파일이라면 share, 최근, 중요 데이터 삭제
     *         1-2-2. 폴더라면 하향 탐색. 이 하향탐색 resource들로 share조회.
     *                조회된 share resource의 하위 하이어라키를 제외한 resource 들 삭제.
     *                (sharedByUserSeq 하나, resourceSeq List) : 최근, 중요, share
     * </p>
     * <p>
     * # sepecific_users 해제시
     * 1. 해당 shareSeq 로 대상 등록 유저 추출.
     *     1-1. 파일이라면 상향탐색에 포함되지 않는 유저 리스트 추출 - 최근, 중요 문서 삭제. share_target 전체 삭제.
     *     1-2. 폴더라면 하향 탐색. 이 하향탐색 resource들 유저별로 share_target 조회. 조회된 share_taget의 resource의 하위 하이어라키를 제외한 유저 별 resource 들 삭제.
     *          List(userId, resourceSeqList) - 개별 삭제 bulk : 최근, 중요, share_target
     * </p>
     * @param shareSeq 공유 seq
     * @param requestUserSeq 요청 유저 seq
     * @param userName 요청 유저 이름 (해제 알림 표기용)
     */
    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.SHARE)
    public void delete(long shareSeq, int requestUserSeq, String userName) {

        // 삭제(deleteShare) 전에 "해제 전" 링크 상태(범위/권한)를 스냅샷 — 삭제 후엔 조회 불가.
        var sharedResource = shareFindService.findSharedResourceByShare(shareSeq, requestUserSeq, false);
        var beforeShareState = shareAuditLookupService.findCurrentShareState(shareSeq, requestUserSeq);

        var result = shareService.deleteShare(sharedResource);

        // 해제 알림(명시 target 기준)은 대상 채널이 운반 — 빠진 target 전원
        var targetDeleteResult = result.targetDeleteResult();
        var actorTenantSeq = TenantContext.getOrThrow();
        var removedUserSeqSet = targetDeleteResult.deleteShareTargetUserSeqList();
        var postProcessUnshareDto = new PostProcessUnshareDto(requestUserSeq, userName, targetDeleteResult.resourceSeq(), targetDeleteResult.fileName(), removedUserSeqSet, requestUserSeq, targetDeleteResult.occurredDatetime(), actorTenantSeq);
        postProcessingOrchestratorRetry.processUnsharedAndNotify(postProcessUnshareDto);

        var resourcePath = shareAuditLookupService.findResourcePath(targetDeleteResult.resourceSeq(), requestUserSeq);

        shareAuditService.recordLinkRemoved(targetDeleteResult.resourceSeq(), targetDeleteResult.fileName(), resourcePath,
                sharedResource.getShareType().name(), beforeShareState.getCanView(), beforeShareState.getCanEdit(), beforeShareState.getCanDownload(), beforeShareState.getCanUpload(), beforeShareState.getCanShare());

        if (!removedUserSeqSet.isEmpty()) {
            var removedUserDataList = userFindService.findVisibleUsers(new ArrayList<>(removedUserSeqSet));
            shareAuditService.recordAll(EventName.SHARE_REMOVE, removedUserSeqSet, removedUserDataList, targetDeleteResult.resourceSeq(), targetDeleteResult.fileName(), resourcePath, actorTenantSeq,
                    shareAuditService.currentPermissionByUserSeq(beforeShareState.getTargets()));
        }

        // 메타 정리: 대상 채널(SPECIFIC) + 링크 채널(링크 유형만) 둘 다 cascade. dto.shareType 으로 방식 라우팅.
        Stream.of(result.targetDeleteResult(), result.linkDeleteResult())
              .filter(Objects::nonNull)
              .map(this::createShareClanUpDto)
              .forEach(shareCleanUpService::deleteShareCascade);
    }

    private ShareCleanUpDto createShareClanUpDto(ShareDeleteResult deleteResult) {
        return new ShareCleanUpDto(deleteResult.sharedByUserSeq(), deleteResult.shareType(), deleteResult.cleanUpPartitionKeys(), deleteResult.cleanUpUserResourceGroups(), deleteResult.deleteDescendants(), deleteResult.preserveUserSeqSet());
    }

    /**
     * update 로 발생한 정리분 dispatch.
     * 대상 교체분(항상)·링크 소멸분(링크->제한됨 전환 시)은 같은 cascade(dto.shareType 으로 방식 라우팅),
     * 링크 축소분(전체공개->조직링크)은 외부 테넌트 전용 cascade.
     */
    private void dispatchUpdateCleanUp(ShareUpdateResult result) {

        Stream.of(result.deleteResult(), result.linkDeleteResult())
              .filter(Objects::nonNull)
              .map(this::createShareClanUpDto)
              .forEach(shareCleanUpService::deleteShareCascade);

        var externalShrinkResult = result.externalShrinkResult();
        if(externalShrinkResult != null){
            shareCleanUpService.deleteOtherTenantShareCascade(new ShareExternalCleanUpDto(externalShrinkResult.sharedByUserSeq(), externalShrinkResult.ownerTenantSeq(),
                    externalShrinkResult.cleanUpPartitionKeys(), externalShrinkResult.deleteDescendants(), externalShrinkResult.preserveUserSeqSet()));
        }
    }

}

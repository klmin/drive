package com.thinkfree.drive.share.service.impl;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.context.ShareDeletionContext;
import com.thinkfree.drive.share.converter.ShareConverter;
import com.thinkfree.drive.share.converter.ShareResultConverter;
import com.thinkfree.drive.share.dto.ShareMoveImpactDto;
import com.thinkfree.drive.share.dto.ShareSaveDto;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.data.SharedResourceData;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareCleanupAction;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.share.result.ShareDeleteResult;
import com.thinkfree.drive.share.result.ShareExternalShrinkResult;
import com.thinkfree.drive.share.result.ShareMoveImpactResult;
import com.thinkfree.drive.share.result.ShareSaveResult;
import com.thinkfree.drive.share.result.ShareUnshareResult;
import com.thinkfree.drive.share.result.ShareUpdateResult;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.share.target.converter.ShareTargetConverter;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.share.target.service.ShareTargetService;
import com.thinkfree.drive.share.validation.SharePermissionValidator;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultShareService implements ShareService {
    
    private final ShareConverter shareConverter;
    private final ShareResultConverter shareResultConverter;
    private final ShareRepository shareRepository;
    private final ShareTargetService shareTargetService;
    private final SharePermissionValidator sharePermissionValidator;
    private final ShareTargetConverter shareTargetConverter;
    private final ShareFindService shareFindService;
    
    @Override
    public int deleteByResource(long resourceSeq, int userSeq){
        var query = shareConverter.toShareDeleteResourceQuery(resourceSeq, userSeq);
        return shareRepository.deleteByResource(query);
    }

    @Override
    public int deleteByResources(Collection<Long> resourceSeqList, int userSeq){
        var query = shareConverter.toShareDeleteResourcesQuery(resourceSeqList, userSeq);
        return shareRepository.deleteByResources(query);
    }
    
    private Share save(ShareSaveDto dto) {
        var entity = shareConverter.toSaveShare(dto);
        return shareRepository.save(entity);
    }
    
    @Override
    public ShareSaveResult saveShareWithTargets(ShareSaveDto dto) {

        var shareType = dto.getShareType();
        
        if(shareType.isPublic()) {
            validPermission(dto, shareType, dto.getResourceType());
        }

        Share entity = save(dto);

        // 공유 유형과 무관하게 대상이 있으면 저장 (대상별 권한은 insertShareTargets 에서 매트릭스 검증)
        var targets = dto.getTargets();
        if(targets == null || targets.isEmpty()){
            return shareResultConverter.toShareSaveResult(entity, null);
        }

        var shareSeq = entity.getShareSeq();
        var targetsWithSeq = targets.stream().peek(r -> r.setShareSeq(shareSeq)).toList();
        var shareTargetsSaveDto = shareTargetConverter.toShareTargetsSaveDto(shareType, dto.getResourceType(), targetsWithSeq);
        var shareTargetSaveResult = shareTargetService.insertShareTargets(shareTargetsSaveDto);

        return shareResultConverter.toShareSaveResult(entity, shareTargetSaveResult);
    }
    
    @Override
    public ShareUpdateResult updateShareWithTargets(ShareUpdateDto dto) {

        var shareType = dto.getShareType();
        var previousShareType = dto.getPreviousShareType();
        var userSeq = dto.getUserSeq();
        var shareSeq = dto.getShareSeq();
        var resourceSeq = dto.getResourceSeq();
        var resourceType = dto.getResourceType();

        // 링크 공유(org/public)는 공유 행(링크) 권한을 매트릭스로 검증. specific 의 공유 행 CAN_* 는 미사용이라 검증하지 않음
        if(shareType.isPublic()){
            validPermission(dto, shareType, resourceType);
        }

        // 링크 도달범위가 줄어드는 전환이면 정리 범위(파일=리소스, 폴더=하위 트리) 계산. 두 전환은 상호배타.
        boolean linkRemoved = previousShareType.isPublic() && !shareType.isPublic();                                  // 링크 소멸: 링크 -> 제한됨
        boolean linkShrunk = previousShareType == ShareType.PUBLIC_LINK && shareType == ShareType.ORG_LINK;           // 링크 축소: 전체공개 -> 조직링크
        ShareDeletionContext linkScopeContext = linkRemoved || linkShrunk
                ? calculateLinkDeleteContext(shareSeq, userSeq, resourceType, resourceSeq)
                : null;

        // 대상 전체 교체 — 빈 목록이면 기존 대상 전부 삭제. 호출부(usecase)가 항상 non-null replaceDto 를 보장한다.
        var replaceResult = shareTargetService.replaceShareTargets(dto.getTargetsReplaceDto());
        Set<Integer> addedUserSeqSet = replaceResult.addedUserSeqSet();
        ShareDeletionContext targetDeletionContext = ShareDeletionContext.from(replaceResult.shareTargetDeletionResult());

        update(dto);

        // 정리 이벤트 시각 = 서비스가 정한 현재 시각(대상 교체·공유 삭제와 같은 방식, W9 D1 — 공유 행 UPDATE 는 커밋 때 변경 감지라 여기서 수정일을 읽지 않는다)
        var occurredDatetime = Instant.now();

        // 대상 교체 정리는 공유 유형과 무관하게 대상(per-user) 방식이라 SPECIFIC_USERS 로 cleanup 라우팅
        var targetDeleteResult = toShareDeleteResult(targetDeletionContext, ShareType.SPECIFIC_USERS, dto, occurredDatetime, Set.of());

        // 링크 전환(소멸/축소)이면 교체 후 남는 target = 메타 보존(preserve) 대상. 두 전환은 상호배타라 한 번만 계산해 켜진 채널에 넘긴다.
        Set<Integer> remainingTargetUserSeqSet = (linkRemoved || linkShrunk) ? resolveRemainingTargetUserSeqSet(dto) : Set.of();

        // 링크 소멸 = 소유자·잔존 target 제외 전원 / 링크 축소 = 외부 테넌트(잔존 target 제외)만 정리
        var linkDeleteResult = linkRemoved
                ? toShareDeleteResult(linkScopeContext, previousShareType, dto, occurredDatetime, remainingTargetUserSeqSet)
                : null;
        var externalShrinkResult = linkShrunk
                ? new ShareExternalShrinkResult(userSeq, dto.getOwnerTenantSeq(), linkScopeContext.cleanUpPartitionKeys(), linkScopeContext.deleteDescendants(), remainingTargetUserSeqSet)
                : null;

        return new ShareUpdateResult(userSeq, addedUserSeqSet, targetDeleteResult, linkDeleteResult, externalShrinkResult);
    }

    /** 교체 후 남는 대상 = 넘어온 replaceDto 의 대상(빈 목록이면 없음). 호출부가 항상 non-null replaceDto·대상을 보장. */
    private Set<Integer> resolveRemainingTargetUserSeqSet(ShareUpdateDto dto){
        return dto.getTargetsReplaceDto().getTargets().stream()
                  .map(ShareTargetUpsertDto::getUserSeq)
                  .collect(Collectors.toSet());
    }

    private ShareDeleteResult toShareDeleteResult(ShareDeletionContext context, ShareType cleanUpShareType, ShareUpdateDto dto, Instant occurredDatetime, Set<Integer> preserveUserSeqSet){
        return new ShareDeleteResult(dto.getUserSeq(), cleanUpShareType, dto.getResourceType(), context.cleanUpPartitionKeys(), context.cleanUpUserResourceGroups(),
                context.deleteDescendants(), dto.getResourceSeq(), dto.getResourceName(), occurredDatetime, context.deleteShareTargetUserSeqSet(), preserveUserSeqSet);
    }
    
    private void validPermission(PermissionHolder permissionHolder, ShareType shareType, ResourceType resourceType){
        var permissions = shareConverter.toPermissions(permissionHolder);
        sharePermissionValidator.validate(shareType, resourceType, permissions);
    }
    
    /**
     * 관리 엔티티 1회 로드 → {@link Share#changeShare} → 커밋 때 변경 감지(감사는 Auditing, 값이 같으면 UPDATE 없음 — W9). 없으면 UPDATE_FAIL(구 0행과 같음).
     * flush 하지 않는다 — 이 트랜잭션에서 수정 뒤 tb_share 를 JdbcClient 로 다시 읽는 곳이 없다(권한 검증 조회는 수정 전, 정리·후처리는 다른 테이블).
     */
    private void update(ShareUpdateDto dto){
        var entity = shareRepository.findByUserSeqAndShareSeq(dto.getUserSeq(), dto.getShareSeq());
        DriveAssert.throwIfNull(entity, ShareErrorCode.UPDATE_FAIL);
        entity.changeShare(dto.getShareType(), dto);
    }
    
    private void delete(long shareSeq, int userSeq){
        var shareDeleteQuery = shareConverter.toShareDeleteQuery(shareSeq, userSeq);
        var result = shareRepository.delete(shareDeleteQuery);
        DriveAssert.throwIfZero(result, ShareErrorCode.DELETE_FAILED);
    }
    
    @Override
    public ShareUnshareResult deleteShare(SharedResourceData sharedResourceData){

        // 호출부(ShareUseCase.delete)가 같은 트랜잭션에서 방금 읽은 판단용 DTO 를 받는다(같은 조회 반복 제거, W9 D4)
        long shareSeq = sharedResourceData.getShareSeq();
        int userSeq = sharedResourceData.getUserSeq();
        var shareType = sharedResourceData.getShareType();
        var resourceType = sharedResourceData.getResourceType();
        var resourceSeq = sharedResourceData.getResourceSeq();
        var resourceName = sharedResourceData.getResourceName();

        // 공통(유형 무관): 이 공유의 target 행 삭제 + 빠진 target 유저 해제 알림. (새 모델은 링크 공유에도 개별 target 이 붙을 수 있음)
        var shareTargetDeleteResult = shareTargetService.calculateTargetsDeleteResult(shareSeq, userSeq, shareType, resourceType, resourceSeq);
        var shareTargetDeleteKeys = shareTargetDeleteResult.shareTargetDeleteKeys();
        var occurredDatetime = Instant.now();

        // 대상 채널(항상, SPECIFIC 라우팅): per-user 메타 정리. 해제 알림 대상(빠진 target 전원)도 이 채널이 운반.
        //   SPECIFIC = 빠진 대상 전원(상위 target 없는) / 링크 = keepsLinkAccess 통과 못한 target
        //   (ORG_LINK=외부 테넌트 & 상위 target 없는 / PUBLIC_LINK=∅). preserve 없음(전체 해제).
        var targetDeleteResult = new ShareDeleteResult(userSeq, ShareType.SPECIFIC_USERS, resourceType,
                shareTargetDeleteResult.cleanUpPartitionKeys(), shareTargetDeleteResult.cleanUpUserResourceGroups(), shareTargetDeleteResult.deleteDescendants(),
                resourceSeq, resourceName, occurredDatetime, shareTargetDeleteResult.deleteShareTargetUserSeqSet(), Set.of());

        // 링크 채널(링크 유형만): 도달 범위(소유자 제외 전원) sweep. 상위 공유 있으면 빈 컨텍스트(보존).
        // 같은 테넌트 유저는 이 채널이, 외부 테넌트 target 은 위 대상 채널이 정리 → 둘이 합쳐 룰셋 충족.
        ShareDeleteResult linkDeleteResult = null;
        if(shareType.isPublic()){
            var linkDeletionContext = calculateLinkDeleteContext(shareSeq, userSeq, resourceType, resourceSeq);
            linkDeleteResult = new ShareDeleteResult(userSeq, shareType, resourceType,
                    linkDeletionContext.cleanUpPartitionKeys(), linkDeletionContext.cleanUpUserResourceGroups(), linkDeletionContext.deleteDescendants(),
                    resourceSeq, resourceName, occurredDatetime, Set.of(), Set.of());
        }

        if(!shareTargetDeleteKeys.isEmpty()){
            shareTargetService.deleteByShareTargetDeleteKeys(shareTargetDeleteKeys, userSeq);
        }

        delete(shareSeq, userSeq);
        
        return new ShareUnshareResult(targetDeleteResult, linkDeleteResult);
    }
    
    /**
     * 리소스 이동(move) 시 적용할 "공유 정리 액션"을 계산한다. (실제 삭제/알림은 PostProcessingOrchestrator 가 수행)
     *
     * <h4>한눈에 보는 결정 트리 (INTRA_USER 기준)</h4>
     * <pre>
     * 소유자 바뀜(CROSS_USER)?           ── 예 ─→ NONE  ※(A) 아래 설명
     * └ 아니오(같은 소유자, 위치만 이동)
     *    목적지가 공유 영역인가?          ── 아니오 ─→ NONE   (B) 공유 유지 — 그냥 다른 폴더로 정리한 것
     *    └ 예(목적지 = 공유 하이어라키)
     *       내가 공유돼 있나?             ── 아니오 ─→ NONE   (C) 지울 공유 없음
     *       └ 예
     *          내가 직접 공유?
     *          ├ 아니오(상위 폴더 상속) + 파일 ─→ DELETE_RELATED_DATA  (D) 메타만 (공유/target 은 상위에 살아있음, 알림X)
     *          └ 예(직접 공유)                 ─→ DELETE_SPECIFIC_SHARE (E) 모두 (share+target+메타+알림)
     * </pre>
     * 핵심: <b>공유된 리소스를 *또 다른 공유 영역 안으로* 옮길 때만</b> 정리한다(중첩 공유 불가 불변식). 비공유 영역으로 옮기면
     * 공유는 리소스를 따라 그대로 유지된다(RESOURCE_SEQ 불변 → 공유 행이 따라감).
     *
     * <h4>주의 — 이 액션은 INTRA_USER 에서만 쓰인다 (A)</h4>
     * CROSS_USER(소유자 변경)는 orchestrator 가 이 액션을 <b>무시하고 무조건</b> {@code deleteSpecificShare}(share+target+메타)+알림 한다.
     * 그래서 {@code !sameUser} 면 여기선 NONE 을 돌려줄 뿐, 실제 정리는 CROSS_USER 분기가 한다. (NONE = "정리 안 함"이 아니라 "이 필드 미사용")
     *
     * <h4>nearest 조회 방향 (헷갈리기 쉬움)</h4>
     * <ul>
     * <li>{@code findNearestSelfOrShareByResource}(목적지) = <b>자기+상위+하위 전방위</b> — 목적지가 어떤 공유 영역에든 닿나.</li>
     * <li>{@code findNearestSelfOrAncestorShareByResource}(source) = <b>자기+상위만</b> — 옮기는 리소스가 공유됐나(직접/상속).</li>
     * </ul>
     *
     * @return 이동 후 적용할 {@link ShareCleanupAction}
     */
    @Override
    public ShareMoveImpactResult evaluateMoveShareImpact(ShareMoveImpactDto dto) {

        // (A) 소유자 변경(CROSS_USER) → 이 액션 미사용(orchestrator 가 무조건 full 해제) → NONE
        boolean sameUser = dto.sourceUserSeq().equals(dto.destinationUserSeq());
        if(!sameUser){
            return new ShareMoveImpactResult(ShareCleanupAction.NONE);
        }

        var isDelete = false;

        // (B) 목적지가 공유 영역이 아님(전방위로 공유 없음) → 중첩 안 생김 → 공유/메타 그대로 유지
        var targetNearestSharedData = dto.destinationResourceSeq() == null ? null : shareFindService.findNearestSelfOrShareByResource(dto.destinationResourceSeq(), dto.destinationUserSeq(), isDelete);
        if(targetNearestSharedData == null){
            return new ShareMoveImpactResult(ShareCleanupAction.NONE);
        }

        // (C) 옮기는 리소스가 공유 안 됨(자기/상위 모두 공유 없음) → 지울 공유 없음
        var sourceResourceSeq = dto.sourceResourceSeq();
        var sharedResourceNearestFindQuery = shareConverter.toShareNearestAncestorFindQuery(sourceResourceSeq, null,  dto.sourceUserSeq(), isDelete);
        var sourceNearestAncestorSharedData = shareFindService.findNearestSelfOrAncestorShareByResource(sharedResourceNearestFindQuery);
        if(sourceNearestAncestorSharedData.getShareSeq() == null){
            return new ShareMoveImpactResult(ShareCleanupAction.NONE);
        }

        // 직접 공유 = 공유 행이 '나 자신'에 걸림 / false = 상위 폴더 공유를 '상속'만 하던 것
        boolean sourceDirectlyShared = sourceResourceSeq.equals(sourceNearestAncestorSharedData.getResourceSeq());

        // (D) 상속받던 파일이 공유 폴더에서 빠지는 것 → 공유/target 은 상위에 그대로, 받은이 '메타'만 정리(알림 없음 — 해제가 아님)
        if(dto.resourceType().isFile() && !sourceDirectlyShared) {
            return new ShareMoveImpactResult(ShareCleanupAction.DELETE_RELATED_DATA);
        }

        // (E) 직접 공유된 리소스 → 그 공유 통째 해제(share+target+메타+알림). 유형 무관(링크/제한됨 동일).
        //     ※ 새 모델은 링크 공유에도 target 이 붙어, 구 DELETE_LINK_SHARE(target/알림 누락)는 orphan/누락을 내므로 specific 으로 일원화·제거됨.
        return new ShareMoveImpactResult(ShareCleanupAction.DELETE_SPECIFIC_SHARE);

    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        shareTargetService.deleteByUsers(userSeqList);
        deleteByUserSeqList(userSeqList);
    }

    @Override
    public void deleteByTenant(Integer tenantSeq) {
        shareTargetService.deleteByTenant(tenantSeq);
        var result = shareRepository.deleteByTenant(tenantSeq);
        log.info("[공유 테넌트 삭제 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    @Override
    public void deleteByTenantAndScopes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return;
        }
        
        shareTargetService.deleteByTenantAndShareTypes(tenantSeq, shareTypes);
        var result = shareRepository.deleteByTenantAndShareTypes(tenantSeq, shareTypes);
        log.info("[공유 테넌트 scope 삭제 : tenantSeq : {}, shareTypes : {}, {}건]", tenantSeq, shareTypes, result);
    }
    
    private void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = shareRepository.deleteByUserSeqList(userSeqList);
        log.info("[공유 문서 삭제  : {}건]", result);
    }
    
    /**
     * 링크(org/public) 공유가 해제/소멸/축소될 때 정리할 "리소스 범위"를 계산한다.
     * <p>
     * 링크 공유는 접근자(유저)를 열거할 수 없으므로(잠재 접근자 = 조직 전체/전원),
     * 유저 목록 대신 "이 링크가 끊기면 접근 경로가 사라지는 리소스 범위"를 만들고,
     * 실제 삭제 유저는 cleanup SQL 의 제외 조건(소유주 제외 / 잔존 target 제외 / 테넌트 조건)으로 결정한다.
     * <ol>
     * <li>상위에 다른 공유가 있으면 → 상위 공유가 접근을 유지하므로 정리 없음(empty).
     *     (update 의 유형 전환 경로에선 validator 가 nearest 없음을 보장해 항상 null — 해제(delete) 경로와 공용이라 존재)</li>
     * <li>파일이면 → 그 리소스 1건. (PartitionKey 의 userSeq 칸은 소유주 운반용 — 삭제 조건으로 안 씀)</li>
     * <li>폴더면 → findUnsharedDescendantsByShare: 루트+하위 전체에서 다른 공유 서브트리를 제외한
     *     "이 공유 하나로만 접근 가능했던 리소스" 목록. deleteDescendants=true.</li>
     * </ol>
     * @param shareSeq 공유 seq
     * @param userSeq 유저 seq
     * @param resourceType 공유 유형
     * @param resourceSeq 리소스 seq
     * @return deleteContext
     */
    private ShareDeletionContext calculateLinkDeleteContext(long shareSeq, int userSeq, ResourceType resourceType, long resourceSeq){
        
        var nearestAncestorShare = shareFindService.findNearestAncestorShareByShare(shareSeq, userSeq, false);
        
        if (nearestAncestorShare != null) {
            return ShareDeletionContext.empty();
        }
        
        if(resourceType.isFile()){
            return ShareDeletionContext.linkFile(userSeq, resourceSeq);
        }
        
        var unsharedResources = shareFindService.findUnsharedDescendantsByShare(shareSeq, userSeq, false);
        var cleanUpPartitionKeys = unsharedResources.getUnsharedResources()
                                                    .stream()
                                                    .map(u -> new PartitionKey(u.getUserSeq(), u.getResourceSeq()))
                                                    .toList();
        
        return ShareDeletionContext.linkFolder(cleanUpPartitionKeys);
    }
    
}

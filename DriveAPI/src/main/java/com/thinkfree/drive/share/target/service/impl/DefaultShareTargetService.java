package com.thinkfree.drive.share.target.service.impl;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.ShareTargetPartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.context.ShareTargetChangeContext;
import com.thinkfree.drive.share.target.converter.ShareTargetConverter;
import com.thinkfree.drive.share.target.data.ShareTargetNearestData;
import com.thinkfree.drive.share.target.data.ShareTargetPartitionKeyData;
import com.thinkfree.drive.share.target.dto.ShareTargetsReplaceDto;
import com.thinkfree.drive.share.target.dto.ShareTargetsSaveDto;
import com.thinkfree.drive.share.target.entity.ShareTarget;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.repository.ShareTargetRepository;
import com.thinkfree.drive.share.target.result.ShareTargetDeletionResult;
import com.thinkfree.drive.share.target.result.ShareTargetReplaceResult;
import com.thinkfree.drive.share.target.result.ShareTargetSaveResult;
import com.thinkfree.drive.share.target.service.ShareTargetFindService;
import com.thinkfree.drive.share.target.service.ShareTargetService;
import com.thinkfree.drive.share.validation.SharePermissionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DefaultShareTargetService implements ShareTargetService {
    
    private final ShareTargetConverter shareTargetConverter;
    private final ShareTargetRepository shareTargetRepository;
    private final SharePermissionValidator sharePermissionValidator;
    private final ShareTargetFindService shareTargetFindService;
    
    @Override
    public ShareTargetSaveResult insertShareTargets(ShareTargetsSaveDto dto) {
        
        var now = Instant.now();
        
        Set<Integer> addedUserSeqSet = new HashSet<>();
        List<ShareTarget> entities = dto.getTargets()
                                        .stream()
                                        .map(d -> {
                                                validatePermission(d, dto.getResourceType());
                                                addedUserSeqSet.add(d.getUserSeq());
                                                return shareTargetConverter.toSaveShareTarget(d, now);
                                        })
                                        .toList();
        
        shareTargetRepository.insertAll(entities);
        
        return new ShareTargetSaveResult(addedUserSeqSet);
    }
    
    @Override
    public ShareTargetReplaceResult replaceShareTargets(ShareTargetsReplaceDto dto) {
        
        var now = Instant.now();
        
        List<ShareTarget> entities = new ArrayList<>();
        Set<ShareTargetPartitionKey> newKeys = new HashSet<>();
        var shareSeq = dto.getShareSeq();
        var userSeq = dto.getSharedByUserSeq();
        
        for(var saveDto : dto.getTargets()){
            
            validatePermission(saveDto, dto.getResourceType());
            
            var entity = shareTargetConverter.toSaveShareTarget(saveDto, now);
            entities.add(entity);
            
            ShareTargetPartitionKey key = shareTargetConverter.toShareTargetPartitionKey(saveDto);
            newKeys.add(key);
        }
        
        ShareTargetChangeContext partitionKeyChangeContext = calculateShareTargetChangeContext(shareSeq, userSeq, newKeys);
        
        var deletedTargetUserKeys = partitionKeyChangeContext.deletedTargetUserKeys();
        
        ShareTargetDeletionResult shareTargetDeletionResult;
        
        if(!deletedTargetUserKeys.isEmpty()) {
            var list = shareTargetFindService.findNearestAncestorShareTargetsByUsers(shareSeq, userSeq, deletedTargetUserKeys, false);
            shareTargetDeletionResult = calculateTargetsDeleteResult(list, shareSeq, userSeq, dto.getShareType(), dto.getResourceType(), dto.getResourceSeq());
            
            if (!shareTargetDeletionResult.shareTargetDeleteKeys().isEmpty()) {
                deleteByShareTargetDeleteKeys(shareTargetDeletionResult.shareTargetDeleteKeys(), userSeq);
            }
        }else{
            shareTargetDeletionResult = ShareTargetDeletionResult.empty();
        }
        
        shareTargetRepository.upsertAll(entities);
        
        var occurredDatetime = now;
        
        return new ShareTargetReplaceResult(userSeq, partitionKeyChangeContext.addedUserSeqSet(), shareTargetDeletionResult, occurredDatetime);
    }
    
    private void validatePermission(PermissionHolder permissionHolder, ResourceType resourceType) {
        var permissions = shareTargetConverter.toPermissions(permissionHolder);
        sharePermissionValidator.validateTarget(resourceType, permissions);
    }
    
    private ShareTargetChangeContext calculateShareTargetChangeContext(Long shareSeq, Integer sharedByUserSeq, Set<ShareTargetPartitionKey> newKeys) {
        
        List<ShareTargetPartitionKeyData> shareTargetPartitionKeys = shareTargetFindService.findShareTargetPartitionKeys(shareSeq, sharedByUserSeq);
        Set<ShareTargetPartitionKey> oldKeys = shareTargetConverter.toShareTargetPartitionKeys(shareTargetPartitionKeys);
        Set<ShareTargetPartitionKey> diffDeletePartitionKeys = new HashSet<>(oldKeys);
        diffDeletePartitionKeys.removeAll(newKeys);
        
        Set<ShareTargetPartitionKey> diffSavePartitionKeys = new HashSet<>(newKeys);
        diffSavePartitionKeys.removeAll(oldKeys);
        
        Set<Integer> addedUserSeqSet = diffSavePartitionKeys.stream()
                                                            .map(ShareTargetPartitionKey::userSeq)
                                                            .collect(Collectors.toSet());
        
        List<Integer> deletedTargetUserKeys = diffDeletePartitionKeys.stream()
                                                                     .map(ShareTargetPartitionKey::userSeq)
                                                                     .toList();
        
        return new ShareTargetChangeContext(addedUserSeqSet, deletedTargetUserKeys);
    }

    @Override
    public void deleteByResource(Long resourceSeq, int sharedByUserSeq){
        var deleteResourceQuery = shareTargetConverter.toShareTargetDeleteResourceQuery(resourceSeq, sharedByUserSeq);
        int deleteCount = shareTargetRepository.deleteByResource(deleteResourceQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteByResources(Collection<Long> resourceSeqList, int sharedByUserSeq) {
        var deleteResourcesQuery = shareTargetConverter.toShareTargetDeleteResourcesQuery(resourceSeqList, sharedByUserSeq);
        int deleteCount = shareTargetRepository.deleteByResources(deleteResourcesQuery);
        deleteLog(deleteCount);
    }
    
    @Override
    public int deleteByShareTargetDeleteKeys(Collection<ShareTargetDeleteKey> deleteKeys, int sharedByUserSeq) {
        var query = shareTargetConverter.toShareTargetDeleteKeysQuery(deleteKeys, sharedByUserSeq);
        return shareTargetRepository.deleteByShareTargetDeleteKeys(query);
    }
    
    private void deleteLog(int deleteCount){
        log.info("[공유 대상 전체 삭제 건수 : {}건]", deleteCount);
    }
    
    /**
     * 1. 해당 shareSeq로 share_target 추출.
     * 2. 해당 userList, share 의 resource 기준 상향 탐색.
     * 3. 파일 상향 탐색에 포함 되지 않는 유저 목록 추출.
     *   - share_target 삭제(shareSeq, sharedByUserSeq, userSeq List)
     *   - recent, starred 삭제 (sharedByUserSeq 하나, userSeq List, resourceSeq 하나)
     * 4. 폴더 라면 상향 탐색에 포함되지 않은 유저 목록으로 하위 hierarchy 추출
     *   - share_target 삭제(shareSeq, sharedByUserSeq, userSeq List)
     *   - recent, starred 배치 삭제 (sharedByUserSeq 하나, userSeq 하나 , resourceSeqList )
     * @param shareSeq 공유 seq
     * @param userSeq 유저 seq
     * @param resourceType 리소스 유형
     * @param resourceSeq 리소스 seq
     * @return deleteContext
     */
    @Override
    public ShareTargetDeletionResult calculateTargetsDeleteResult(long shareSeq, int userSeq, ShareType shareType, ResourceType resourceType, long resourceSeq){
        var targets = shareTargetFindService.findNearestAncestorShareTargets(shareSeq, userSeq, false);
        return calculateTargetsDeleteResult(targets, shareSeq, userSeq, shareType, resourceType, resourceSeq);
    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        deleteByUserSeqList(userSeqList);
        deleteBySharedByUserSeqList(userSeqList);
    }

    @Override
    public void deleteByTenant(Integer tenantSeq) {
        var result = shareTargetRepository.deleteByTenant(tenantSeq);
        log.info("[공유 대상 테넌트 삭제 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    @Override
    public void deleteByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return;
        }
        var result = shareTargetRepository.deleteByTenantAndShareTypes(tenantSeq, shareTypes);
        log.info("[공유 대상 테넌트 scope 삭제 : tenantSeq : {}, shareTypes : {}, {}건]", tenantSeq, shareTypes, result);
    }

    @Override
    public void deleteOtherTenantByTenant(Integer tenantSeq) {
        var result = shareTargetRepository.deleteOtherTenantByTenant(tenantSeq);
        log.info("[공유 대상 외부테넌트 삭제 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    private void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = shareTargetRepository.deleteByUserSeqList(userSeqList);
        log.info("[공유 대상 삭제 - 대상 유저 기준 : {}건]", result);
    }
    
    public void deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        var result = shareTargetRepository.deleteBySharedByUserSeqList(sharedByUserSeqList);
        log.info("[공유 대상 삭제 - 공유한 유저 기준 : {}건]", result);
    }
    
    private ShareTargetDeletionResult calculateTargetsDeleteResult(List<ShareTargetNearestData> targets, long shareSeq, int userSeq, ShareType shareType, ResourceType resourceType, long resourceSeq){

        List<ShareTargetDeleteKey> shareTargetDeleteKeys = new ArrayList<>();
        List<PartitionKey> cleanUpPartitionKeys = new ArrayList<>();

        for(var target : targets){

            var shareTargetUserSeq = target.getUserSeq();

            // target 행 삭제 + 해제 알림 대상은 빠진 유저 전원 (아래 메타 보존 게이트와 무관)
            shareTargetDeleteKeys.add(new ShareTargetDeleteKey(shareTargetUserSeq, target.getShareTargetSeq()));

            // 대상에서 빠져도 링크로 접근이 유지되는 유저는 메타(최근/중요/핀) 정리 제외 (target 행 삭제만)
            if (keepsLinkAccess(shareType, target)) {
                continue;
            }

            // 상위 폴더 공유에도 본인 target 이 있으면 상위로 접근 유지 → 메타 정리 제외
            if (target.getAncestorShareTargetSeq() == null) {
                cleanUpPartitionKeys.add(new PartitionKey(shareTargetUserSeq, resourceSeq));
            }
        }
        
        if(resourceType.isFile()){
            return ShareTargetDeletionResult.targetFile(cleanUpPartitionKeys, shareTargetDeleteKeys);
        }
        
        if(cleanUpPartitionKeys.isEmpty()){
            return ShareTargetDeletionResult.targetFolder(shareTargetDeleteKeys, List.of());
        }
        
        List<Integer> descendantUserSeqList = cleanUpPartitionKeys.stream()
                                                                  .map(PartitionKey::sharedByUserSeq)
                                                                  .toList();
        
        var unsharedTargets = shareTargetFindService.findUnsharedDescendantsByTargets(descendantUserSeqList, userSeq, shareSeq, false);
        
        List<UserResourceGroup> userResourceGroups = unsharedTargets.stream()
                                                                    .map(u -> new UserResourceGroup(u.getUserSeq(), u.getResourceSeqList()))
                                                                    .toList();
        
        return ShareTargetDeletionResult.targetFolder(shareTargetDeleteKeys, userResourceGroups);

    }

    /**
     * 공유 유형 기준 링크 접근 유지 여부 — PUBLIC_LINK 는 전원, ORG_LINK 는 소유자와 같은 테넌트만.
     * SPECIFIC_USERS 는 링크가 없으므로 항상 false.
     */
    private boolean keepsLinkAccess(ShareType shareType, ShareTargetNearestData target){
        if(shareType == ShareType.PUBLIC_LINK){
            return true;
        }
        return shareType == ShareType.ORG_LINK && Objects.equals(target.getTenantSeq(), target.getOwnerTenantSeq());
    }

}

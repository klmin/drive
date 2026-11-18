package com.thinkfree.drive.recent.service.impl;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.recent.converter.RecentConverter;
import com.thinkfree.drive.recent.dto.RecentSaveDto;
import com.thinkfree.drive.recent.repository.RecentRepository;
import com.thinkfree.drive.recent.service.RecentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultRecentService implements RecentService {
    
    private final RecentConverter recentConverter;
    private final RecentRepository recentRepository;
    
    @Override
    public void save(RecentSaveDto dto) {
        var entity = recentConverter.toSaveRecent(dto);
        recentRepository.save(entity);
    }

    @Override
    public void deleteByResource(Long resourceSeq, int sharedByUserSeq){
        var deleteResourceQuery = recentConverter.toRecentDeleteResourceQuery(resourceSeq, null, sharedByUserSeq, null);
        int deleteCount = recentRepository.deleteByResource(deleteResourceQuery);
        deleteLog(deleteCount);

    }

    @Override
    public void deleteByResources(Collection<Long> resourceSeqList, int sharedByUserSeq){
        var deleteResourcesQuery = recentConverter.toRecentDeleteResourcesQuery(resourceSeqList, null, sharedByUserSeq, null);
        int deleteCount = recentRepository.deleteByResources(deleteResourcesQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteByResourceExcludingOwner(Long resourceSeq, int userSeq, int sharedByUserSeq) {
        deleteByResourceExcludingOwner(resourceSeq, userSeq, sharedByUserSeq, null);
    }

    @Override
    public void deleteByResourcesExcludingOwner(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq) {
        deleteByResourcesExcludingOwner(resourceSeqList, userSeq, sharedByUserSeq, null);
    }

    @Override
    public void deleteByResourceExcludingOwner(Long resourceSeq, int userSeq, int sharedByUserSeq, Collection<Integer> excludeUserSeqList) {
        var deleteResourceQuery = recentConverter.toRecentDeleteResourceQuery(resourceSeq, userSeq, sharedByUserSeq, toExcludeList(excludeUserSeqList));
        int deleteCount = recentRepository.deleteByResourceExcludingOwner(deleteResourceQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteByResourcesExcludingOwner(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq, Collection<Integer> excludeUserSeqList) {
        var deleteResourcesQuery = recentConverter.toRecentDeleteResourcesQuery(resourceSeqList, userSeq, sharedByUserSeq, toExcludeList(excludeUserSeqList));
        int deleteCount = recentRepository.deleteByResourcesExcludingOwner(deleteResourcesQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteOtherTenantByResources(Collection<Long> resourceSeqList, int sharedByUserSeq, int tenantSeq, Collection<Integer> excludeUserSeqList) {
        var query = recentConverter.toRecentDeleteOtherTenantResourcesQuery(resourceSeqList, sharedByUserSeq, tenantSeq, toExcludeList(excludeUserSeqList));
        int deleteCount = recentRepository.deleteOtherTenantByResources(query);
        deleteLog(deleteCount);
    }

    private List<Integer> toExcludeList(Collection<Integer> excludeUserSeqList) {
        return excludeUserSeqList == null || excludeUserSeqList.isEmpty() ? null : List.copyOf(excludeUserSeqList);
    }
    
    @Override
    public void deleteByShareTargets(int sharedByUserSeq, Collection<PartitionKey> keys) {
        var deleteShareTargetsQuery = recentConverter.toRecentDeleteShareTargetsQuery(sharedByUserSeq, keys);
        int deleteCount = recentRepository.deleteByShareTargets(deleteShareTargetsQuery);
        deleteLog(deleteCount);
    }
    
    @Override
    public void deleteByUserResources(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq) {
        var queries = recentConverter.toRecentDeleteUserResourcesQueryList(userResourceGroups, sharedByUserSeq);
        recentRepository.deleteByUserResources(queries);
    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        deleteByUserSeqList(userSeqList);
        deleteBySharedByUserSeqList(userSeqList);
    }
    
    private void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = recentRepository.deleteByUserSeqList(userSeqList);
        log.info("[공유 최근 문서함 삭제 - 대상 유저 기준 : {}건]", result);
    }
    
    private void deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        var result = recentRepository.deleteBySharedByUserSeqList(sharedByUserSeqList);
        log.info("[공유 최근 문서함 삭제 - 공유한 유저 기준 : {}건]", result);
    }
    
    @Override
    public void deleteAllSharedByTenant(Integer tenantSeq) {
        var result = recentRepository.deleteAllSharedByTenant(tenantSeq);
        log.info("[최근 문서함 공유 데이터 테넌트 삭제 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    @Override
    public void deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return;
        }
        var result = recentRepository.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes);
        log.info("[최근 문서함 scope 공유 데이터 정리 : tenantSeq : {}, shareTypes : {}, {}건]", tenantSeq, shareTypes, result);
    }

    @Override
    public void deleteOtherTenantSharedByTenant(Integer tenantSeq) {
        var result = recentRepository.deleteOtherTenantSharedByTenant(tenantSeq);
        log.info("[최근 문서함 외부테넌트 공유 데이터 정리 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    private void deleteLog(int deleteCount){
        log.info("[최근 문서함 전체 삭제 건수 : {}건]", deleteCount);
    }

}

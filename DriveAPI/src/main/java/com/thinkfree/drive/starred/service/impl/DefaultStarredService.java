package com.thinkfree.drive.starred.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.starred.converter.StarredConverter;
import com.thinkfree.drive.starred.dto.StarredSaveDto;
import com.thinkfree.drive.starred.exception.StarredErrorCode;
import com.thinkfree.drive.starred.repository.StarredRepository;
import com.thinkfree.drive.starred.service.StarredService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DefaultStarredService implements StarredService {

    private final StarredConverter starredConverter;
    private final StarredRepository starredRepository;

    @Override
    public long save(StarredSaveDto dto) {
        var entity = starredConverter.toSaveStarred(dto);
        try {
            starredRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // uniq_starred(USER_SEQ, RESOURCE_SEQ, SHARED_BY_USER_SEQ) 위반 = 이미 등록된 리소스. JPA(save — IDENTITY 라 저장 순간 INSERT) 경로는 DuplicateKeyException 이 아니라 상위 DataIntegrityViolationException 으로 번역될 수 있어 상위 타입으로 잡는다
            throw ApiRuntimeException.type(StarredErrorCode.CONFLICT);
        }
        return entity.getStarredSeq();
    }
    
    @Override
    public void delete(long starredSeq, int userSeq){
        var deleteQuery = starredConverter.toDeleteQuery(starredSeq,userSeq);
        var result = starredRepository.delete(deleteQuery);
        DriveAssert.throwIfZero(result, StarredErrorCode.DELETE_FAIL);
    }
    
    @Override
    public void deleteByResource(Long resourceSeq, int sharedByUserSeq){
        var deleteResourceQuery = starredConverter.toStarredDeleteByResourceQuery(resourceSeq, null, sharedByUserSeq, null);
        int deleteCount = starredRepository.deleteByResource(deleteResourceQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteByResources(Collection<Long> resourceSeqList, int sharedByUserSeq) {
        var deleteResourcesQuery = starredConverter.toStarredDeleteByResourcesQuery(resourceSeqList, null, sharedByUserSeq, null);
        int deleteCount = starredRepository.deleteByResources(deleteResourcesQuery);
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
        var deleteResourceQuery = starredConverter.toStarredDeleteByResourceQuery(resourceSeq, userSeq, sharedByUserSeq, toExcludeList(excludeUserSeqList));
        int deleteCount = starredRepository.deleteByResourceExcludingOwner(deleteResourceQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteByResourcesExcludingOwner(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq, Collection<Integer> excludeUserSeqList) {
        var deleteResourcesQuery = starredConverter.toStarredDeleteByResourcesQuery(resourceSeqList, userSeq, sharedByUserSeq, toExcludeList(excludeUserSeqList));
        int deleteCount = starredRepository.deleteByResourcesExcludingOwner(deleteResourcesQuery);
        deleteLog(deleteCount);
    }

    @Override
    public void deleteOtherTenantByResources(Collection<Long> resourceSeqList, int sharedByUserSeq, int tenantSeq, Collection<Integer> excludeUserSeqList) {
        var query = starredConverter.toStarredDeleteOtherTenantResourcesQuery(resourceSeqList, sharedByUserSeq, tenantSeq, toExcludeList(excludeUserSeqList));
        int deleteCount = starredRepository.deleteOtherTenantByResources(query);
        deleteLog(deleteCount);
    }

    private List<Integer> toExcludeList(Collection<Integer> excludeUserSeqList) {
        return excludeUserSeqList == null || excludeUserSeqList.isEmpty() ? null : List.copyOf(excludeUserSeqList);
    }
    
    @Override
    public void deleteByShareTargets(int sharedByUserSeq, Collection<PartitionKey> keys) {
        var deleteShareTargetsQuery = starredConverter.toStarredDeleteShareTargetsQuery(sharedByUserSeq, keys);
        int deleteCount = starredRepository.deleteByShareTargets(deleteShareTargetsQuery);
        deleteLog(deleteCount);
    }
    
    @Override
    public void deleteByUserResources(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq) {
        var queries = starredConverter.toStarredDeleteUserResourcesQueryList(userResourceGroups, sharedByUserSeq);
        starredRepository.deleteByUserResources(queries);
    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        deleteByUserSeqList(userSeqList);
        deleteBySharedByUserSeqList(userSeqList);
    }
    
    private void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = starredRepository.deleteByUserSeqList(userSeqList);
        log.info("[중요 문서함 삭제 - 대상 유저 기준 : {}건]", result);
    }
    
    private void deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        var result = starredRepository.deleteBySharedByUserSeqList(sharedByUserSeqList);
        log.info("[중요 문서함 삭제 - 공유한 유저 기준 : {}건]", result);
    }
    
    @Override
    public void deleteAllSharedByTenant(Integer tenantSeq) {
        var result = starredRepository.deleteAllSharedByTenant(tenantSeq);
        log.info("[중요 문서함 공유 데이터 테넌트 삭제 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    @Override
    public void deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return;
        }
        var result = starredRepository.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes);
        log.info("[중요 문서함 scope 공유 데이터 정리 : tenantSeq : {}, shareTypes : {}, {}건]", tenantSeq, shareTypes, result);
    }

    @Override
    public void deleteOtherTenantSharedByTenant(Integer tenantSeq) {
        var result = starredRepository.deleteOtherTenantSharedByTenant(tenantSeq);
        log.info("[중요 문서함 외부테넌트 공유 데이터 정리 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    private void deleteLog(int deleteCount){
        log.info("[중요 문서함 전체 삭제 건수 : {}건]", deleteCount);
    }
}

package com.thinkfree.drive.pinnedfolder.service.impl;

import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.pinnedfolder.converter.PinnedFolderConverter;
import com.thinkfree.drive.pinnedfolder.dto.PinnedFolderSaveDto;
import com.thinkfree.drive.pinnedfolder.exception.PinnedFolderErrorCode;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourceQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourcesQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteUserResourcesQuery;
import com.thinkfree.drive.pinnedfolder.repository.PinnedFolderRepository;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderFindService;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderService;
import com.thinkfree.drive.valid.DriveAssert;
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
public class DefaultPinnedFolderService implements PinnedFolderService {
    
    private static final int MAX_PINNED_FOLDER_COUNT = 3;
    
    private final PinnedFolderConverter pinnedFolderConverter;
    private final PinnedFolderRepository pinnedFolderRepository;
    private final PinnedFolderFindService pinnedFolderFindService;
    
    @Override
    public void pinFolder(PinnedFolderSaveDto dto) {
        
        var userSeq = dto.getUserSeq();
        
        var pinnedFolderExistsQuery = pinnedFolderConverter.toPinnedFolderExistsQuery(userSeq, dto.getResourceSeq(), dto.getSharedByUserSeq());
        pinnedFolderFindService.existsPinnedFolder(pinnedFolderExistsQuery);
        
        lockAndCount(userSeq);
        
        var savePinnedFolder = pinnedFolderConverter.toSavePinnedFolder(dto);
        pinnedFolderRepository.insertPinnedFolder(savePinnedFolder);
        
    }
    
    private void lockAndCount(int userSeq){
        var pinnedFolderCountQuery = pinnedFolderConverter.toPinnedFolderCountQuery(userSeq);
        var count = pinnedFolderRepository.countByUserForUpdate(pinnedFolderCountQuery);
        DriveAssert.throwIfGreaterOrEqual(count, MAX_PINNED_FOLDER_COUNT, PinnedFolderErrorCode.LIMIT_EXCEEDED);
    }
    
    @Override
    public void unpinFolder(PinnedFolderDeleteQuery query) {
        var result = pinnedFolderRepository.deletePinnedFolder(query);
        DriveAssert.throwIfZero(result, PinnedFolderErrorCode.DELETE_FAIL);
    }
    
    @Override
    public void unpinFolderByResource(PinnedFolderDeleteResourceQuery query) {
        var result = pinnedFolderRepository.deleteByResource(query);
        log.info("[unpinFolder delete] count : {}, sharedByUserSeq : {}, resourceSeq : {}", result, query.sharedByUserSeq(), query.resourceSeq());
    }
    
    @Override
    public void unpinFolderByResources(PinnedFolderDeleteResourcesQuery query) {
        var result = pinnedFolderRepository.deleteByResources(query);
        log.info("[unpinFolders delete] count : {}, sharedByUserSeq : {}, resourceSeqList : {}", result, query.sharedByUserSeq(), query.resourceSeqList());
    }
    
    @Override
    public void unpinFolderByResourcesExcludingOwner(PinnedFolderDeleteResourcesQuery query) {
        var result = pinnedFolderRepository.deleteByResourcesExcludingOwner(query);
        log.info("[unpinFolders excludingOwner delete] count : {}, userSeq : {}, sharedByUserSeq : {}, resourceSeqList : {}", result, query.userSeq(), query.sharedByUserSeq(), query.resourceSeqList());
    }
    
    @Override
    public void unpinFolderByUserResources(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq) {
        
        var queries = userResourceGroups.stream()
                                  .map(u -> new PinnedFolderDeleteUserResourcesQuery(u.userSeq(), sharedByUserSeq, u.resourceSeqList()))
                                  .toList();
        
        pinnedFolderRepository.deleteByUserResources(queries);
    }
    
    @Override
    public void unpinFoldersByUsers(List<Integer> userSeqList) {
        unpinFolderByUserSeqList(userSeqList);
        unpinFolderBySharedByUserSeqList(userSeqList);
    }
    
    private void unpinFolderByUserSeqList(List<Integer> userSeqList) {
        var result = pinnedFolderRepository.deleteByUserSeqList(userSeqList);
        log.info("[고정 폴더 삭제 - 대상 유저 기준 : {}건]", result);
    }
    
    @Override
    public void unpinAllSharedByTenant(Integer tenantSeq) {
        var result = pinnedFolderRepository.deleteAllSharedByTenant(tenantSeq);
        log.info("[고정 폴더 공유 데이터 테넌트 삭제 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    @Override
    public void unpinSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return;
        }
        var result = pinnedFolderRepository.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes);
        log.info("[고정 폴더 scope 공유 데이터 정리 : tenantSeq : {}, shareTypes : {}, {}건]", tenantSeq, shareTypes, result);
    }

    @Override
    public void unpinOtherTenantSharedByTenant(Integer tenantSeq) {
        var result = pinnedFolderRepository.deleteOtherTenantSharedByTenant(tenantSeq);
        log.info("[고정 폴더 외부테넌트 공유 데이터 정리 : tenantSeq : {}, {}건]", tenantSeq, result);
    }

    @Override
    public void unpinOtherTenantByResources(PinnedFolderDeleteResourcesQuery query) {
        var result = pinnedFolderRepository.deleteOtherTenantByResources(query);
        log.info("[고정 폴더 외부테넌트 정리 - 리소스 기준 : tenantSeq : {}, sharedByUserSeq : {}, {}건]", query.tenantSeq(), query.sharedByUserSeq(), result);
    }

    private void unpinFolderBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        var result = pinnedFolderRepository.deleteBySharedByUserSeqList(sharedByUserSeqList);
        log.info("[고정 폴더 삭제 - 공유한 유저 기준 : {}건]", result);
    }
}

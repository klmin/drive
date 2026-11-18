package com.thinkfree.drive.share.service.impl;

import com.thinkfree.drive.share.converter.ShareConverter;
import com.thinkfree.drive.share.data.*;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.query.SharedResourceNearestFindQuery;
import com.thinkfree.drive.share.repository.ShareQueryRepository;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.target.service.ShareTargetFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultShareFindService implements ShareFindService {
    
    private final ShareConverter shareConverter;
    private final ShareQueryRepository shareQueryRepository;
    private final ShareTargetFindService shareTargetFindService;
    
    @Override
    public SharedResourceData findSharedResourceByShare(Long shareSeq, int userSeq, boolean isDelete) {
        var sharedResourceQuery = shareConverter.toSharedResourceFindQuery(shareSeq, null, userSeq, isDelete);
        var sharedResourceData = shareQueryRepository.findSharedResourceByShare(sharedResourceQuery);
        DriveAssert.throwIfNull(sharedResourceData, ShareErrorCode.NOT_FOUND);
        return sharedResourceData;
    }
    
    @Override
    public ShareWithTargetsData findShareWithTargets(long shareSeq, int userSeq, boolean isDelete) {
        var shareWithTargetsFindQuery = shareConverter.toShareWithTargetsFindQuery(shareSeq, userSeq, isDelete);
        var shareWithTargetsData = shareQueryRepository.findShareWithTargets(shareWithTargetsFindQuery);
        DriveAssert.throwIfNull(shareWithTargetsData, ShareErrorCode.NOT_FOUND);
        return shareWithTargetsData;
    }
    
    @Override
    public List<ShareUserData> findUsersByShare(long shareSeq, int userSeq, boolean isDelete) {
        var shareUsersFindQuery = shareConverter.toShareUsersFindQuery(shareSeq, userSeq, isDelete);
        return shareQueryRepository.findUsersByShare(shareUsersFindQuery);
    }
    
    @Override
    public SharedResourceNearestData findNearestShareByResource(long resourceSeq, int userSeq, boolean isDelete) {
        var sharedResourceNearestAncestorFindQuery = shareConverter.toShareNearestAncestorFindQuery(resourceSeq, null, userSeq, isDelete);
        var nearestAncestor = shareQueryRepository.findNearestAncestorShareByResource(sharedResourceNearestAncestorFindQuery);
        
        DriveAssert.throwIfNull(nearestAncestor, ShareErrorCode.NOT_FOUND);
        
        if(nearestAncestor.getResourceType().isFile()){
            return null;
        }
        if(nearestAncestor.getShareSeq() != null){
            return nearestAncestor;
        }
        
        var sharedResourceNearestDescendantFindQuery = shareConverter.toShareNearestDescendantFindQuery(resourceSeq, null, nearestAncestor.getRootResourceSeq(), userSeq, isDelete);
        return shareQueryRepository.findNearestDescendantShareByResource(sharedResourceNearestDescendantFindQuery);
    }
    
    @Override
    public SharedResourceNearestData findNearestSelfOrAncestorShareByResource(SharedResourceNearestFindQuery query){
        var nearestAncestorSharedData = shareQueryRepository.findNearestSelfOrAncestorShareByResource(query);
        DriveAssert.throwIfNull(nearestAncestorSharedData, ShareErrorCode.NOT_FOUND);
        return nearestAncestorSharedData;
    }
    
    @Override
    public SharedResourceNearestData findNearestSelfOrShareByResource(long resourceSeq, int userSeq, boolean isDelete) {
        
        var sharedResourceNearestFindQuery = shareConverter.toShareNearestAncestorFindQuery(resourceSeq, null, userSeq, isDelete);
        var nearestAncestor = findNearestSelfOrAncestorShareByResource(sharedResourceNearestFindQuery);
        
        if(nearestAncestor.getShareSeq() != null){
            return nearestAncestor;
        }
        
        var sharedResourceNearestDescendantFindQuery = shareConverter.toShareNearestDescendantFindQuery(resourceSeq, null, nearestAncestor.getRootResourceSeq(), userSeq, isDelete);
        return shareQueryRepository.findNearestDescendantShareByResource(sharedResourceNearestDescendantFindQuery);
    }
    
    @Override
    public SharedResourceData findSharedResourceByResource(long resourceSeq, int userSeq, boolean isDelete) {
        var query = shareConverter.toSharedResourceFindQuery(null, resourceSeq, userSeq, isDelete);
        return shareQueryRepository.findSharedResourceByResource(query);
    }

    @Override
    public SharedResourceNearestData findNearestAncestorShareByShare(long shareSeq, int userSeq, boolean isDelete){
        var sharedResourceNearestAncestorFindQuery = shareConverter.toShareNearestAncestorFindQuery(null, shareSeq, userSeq, isDelete);
        return shareQueryRepository.findNearestAncestorShareByShare(sharedResourceNearestAncestorFindQuery);
    }
    @Override
    public SharedResourceNearestData findNearestAncestorShareAndRootByShare(long shareSeq, int userSeq, boolean isDelete){
        var sharedResourceNearestAncestorFindQuery = shareConverter.toShareNearestAncestorFindQuery(null, shareSeq, userSeq, isDelete);
        return shareQueryRepository.findNearestAncestorShareAndRootByShare(sharedResourceNearestAncestorFindQuery);
    }
    
    @Override
    public SharedResourceNearestData findNearestShareByShare(long shareSeq, int userSeq, boolean isDelete) {
        var nearestAncestor = findNearestAncestorShareAndRootByShare(shareSeq, userSeq, isDelete);
        
        DriveAssert.throwIfNull(nearestAncestor, ShareErrorCode.NOT_FOUND);
        
        if(nearestAncestor.getResourceType().isFile()){
            return null;
        }
        if(nearestAncestor.getShareSeq() != null){
            return nearestAncestor;
        }
        
        var sharedResourceNearestDescendantFindQuery = shareConverter.toShareNearestDescendantFindQuery(null, shareSeq, nearestAncestor.getRootResourceSeq(), userSeq, isDelete);
        return shareQueryRepository.findNearestDescendantShareByShare(sharedResourceNearestDescendantFindQuery);
    }
    
    @Override
    public UnsharedResourceData findUnsharedDescendantsByShare(long shareSeq, int userSeq, boolean isDelete) {
        var query = shareConverter.toUnSharedResourcesFindQuery(shareSeq, userSeq, isDelete);
        return shareQueryRepository.findUnsharedDescendantsByShare(query);
    }
    
    @Override
    public boolean existsByTenant(Integer tenantSeq) {
        return shareQueryRepository.existsByTenant(tenantSeq);
    }

    @Override
    public List<ShareUserData> findSharedUsersByResource(long resourceSeq, int userSeq, boolean isDelete) {
        
        var sharedResourceFindQuery = shareConverter.toSharedResourceFindQuery(null, resourceSeq, userSeq, isDelete);
        var sharedResourceDataList = shareQueryRepository.findAncestorSharesByResource(sharedResourceFindQuery);
        
        if(sharedResourceDataList.isEmpty()){
            return Collections.emptyList();
        }
        
        List<Long> shareSeqList = new ArrayList<>();
        
        for(var share : sharedResourceDataList){
            if(share.getShareType().isPublic()){
                return Collections.emptyList();
            }
            shareSeqList.add(share.getShareSeq());
        }
        
        var shareTargetUsers = shareTargetFindService.findShareTargetUsersByShares(shareSeqList, userSeq, isDelete);
        
        return shareConverter.toShareUsersData(shareTargetUsers);
    }
    
    
}

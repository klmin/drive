package com.thinkfree.drive.share.target.service.impl;

import com.thinkfree.drive.permission.service.PermissionAggregationService;
import com.thinkfree.drive.share.target.converter.ShareTargetConverter;
import com.thinkfree.drive.share.target.data.*;
import com.thinkfree.drive.share.target.query.ShareTargetListQuery;
import com.thinkfree.drive.share.target.repository.ShareTargetQueryRepository;
import com.thinkfree.drive.share.target.service.ShareTargetFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultShareTargetFindService implements ShareTargetFindService {

    private final ShareTargetConverter shareTargetConverter;
    private final ShareTargetQueryRepository shareTargetQueryRepository;
    private final PermissionAggregationService permissionAggregationService;

    @Override
    public List<ShareTargetResourceData> findAll(ShareTargetListQuery query){
        var list = shareTargetQueryRepository.findAll(query);
        var userSeq = query.getUserSeq();
        return permissionAggregationService.mergeResourcesPermissionWithFarthest(userSeq, list, shareTargetConverter::toShareTargetResourceData);
    }

    @Override
    public List<ShareTargetPartitionKeyData> findShareTargetPartitionKeys(Long shareSeq, Integer sharedByUserSeq) {
        var query = shareTargetConverter.toShareTargetFindQuery(shareSeq, sharedByUserSeq);
        return shareTargetQueryRepository.findShareTargetPartitionKeys(query);
    }
    
    @Override
    public List<ShareTargetNearestData> findNearestAncestorShareTargets(long shareSeq, int userSeq, boolean isDelete) {
        var query = shareTargetConverter.toShareTargetNearestFindQuery(shareSeq, userSeq, Collections.emptyList(), isDelete);
        return shareTargetQueryRepository.findNearestAncestorShareTargets(query);
    }
    
    @Override
    public List<ShareTargetNearestData> findNearestAncestorShareTargetsByUsers(long shareSeq, int userSeq, Collection<Integer> targetUserSeqList, boolean isDelete) {
        var query = shareTargetConverter.toShareTargetNearestFindQuery(shareSeq, userSeq, targetUserSeqList, isDelete);
        return shareTargetQueryRepository.findNearestAncestorShareTargetsByUsers(query);
    }
    
    @Override
    public List<UnsharedTargetsData> findUnsharedDescendantsByTargets(Collection<Integer> targetUserSeqList, int userSeq, long shareSeq, boolean isDelete) {
        var query = shareTargetConverter.toUnsharedTargetsFindQuery(targetUserSeqList, userSeq, shareSeq, isDelete);
        return shareTargetQueryRepository.findUnsharedDescendantsByTargets(query);
    }
    
    @Override
    public List<ShareTargetUserData> findShareTargetUsersByShares(Collection<Long> shareSeqList, int sharedByUserSeq, boolean isDelete) {
        var query = shareTargetConverter.toShareTargetUsersQuery(shareSeqList, sharedByUserSeq, null, null, isDelete);
        return shareTargetQueryRepository.findShareTargetUsersByShares(query);
    }
    @Override
    public List<ShareTargetUserData> findShareTargetUsersByResource(long resourceSeq, int sharedByUserSeq, int targetUserSeq, boolean isDelete) {
        var query = shareTargetConverter.toShareTargetUsersQuery(Collections.emptyList(), sharedByUserSeq, targetUserSeq, resourceSeq, isDelete);
        return shareTargetQueryRepository.findShareTargetUsersByResource(query);
    }
    
    @Override
    public List<ShareTargetUserData> findShareTargetUsersByDeletedResource(long resourceSeq, int sharedByUserSeq) {
        var query = shareTargetConverter.toShareTargetUsersQuery(Collections.emptyList(), sharedByUserSeq, null, resourceSeq, null);
        return shareTargetQueryRepository.findShareTargetUsersByDeletedResource(query);
    }
    
    @Override
    public List<ShareTargetResourceUsersData> findShareTargetResourceUsersByResources(Collection<Long> resourceSeqList, int sharedByUserSeq, int targetUserSeq, boolean isDelete) {
        var query = shareTargetConverter.toShareTargetResourceUsersQuery(resourceSeqList, sharedByUserSeq, targetUserSeq, isDelete);
        return shareTargetQueryRepository.findShareTargetResourceUsersByResources(query);
    }
    
    @Override
    public List<ShareTargetResourceUsersData> findShareTargetResourceUsersByDeletedResources(Collection<Long> resourceSeqList, int sharedByUserSeq) {
        var query = shareTargetConverter.toShareTargetResourceUsersQuery(resourceSeqList, sharedByUserSeq, null, null);
        return shareTargetQueryRepository.findShareTargetResourceUsersByDeletedResources(query);
    }
    
    
}

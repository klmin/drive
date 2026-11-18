package com.thinkfree.drive.share.target.service;

import com.thinkfree.drive.share.target.data.*;
import com.thinkfree.drive.share.target.query.ShareTargetListQuery;

import java.util.Collection;
import java.util.List;


public interface ShareTargetFindService {
    
    List<ShareTargetResourceData> findAll(ShareTargetListQuery query);
    List<ShareTargetPartitionKeyData> findShareTargetPartitionKeys(Long shareSeq, Integer sharedByUserSeq);
    List<ShareTargetNearestData> findNearestAncestorShareTargets(long shareSeq, int userSeq, boolean isDelete);
    List<ShareTargetNearestData> findNearestAncestorShareTargetsByUsers(long shareSeq, int userSeq, Collection<Integer> targetUserSeqList, boolean isDelete);
    List<UnsharedTargetsData> findUnsharedDescendantsByTargets(Collection<Integer> targetUserSeqList, int userSeq, long shareSeq, boolean isDelete);
    List<ShareTargetUserData> findShareTargetUsersByShares(Collection<Long> shareSeqList, int sharedByUserSeq, boolean isDelete);
    List<ShareTargetUserData> findShareTargetUsersByResource(long resourceSeq, int sharedByUserSeq, int targetUserSeq, boolean isDelete);
    List<ShareTargetUserData> findShareTargetUsersByDeletedResource(long resourceSeq, int sharedByUserSeq);
    List<ShareTargetResourceUsersData> findShareTargetResourceUsersByResources(Collection<Long> resourceSeqList, int sharedByUserSeq, int targetUserSeq, boolean isDelete);
    List<ShareTargetResourceUsersData> findShareTargetResourceUsersByDeletedResources(Collection<Long> resourceSeqList, int sharedByUserSeq);
    
}

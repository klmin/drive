package com.thinkfree.drive.cleanup.resource.service.impl;

import com.thinkfree.drive.cleanup.resource.service.ResourceCleanUpService;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourceQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourcesQuery;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderService;
import com.thinkfree.drive.recent.service.RecentService;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.share.target.service.ShareTargetService;
import com.thinkfree.drive.starred.service.StarredService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultResourceCleanUpService implements ResourceCleanUpService {
    
    private final ShareTargetService shareTargetService;
    private final StarredService starredService;
    private final RecentService recentService;
    private final ShareService shareService;
    private final PinnedFolderService pinnedFolderService;
    

    @Override
    public void deleteResourceCascade(long resourceSeq, int userSeq) {
        deleteResourceCascade(resourceSeq, userSeq, false);
    }

    private void deleteResourceCascade(long resourceSeq, int userSeq, boolean isThrow) {
        var shareDeleteCount = shareService.deleteByResource(resourceSeq, userSeq);
        if(isThrow) {
            DriveAssert.throwIfZero(shareDeleteCount, ShareErrorCode.DELETE_FAILED);
        }
        shareTargetService.deleteByResource(resourceSeq, userSeq);
        starredService.deleteByResource(resourceSeq, userSeq);
        recentService.deleteByResource(resourceSeq, userSeq);

    }

    @Override
    public void deleteResourcesCascade(Collection<Long> resourceSeqList, int userSeq) {
        shareService.deleteByResources(resourceSeqList, userSeq);
        shareTargetService.deleteByResources(resourceSeqList, userSeq);
        starredService.deleteByResources(resourceSeqList, userSeq);
        recentService.deleteByResources(resourceSeqList, userSeq);
    }
    
    @Override
    public void deleteRelatedDataExcludingOwner(ResourceType resourceType, Collection<Long> resourceSeqList, Long resourceSeq, int userSeq, int sharedByUserSeq) {
        if (resourceType.isFile()) {
            deleteRelatedDataExcludingOwner(resourceSeq, userSeq, sharedByUserSeq);
        } else {
            deleteRelatedDataExcludingOwner(resourceSeqList, userSeq, sharedByUserSeq);
        }
    }
    
    @Override
    public void deleteSpecificShare(ResourceType resourceType, Collection<Long> resourceSeqList, Long resourceSeq, int userSeq, int sharedByUserSeq) {
        if (resourceType.isFile()) {
            deleteSpecificShare(resourceSeq, userSeq, sharedByUserSeq);
        } else {
            deleteSpecificShare(resourceSeqList, userSeq, sharedByUserSeq);
        }
    }
    
    @Override
    public void deletePinnedFolder(int sharedByUserSeq, long resourceSeq) {
        var pinnedFolderDeleteQuery = new PinnedFolderDeleteResourceQuery(null, sharedByUserSeq, resourceSeq);
        pinnedFolderService.unpinFolderByResource(pinnedFolderDeleteQuery);
    }
    
    @Override
    public void deletePinnedFolders(int sharedByUserSeq, List<Long> resourceSeqList) {
        var pinnedFolderDeleteQuery = new PinnedFolderDeleteResourcesQuery(null, sharedByUserSeq, resourceSeqList, null, null);
        pinnedFolderService.unpinFolderByResources(pinnedFolderDeleteQuery);
    }
    
    private void deleteSpecificShare(long resourceSeq, int userSeq, int sharedByUserSeq){
        shareService.deleteByResource(resourceSeq, sharedByUserSeq);
        shareTargetService.deleteByResource(resourceSeq, sharedByUserSeq);
        deleteRelatedDataExcludingOwner(resourceSeq, userSeq, sharedByUserSeq);
    }
    
    private void deleteSpecificShare(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq){
        shareService.deleteByResources(resourceSeqList, sharedByUserSeq);
        shareTargetService.deleteByResources(resourceSeqList, sharedByUserSeq);
        deleteRelatedDataExcludingOwner(resourceSeqList, userSeq, sharedByUserSeq);
    }
    
    private void deleteRelatedDataExcludingOwner(long resourceSeq, int userSeq, int sharedByUserSeq){
        starredService.deleteByResourceExcludingOwner(resourceSeq, userSeq, sharedByUserSeq);
        recentService.deleteByResourceExcludingOwner(resourceSeq, userSeq, sharedByUserSeq);
    }
    
    private void deleteRelatedDataExcludingOwner(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq){
        starredService.deleteByResourcesExcludingOwner(resourceSeqList, userSeq, sharedByUserSeq);
        recentService.deleteByResourcesExcludingOwner(resourceSeqList, userSeq, sharedByUserSeq);
        var pinnedFolderDeleteResourcesQuery = new PinnedFolderDeleteResourcesQuery(userSeq, sharedByUserSeq, List.copyOf(resourceSeqList), null, null);
        pinnedFolderService.unpinFolderByResourcesExcludingOwner(pinnedFolderDeleteResourcesQuery);
    }
    
    
}

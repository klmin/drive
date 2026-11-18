package com.thinkfree.drive.cleanup.resource.service;

import com.thinkfree.drive.resource.enums.ResourceType;

import java.util.Collection;
import java.util.List;

public interface ResourceCleanUpService {

    void deleteResourceCascade(long resourceSeq, int userSeq);
    void deleteResourcesCascade(Collection<Long> resourceSeqList, int userSeq);
    void deleteRelatedDataExcludingOwner(ResourceType resourceType, Collection<Long> resourceSeqList, Long resourceSeq, int userSeq, int sharedByUserSeq);
    void deleteSpecificShare(ResourceType resourceType, Collection<Long> resourceSeqList, Long resourceSeq, int userSeq, int sharedByUserSeq);
    void deletePinnedFolder(int sharedByUserSeq, long resourceSeq);
    void deletePinnedFolders(int sharedByUserSeq, List<Long> resourceSeqList);
    
}

package com.thinkfree.drive.resource.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ResourceVersionHierarchyStats {
   
    private final Long totalSizeByte;
    private final Set<Long> resourceSeqList;
    private final Set<Long> fileResourceSeqList;
    private final Boolean hasVersion;
    
    public static ResourceVersionHierarchyStats create(Long totalSizeByte, Set<Long> resourceSeqList, Set<Long> fileResourceSeqList, Boolean hasVersion) {
        return new ResourceVersionHierarchyStats(totalSizeByte, resourceSeqList, fileResourceSeqList, hasVersion);
    }
}

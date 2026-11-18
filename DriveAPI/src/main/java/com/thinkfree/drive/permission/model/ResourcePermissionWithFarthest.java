package com.thinkfree.drive.permission.model;

import com.thinkfree.drive.partition.PartitionKeyProvider;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
public class ResourcePermissionWithFarthest implements PartitionKeyProvider {
    
    private ResourcePermission resourcePermission;
    private Long parentFolderSeq;
    private String parentFolderName;
    private String fullPathSeq;
    private String fullPath;
    
    public static ResourcePermissionWithFarthest from(ResourceShareFarthest resourceShareFarthest, ResourcePermission resourcePermission) {
        return ResourcePermissionWithFarthest.builder()
                                             .resourcePermission(resourcePermission)
                                             .parentFolderSeq(resourceShareFarthest.getParentFolderSeq())
                                             .parentFolderName(resourceShareFarthest.getParentFolderName())
                                             .fullPathSeq(resourceShareFarthest.getFullPathSeq())
                                             .fullPath(resourceShareFarthest.getFullPath())
                                             .build();

    }
    
    
    @Override
    public Integer getSharedByUserSeq() {
        return resourcePermission.getUserSeq();
    }
    
    @Override
    public Long getResourceSeq() {
        return resourcePermission.getResourceSeq();
    }
}

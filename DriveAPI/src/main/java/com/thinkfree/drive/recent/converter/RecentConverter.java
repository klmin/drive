package com.thinkfree.drive.recent.converter;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import com.thinkfree.drive.recent.data.RecentData;
import com.thinkfree.drive.recent.data.RecentResourceShareData;
import com.thinkfree.drive.recent.dto.RecentSaveDto;
import com.thinkfree.drive.recent.entity.Recent;
import com.thinkfree.drive.recent.query.RecentDeleteResourceQuery;
import com.thinkfree.drive.recent.query.RecentDeleteResourcesQuery;
import com.thinkfree.drive.recent.query.RecentDeleteShareTargetsQuery;
import com.thinkfree.drive.recent.query.RecentDeleteUserResourcesQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring")
public interface RecentConverter {
    
    Recent toSaveRecent(RecentSaveDto dto);
    
    @Mapping(target = "resourceSeq", source = "data.resourceSeq")
    @Mapping(target = "parentFolderSeq", source = "resourcePermissionWithFarthest.parentFolderSeq")
    @Mapping(target = "parentFolderName", source = "resourcePermissionWithFarthest.parentFolderName")
    @Mapping(target = "fullPathSeq", source = "resourcePermissionWithFarthest.fullPathSeq")
    @Mapping(target = "fullPath", source = "resourcePermissionWithFarthest.fullPath")
    @Mapping(target = "sharedByUserSeq", source = "data.sharedByUserSeq")
    @Mapping(target = "shareSeq", source = "resourcePermissionWithFarthest.resourcePermission.shareSeq")
    @Mapping(target = "shareResourceSeq", source = "resourcePermissionWithFarthest.resourcePermission.shareResourceSeq")
    @Mapping(target = "shareTargetSeq", source = "resourcePermissionWithFarthest.resourcePermission.shareTargetSeq")
    @Mapping(target = "canView", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canView")
    @Mapping(target = "canEdit", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canEdit")
    @Mapping(target = "canDownload", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canDownload")
    @Mapping(target = "canUpload", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canUpload")
    @Mapping(target = "canDelete", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canDelete")
    @Mapping(target = "canShare", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canShare")
    @Mapping(target = "isShared", source = "resourcePermissionWithFarthest.resourcePermission.isShared")
    @Mapping(target = "resourceLocation", source = "resourcePermissionWithFarthest.resourcePermission.resourceLocation")
    RecentResourceShareData toRecentResourceShareData(RecentData data, ResourcePermissionWithFarthest resourcePermissionWithFarthest);

    RecentDeleteResourceQuery toRecentDeleteResourceQuery(Long resourceSeq, Integer userSeq, int sharedByUserSeq, List<Integer> excludeUserSeqList);
    RecentDeleteResourcesQuery toRecentDeleteResourcesQuery(Collection<Long> resourceSeqList, Integer userSeq, int sharedByUserSeq, List<Integer> excludeUserSeqList);
    RecentDeleteResourcesQuery toRecentDeleteOtherTenantResourcesQuery(Collection<Long> resourceSeqList, Integer sharedByUserSeq, Integer tenantSeq, List<Integer> excludeUserSeqList);
    RecentDeleteShareTargetsQuery toRecentDeleteShareTargetsQuery(Integer sharedByUserSeq, Collection<PartitionKey> keys);
    
    RecentDeleteUserResourcesQuery toRecentDeleteUserResourcesQuery(UserResourceGroup userResourceGroup, Integer sharedByUserSeq);
    
    default List<RecentDeleteUserResourcesQuery> toRecentDeleteUserResourcesQueryList(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq){
        return userResourceGroups.stream()
                                  .map(u -> toRecentDeleteUserResourcesQuery(u, sharedByUserSeq))
                                  .toList();
    }

}


package com.thinkfree.drive.starred.converter;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import com.thinkfree.drive.starred.data.StarredData;
import com.thinkfree.drive.starred.data.StarredResourceShareData;
import com.thinkfree.drive.starred.dto.StarredSaveDto;
import com.thinkfree.drive.starred.entity.Starred;
import com.thinkfree.drive.starred.query.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring")
public interface StarredConverter {

    Starred toSaveStarred(StarredSaveDto dto);
    StarredDeleteQuery toDeleteQuery(Long starredSeq, int userSeq);
    
    @Mapping(target = "resourceSeq", source = "starredData.resourceSeq")
    @Mapping(target = "parentFolderSeq", source = "resourcePermissionWithFarthest.parentFolderSeq")
    @Mapping(target = "parentFolderName", source = "resourcePermissionWithFarthest.parentFolderName")
    @Mapping(target = "fullPathSeq", source = "resourcePermissionWithFarthest.fullPathSeq")
    @Mapping(target = "fullPath", source = "resourcePermissionWithFarthest.fullPath")
    @Mapping(target = "sharedByUserSeq", source = "starredData.sharedByUserSeq")
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
    StarredResourceShareData toStarredResourceShareData(StarredData starredData, ResourcePermissionWithFarthest resourcePermissionWithFarthest);
    
    StarredDeleteResourceQuery toStarredDeleteByResourceQuery(Long resourceSeq, Integer userSeq, int sharedByUserSeq, List<Integer> excludeUserSeqList);

    StarredDeleteResourcesQuery toStarredDeleteByResourcesQuery(Collection<Long> resourceSeqList, Integer userSeq, int sharedByUserSeq, List<Integer> excludeUserSeqList);

    StarredDeleteResourcesQuery toStarredDeleteOtherTenantResourcesQuery(Collection<Long> resourceSeqList, Integer sharedByUserSeq, Integer tenantSeq, List<Integer> excludeUserSeqList);
    
    StarredDeleteShareTargetsQuery toStarredDeleteShareTargetsQuery(Integer sharedByUserSeq, Collection<PartitionKey> keys);
    
    StarredDeleteUserResourcesQuery toStarredDeleteUserResourcesQuery(UserResourceGroup userResourceGroup, Integer sharedByUserSeq);
    
    default List<StarredDeleteUserResourcesQuery> toStarredDeleteUserResourcesQueryList(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq){
        return userResourceGroups.stream()
                                  .map(u -> toStarredDeleteUserResourcesQuery(u, sharedByUserSeq))
                                  .toList();
    }

}

package com.thinkfree.drive.share.target.converter;

import com.thinkfree.drive.partition.ShareTargetPartitionKey;
import com.thinkfree.drive.permission.model.Permissions;
import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.query.ShareTargetNearestFindQuery;
import com.thinkfree.drive.share.target.data.ShareTargetData;
import com.thinkfree.drive.share.target.data.ShareTargetPartitionKeyData;
import com.thinkfree.drive.share.target.data.ShareTargetResourceData;
import com.thinkfree.drive.share.target.dto.ShareTargetSaveDto;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.share.target.dto.ShareTargetsSaveDto;
import com.thinkfree.drive.share.target.entity.ShareTarget;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.query.*;
import com.thinkfree.drive.util.SecurityUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", imports = {Instant.class, SecurityUtil.class})
public interface ShareTargetConverter {
    
    @Mapping(target = "shareDate", expression = "java(dto.getShareDate() == null ? shareDate : dto.getShareDate())")
    @Mapping(target = "updateUserSeq", expression = "java(SecurityUtil.getUserSeq())")
    @Mapping(target = "updateDate", expression = "java(Instant.now())")
    ShareTarget toSaveShareTarget(ShareTargetSaveDto dto, Instant shareDate);
    
    @Mapping(target = "shareDate", expression = "java(dto.getShareDate() == null ? shareDate : dto.getShareDate())")
    @Mapping(target = "updateUserSeq", expression = "java(SecurityUtil.getUserSeq())")
    @Mapping(target = "updateDate", expression = "java(Instant.now())")
    ShareTarget toSaveShareTarget(ShareTargetUpsertDto dto, Instant shareDate);
    
    @Mapping(target = "resourceSeq", source = "data.resourceSeq")
    @Mapping(target = "sharedByUserSeq", source = "data.sharedByUserSeq")
    @Mapping(target = "parentFolderSeq", source = "resourcePermissionWithFarthest.parentFolderSeq")
    @Mapping(target = "parentFolderName", source = "resourcePermissionWithFarthest.parentFolderName")
    @Mapping(target = "fullPathSeq", source = "resourcePermissionWithFarthest.fullPathSeq")
    @Mapping(target = "fullPath", source = "resourcePermissionWithFarthest.fullPath")
    @Mapping(target = "canView", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canView")
    @Mapping(target = "canEdit", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canEdit")
    @Mapping(target = "canDownload", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canDownload")
    @Mapping(target = "canUpload", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canUpload")
    @Mapping(target = "canDelete", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canDelete")
    @Mapping(target = "canShare", source = "resourcePermissionWithFarthest.resourcePermission.permissions.canShare")
    ShareTargetResourceData toShareTargetResourceData(ShareTargetData data, ResourcePermissionWithFarthest resourcePermissionWithFarthest);
    ShareTargetDeleteResourceQuery toShareTargetDeleteResourceQuery(Long resourceSeq, int sharedByUserSeq);
    ShareTargetDeleteResourcesQuery toShareTargetDeleteResourcesQuery(Collection<Long> resourceSeqList, int sharedByUserSeq);
    
    Permissions toPermissions(PermissionHolder permissionHolder);
    
    ShareTargetFindQuery toShareTargetFindQuery(Long shareSeq, Integer sharedByUserSeq);
    
    ShareTargetPartitionKey toShareTargetPartitionKey(ShareTargetUpsertDto dto);
    
    Set<ShareTargetPartitionKey> toShareTargetPartitionKeys(List<ShareTargetPartitionKeyData> data);
    
    ShareTargetsSaveDto toShareTargetsSaveDto(ShareType shareType, ResourceType resourceType, List<ShareTargetSaveDto> targets);
    
    ShareTargetNearestFindQuery toShareTargetNearestFindQuery(Long shareSeq, int userSeq, Collection<Integer> targetUserSeqList, boolean isDelete);
    ShareTargetDeleteKeysQuery toShareTargetDeleteKeysQuery(Collection<ShareTargetDeleteKey> deleteKeys, int sharedByUserSeq);
    UnsharedTargetsFindQuery toUnsharedTargetsFindQuery(Collection<Integer> targetUserSeqList, int userSeq, Long shareSeq, boolean isDelete);
    
    ShareTargetUsersQuery toShareTargetUsersQuery(Collection<Long> shareSeqList, int sharedByUserSeq, Integer targetUserSeq, Long resourceSeq, Boolean isDelete);
    ShareTargetResourceUsersQuery toShareTargetResourceUsersQuery(Collection<Long> resourceSeqList, int sharedByUserSeq, Integer targetUserSeq, Boolean isDelete);
    
}

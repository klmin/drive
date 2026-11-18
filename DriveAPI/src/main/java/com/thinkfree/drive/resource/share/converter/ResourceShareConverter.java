package com.thinkfree.drive.resource.share.converter;

import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import com.thinkfree.drive.resource.share.data.ResourceSharedData;
import com.thinkfree.drive.resource.share.data.ResourceSharedListData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ResourceShareConverter {
    
    @Mapping(target = "resourceSeq", source = "data.resourceSeq")
    @Mapping(target = "parentFolderSeq", source = "resourcePermissionWithFarthest.parentFolderSeq")
    @Mapping(target = "parentFolderName", source = "resourcePermissionWithFarthest.parentFolderName")
    @Mapping(target = "fullPathSeq", source = "resourcePermissionWithFarthest.fullPathSeq")
    @Mapping(target = "fullPath", source = "resourcePermissionWithFarthest.fullPath")
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
    ResourceSharedListData toResourceSharedListData(ResourceSharedData data, ResourcePermissionWithFarthest resourcePermissionWithFarthest);
    
}

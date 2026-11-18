package com.thinkfree.drive.application.resource.converter;

import com.thinkfree.drive.application.resource.info.ResourceDetailInfo;
import com.thinkfree.drive.application.resource.info.ResourceExistsInfo;
import com.thinkfree.drive.application.resource.info.ResourceListInfo;
import com.thinkfree.drive.application.resource.info.ResourceTreeInfo;
import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import com.thinkfree.drive.resource.data.ResourceData;
import com.thinkfree.drive.resource.data.ResourceExistsData;
import com.thinkfree.drive.resource.data.ResourceTreeData;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.global.data.ResourceGlobalListData;
import com.thinkfree.drive.resource.share.data.ResourceSharedListData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {ResourceLocation.class})
public interface ResourceInfoConverter {

    ResourceExistsInfo toExistsInfo(ResourceExistsData data);

    @Mapping(target = "resourceRegisterUserId",     source = "registerUserId")
    @Mapping(target = "resourceRegisterUserName",   source = "registerUserName")
    @Mapping(target = "resourceRegisterUserStatus", source = "registerUserStatus")
    @Mapping(target = "resourceRegisterDate",       source = "registerDate")
    @Mapping(target = "resourceUpdateUserId",       source = "updateUserId")
    @Mapping(target = "resourceUpdateUserName",     source = "updateUserName")
    @Mapping(target = "resourceUpdateUserStatus",   source = "updateUserStatus")
    @Mapping(target = "resourceUpdateDate",         source = "updateDate")
    @Mapping(target = "resourceLocation",           expression = "java(ResourceLocation.MY)")
    ResourceListInfo toListInfo(ResourceData resource);

    List<ResourceListInfo> toListInfos(List<ResourceData> resource);

    @Mapping(target = "resourceRegisterUserId",     source = "registerUserId")
    @Mapping(target = "resourceRegisterUserName",   source = "registerUserName")
    @Mapping(target = "resourceRegisterUserStatus", source = "registerUserStatus")
    @Mapping(target = "resourceRegisterDate",       source = "registerDate")
    @Mapping(target = "resourceUpdateUserId",       source = "updateUserId")
    @Mapping(target = "resourceUpdateUserName",     source = "updateUserName")
    @Mapping(target = "resourceUpdateUserStatus",   source = "updateUserStatus")
    @Mapping(target = "resourceUpdateDate",         source = "updateDate")
    ResourceListInfo toListInfo(ResourceSharedListData resourceSharedData);

    List<ResourceListInfo> toListInfosFromShared(List<ResourceSharedListData> shareResource);

    @Mapping(target = "resourceRegisterUserId",     source = "registerUserId")
    @Mapping(target = "resourceRegisterUserName",   source = "registerUserName")
    @Mapping(target = "resourceRegisterUserStatus", source = "registerUserStatus")
    @Mapping(target = "resourceRegisterDate",       source = "registerDate")
    @Mapping(target = "resourceUpdateUserId",       source = "updateUserId")
    @Mapping(target = "resourceUpdateUserName",     source = "updateUserName")
    @Mapping(target = "resourceUpdateUserStatus",   source = "updateUserStatus")
    @Mapping(target = "resourceUpdateDate",         source = "updateDate")
    ResourceListInfo toListInfo(ResourceGlobalListData resourceGlobalResult);

    List<ResourceListInfo> toListInfosFromGlobal(List<ResourceGlobalListData> resourceGlobalResult);

    @Mapping(target = "resourceSeq",                source = "resource.resourceSeq")
    @Mapping(target = "resourceRegisterUserId",     source = "resource.registerUserId")
    @Mapping(target = "resourceRegisterUserName",   source = "resource.registerUserName")
    @Mapping(target = "resourceRegisterUserStatus", source = "resource.registerUserStatus")
    @Mapping(target = "resourceRegisterDate",       source = "resource.registerDate")
    @Mapping(target = "resourceUpdateUserId",       source = "resource.updateUserId")
    @Mapping(target = "resourceUpdateUserName",     source = "resource.updateUserName")
    @Mapping(target = "resourceUpdateUserStatus",   source = "resource.updateUserStatus")
    @Mapping(target = "resourceUpdateDate",         source = "resource.updateDate")
    @Mapping(target = "parentFolderSeq",            source = "resourcePermissionWithFarthest.parentFolderSeq")
    @Mapping(target = "parentFolderName",           source = "resourcePermissionWithFarthest.parentFolderName")
    @Mapping(target = "fullPathSeq",                source = "resourcePermissionWithFarthest.fullPathSeq")
    @Mapping(target = "fullPath",                   source = "resourcePermissionWithFarthest.fullPath")
    @Mapping(target = "canView",                    source = "resourcePermissionWithFarthest.resourcePermission.permissions.canView")
    @Mapping(target = "canEdit",                    source = "resourcePermissionWithFarthest.resourcePermission.permissions.canEdit")
    @Mapping(target = "canDownload",                source = "resourcePermissionWithFarthest.resourcePermission.permissions.canDownload")
    @Mapping(target = "canUpload",                  source = "resourcePermissionWithFarthest.resourcePermission.permissions.canUpload")
    @Mapping(target = "canDelete",                  source = "resourcePermissionWithFarthest.resourcePermission.permissions.canDelete")
    @Mapping(target = "canShare",                   source = "resourcePermissionWithFarthest.resourcePermission.permissions.canShare")
    ResourceDetailInfo toDetailInfo(ResourceData resource, ResourcePermissionWithFarthest resourcePermissionWithFarthest);

    List<ResourceTreeInfo> toTreeInfos(List<ResourceTreeData> list);

}

package com.thinkfree.drive.share.converter;

import com.thinkfree.drive.permission.model.Permissions;
import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.share.data.ShareUserData;
import com.thinkfree.drive.share.dto.ShareSaveDto;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.query.*;
import com.thinkfree.drive.share.target.data.ShareTargetUserData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ShareConverter {
    
    ShareDeleteResourceQuery toShareDeleteResourceQuery(Long resourceSeq, int userSeq);
    ShareDeleteResourcesQuery toShareDeleteResourcesQuery(Collection<Long> resourceSeqList, int userSeq);
    
    @Mapping(target = "canView", defaultValue = "true")
    @Mapping(target = "canEdit", defaultValue = "false")
    @Mapping(target = "canDownload", defaultValue = "false")
    @Mapping(target = "canUpload", defaultValue = "false")
    @Mapping(target = "canDelete", defaultValue = "false")
    @Mapping(target = "canShare", defaultValue = "false")
    @Mapping(target = "isPasswordEnabled", defaultValue = "false")
    Share toSaveShare(ShareSaveDto dto);
    
    SharedResourceFindQuery toSharedResourceFindQuery(Long shareSeq, Long resourceSeq, int userSeq, boolean isDelete);
    
    
    Permissions toPermissions(PermissionHolder permissionHolder);
    
    ShareWithTargetsFindQuery toShareWithTargetsFindQuery(Long shareSeq, int userSeq, boolean isDelete);
    
    ShareDeleteQuery toShareDeleteQuery(Long shareSeq, int userSeq);
    
    ShareUsersFindQuery toShareUsersFindQuery(Long shareSeq, int userSeq, boolean isDelete);
    SharedResourceNearestFindQuery toShareNearestAncestorFindQuery(Long resourceSeq, Long shareSeq, int userSeq, boolean isDelete);
    SharedResourceNearestFindQuery toShareNearestDescendantFindQuery(Long resourceSeq, Long shareSeq, Long rootResourceSeq, int userSeq, boolean isDelete);
    UnsharedResourcesFindQuery toUnSharedResourcesFindQuery(Long shareSeq, int userSeq, boolean isDelete);
    
    List<ShareUserData> toShareUsersData(List<ShareTargetUserData> shareTargetUsers);
    
}

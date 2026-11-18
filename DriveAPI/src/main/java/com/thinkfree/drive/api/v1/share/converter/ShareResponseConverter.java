package com.thinkfree.drive.api.v1.share.converter;

import com.thinkfree.drive.api.v1.share.response.*;
import com.thinkfree.drive.application.share.info.ShareDetailInfo;
import com.thinkfree.drive.application.share.info.ShareLinkAccessInfo;
import com.thinkfree.drive.application.share.info.ShareSaveInfo;
import com.thinkfree.drive.application.share.info.ShareTypesInfo;
import com.thinkfree.drive.application.share.info.ShareUserInfo;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShareResponseConverter {

    ShareSaveResponse toShareSaveResponse(ShareSaveInfo info);

    ShareDetailResponse toShareDetailResponse(ShareDetailInfo info);

    List<ShareUserListResponse> toShareUsersResponse(List<ShareUserInfo> infos);

    ShareUserSearchResponse.UserSearchItem toUserSearchItem(ShareUserInfo info);

    default ShareUserSearchResponse toShareUserSearchResponse(List<ShareUserInfo> infos) {
        return new ShareUserSearchResponse(infos.stream().map(this::toUserSearchItem).toList());
    }

    ShareLinkAccessResponse toShareLinkAccessResponse(ShareLinkAccessInfo info);

    ShareTypesResponse toShareTypesResponse(ShareTypesInfo info);

}

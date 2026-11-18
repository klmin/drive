package com.thinkfree.drive.application.share.converter;

import com.thinkfree.drive.application.share.info.SharedListInfo;
import com.thinkfree.drive.share.target.data.ShareTargetResourceData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SharedInfoConverter {

    // register/update는 데이터상 공유 대상(share_target) 행의 것 — 웹 기존 계약(shareTarget* 접두 명명) 그대로 인계
    @Mapping(target = "shareTargetRegisterUserId", source = "registerUserId")
    @Mapping(target = "shareTargetRegisterUserName", source = "registerUserName")
    @Mapping(target = "shareTargetRegisterUserStatus", source = "registerUserStatus")
    @Mapping(target = "shareTargetRegisterDate", source = "registerDate")
    @Mapping(target = "shareTargetUpdateUserId", source = "updateUserId")
    @Mapping(target = "shareTargetUpdateUserName", source = "updateUserName")
    @Mapping(target = "shareTargetUpdateUserStatus", source = "updateUserStatus")
    @Mapping(target = "shareTargetUpdateDate", source = "updateDate")
    SharedListInfo toListInfo(ShareTargetResourceData data);

    List<SharedListInfo> toListInfos(List<ShareTargetResourceData> data);

}

package com.thinkfree.drive.application.share.converter;

import com.thinkfree.drive.application.share.info.ShareDetailInfo;
import com.thinkfree.drive.application.share.info.ShareSaveInfo;
import com.thinkfree.drive.application.share.info.ShareUserInfo;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.share.data.ShareUserData;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.result.ShareSaveResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = DriveTime.class)
public interface ShareInfoConverter {

    // 공유 만료일은 달력 날짜 — 저장된 시각을 설정 시간대 날짜로
    @Mapping(target = "expireDate", expression = "java(DriveTime.toLocalDate(data.getExpireDate()))")
    ShareDetailInfo toDetailInfo(ShareWithTargetsData data, Boolean modifiable);

    ShareSaveInfo toSaveInfo(ShareSaveResult result);

    List<ShareUserInfo> toShareUserInfos(List<ShareUserData> data);

}

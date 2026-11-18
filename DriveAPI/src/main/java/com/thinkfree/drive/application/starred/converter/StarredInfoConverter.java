package com.thinkfree.drive.application.starred.converter;

import com.thinkfree.drive.application.starred.info.StarredListInfo;
import com.thinkfree.drive.starred.data.StarredResourceShareData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StarredInfoConverter {

    // 중요문서함 목록의 항목은 정의상 전부 즐겨찾기 상태 — 데이터에 없는 필드라 상수로 채운다 (웹 기존 계약과 동일)
    @Mapping(target = "isStarred", constant = "true")
    StarredListInfo toListInfo(StarredResourceShareData data);

    List<StarredListInfo> toListInfos(List<StarredResourceShareData> data);

}

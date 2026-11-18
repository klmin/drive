package com.thinkfree.drive.application.recent.converter;

import com.thinkfree.drive.application.recent.info.RecentListInfo;
import com.thinkfree.drive.recent.data.RecentResourceShareData;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecentInfoConverter {

    RecentListInfo toListInfo(RecentResourceShareData data);

    List<RecentListInfo> toListInfos(List<RecentResourceShareData> data);

}

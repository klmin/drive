package com.thinkfree.drive.application.version.converter;

import com.thinkfree.drive.application.version.info.VersionListInfo;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface VersionInfoConverter {

    VersionListInfo toListInfo(ResourceVersionData data);

    List<VersionListInfo> toListInfos(List<ResourceVersionData> data);

}

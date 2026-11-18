package com.thinkfree.drive.application.trash.converter;

import com.thinkfree.drive.application.trash.info.TrashListInfo;
import com.thinkfree.drive.trash.data.TrashData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrashInfoConverter {

    @Mapping(target = "resourceRegisterUserId",     source = "registerUserId")
    @Mapping(target = "resourceRegisterUserName",   source = "registerUserName")
    @Mapping(target = "resourceRegisterUserStatus", source = "registerUserStatus")
    @Mapping(target = "resourceRegisterDate",       source = "registerDate")
    @Mapping(target = "resourceUpdateUserId",       source = "updateUserId")
    @Mapping(target = "resourceUpdateUserName",     source = "updateUserName")
    @Mapping(target = "resourceUpdateUserStatus",   source = "updateUserStatus")
    @Mapping(target = "resourceUpdateDate",         source = "updateDate")
    TrashListInfo toListInfo(TrashData data);

    List<TrashListInfo> toListInfos(List<TrashData> data);

}

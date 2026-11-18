package com.thinkfree.drive.application.share.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.share.command.SharedListCommand;
import com.thinkfree.drive.share.target.query.ShareTargetListQuery;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {DriveTime.class, CursorPaginationQuery.class})
public interface SharedCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "userId", source = "command.ownerId")
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "from", expression = "java(DriveTime.startOfDay(command.startDate()))")
    @Mapping(target = "to", expression = "java(DriveTime.endExclusive(command.endDate()))")
    ShareTargetListQuery toListQuery(SharedListCommand command, int userSeq);

}

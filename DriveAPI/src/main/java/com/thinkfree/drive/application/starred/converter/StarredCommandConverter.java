package com.thinkfree.drive.application.starred.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.starred.command.StarredListCommand;
import com.thinkfree.drive.application.starred.command.StarredSaveCommand;
import com.thinkfree.drive.starred.dto.StarredSaveDto;
import com.thinkfree.drive.starred.query.StarredListQuery;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {DriveTime.class, CursorPaginationQuery.class})
public interface StarredCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "userId", source = "command.ownerId")
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "from", expression = "java(DriveTime.startOfDay(command.startDate()))")
    @Mapping(target = "to", expression = "java(DriveTime.endExclusive(command.endDate()))")
    StarredListQuery toListQuery(StarredListCommand command, int userSeq);

    @Mapping(target = "userSeq", source = "requestUserSeq")
    @Mapping(target = "resourceSeq", source = "command.resourceSeq")
    @Mapping(target = "sharedByUserSeq", source = "userSeq")
    StarredSaveDto toSaveDto(StarredSaveCommand command, int userSeq, int requestUserSeq);

}

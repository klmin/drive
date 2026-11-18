package com.thinkfree.drive.application.recent.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.recent.command.RecentListCommand;
import com.thinkfree.drive.application.recent.command.RecentSaveCommand;
import com.thinkfree.drive.recent.dto.RecentSaveDto;
import com.thinkfree.drive.recent.query.RecentListQuery;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = {DriveTime.class, Instant.class, CursorPaginationQuery.class})
public interface RecentCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "userId", source = "command.ownerId")
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "from", expression = "java(DriveTime.startOfDay(command.startDate()))")
    @Mapping(target = "to", expression = "java(DriveTime.endExclusive(command.endDate()))")
    RecentListQuery toListQuery(RecentListCommand command, int userSeq);

    @Mapping(target = "userSeq", source = "requestUserSeq")
    @Mapping(target = "resourceSeq", source = "command.resourceSeq")
    @Mapping(target = "sharedByUserSeq", source = "userSeq")
    @Mapping(target = "updateDate", expression = "java(Instant.now())")
    RecentSaveDto toSaveDto(RecentSaveCommand command, int userSeq, int requestUserSeq);

}

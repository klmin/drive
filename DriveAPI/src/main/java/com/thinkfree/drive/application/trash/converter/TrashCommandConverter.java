package com.thinkfree.drive.application.trash.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.trash.command.TrashListCommand;
import com.thinkfree.drive.resource.query.ResourceDeleteStateQuery;
import com.thinkfree.drive.trash.query.TrashDeleteQuery;
import com.thinkfree.drive.trash.query.TrashListQuery;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {DriveTime.class, CursorPaginationQuery.class})
public interface TrashCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "from", expression = "java(DriveTime.startOfDay(command.startDate()))")
    @Mapping(target = "to", expression = "java(DriveTime.endExclusive(command.endDate()))")
    TrashListQuery toListQuery(TrashListCommand command, int userSeq);

    TrashDeleteQuery toDeleteQuery(Long resourceSeq, int userSeq);

    // 폴더 휴지통 이동(soft delete) 상태 전환 쿼리 — 구 File/FolderCommandConverter의 toDeleteStateQuery 인계(파일은 W10 부터 Service 가 관리 엔티티로)
    @Mapping(target = "isDelete", constant = "true")
    ResourceDeleteStateQuery toFolderDeleteStateQuery(Long resourceSeq, String fullPathSeq, int userSeq);

}

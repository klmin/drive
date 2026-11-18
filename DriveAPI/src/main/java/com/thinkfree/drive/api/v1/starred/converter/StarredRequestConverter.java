package com.thinkfree.drive.api.v1.starred.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.starred.request.StarredListRequest;
import com.thinkfree.drive.api.v1.starred.request.StarredSaveRequest;
import com.thinkfree.drive.application.starred.command.StarredListCommand;
import com.thinkfree.drive.application.starred.command.StarredSaveCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query 생성은 application의 StarredCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface StarredRequestConverter {

    @Mapping(target = "resourceType", source = "request.rt")
    @Mapping(target = "ownerId",      source = "request.oi")
    @Mapping(target = "startDate",    source = "request.sd")
    @Mapping(target = "endDate",      source = "request.ed")
    @Mapping(target = "fileType",     source = "request.ft")
    @Mapping(target = "sortBy",       source = "request.sb")
    @Mapping(target = "sortOrder",    source = "request.so")
    @Mapping(target = "cursorValue",  source = "pagingRequest.cv")
    @Mapping(target = "cursorId",     source = "pagingRequest.ci")
    @Mapping(target = "cursorType",   source = "pagingRequest.ct")
    @Mapping(target = "pageSize",     source = "pagingRequest.ps")
    StarredListCommand toListCommand(StarredListRequest request, CursorPagingRequest pagingRequest);

    @Mapping(target = "resourceSeq",     source = "rs")
    @Mapping(target = "sharedByUserSeq", source = "ss")
    StarredSaveCommand toSaveCommand(StarredSaveRequest request);

}

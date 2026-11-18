package com.thinkfree.drive.api.v1.recent.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.recent.request.RecentListRequest;
import com.thinkfree.drive.api.v1.recent.request.RecentSaveRequest;
import com.thinkfree.drive.application.recent.command.RecentListCommand;
import com.thinkfree.drive.application.recent.command.RecentSaveCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query 생성은 application의 RecentCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface RecentRequestConverter {

    @Mapping(target = "resourceType", source = "request.rt")
    @Mapping(target = "ownerId",      source = "request.oi")
    @Mapping(target = "startDate",    source = "request.sd")
    @Mapping(target = "endDate",      source = "request.ed")
    @Mapping(target = "fileType",     source = "request.ft")
    @Mapping(target = "cursorValue",  source = "pagingRequest.cv")
    @Mapping(target = "cursorId",     source = "pagingRequest.ci")
    @Mapping(target = "cursorType",   source = "pagingRequest.ct")
    @Mapping(target = "pageSize",     source = "pagingRequest.ps")
    RecentListCommand toListCommand(RecentListRequest request, CursorPagingRequest pagingRequest);

    @Mapping(target = "resourceSeq",     source = "rs")
    @Mapping(target = "sharedByUserSeq", source = "ss")
    RecentSaveCommand toSaveCommand(RecentSaveRequest request);

}

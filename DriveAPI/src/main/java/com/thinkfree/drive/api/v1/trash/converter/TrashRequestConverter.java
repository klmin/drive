package com.thinkfree.drive.api.v1.trash.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.trash.request.TrashListRequest;
import com.thinkfree.drive.application.trash.command.TrashListCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query 생성은 application의 TrashCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface TrashRequestConverter {

    @Mapping(target = "resourceType", source = "request.rt")
    @Mapping(target = "deleteUserId", source = "request.di")
    @Mapping(target = "startDate",    source = "request.sd")
    @Mapping(target = "endDate",      source = "request.ed")
    @Mapping(target = "fileType",     source = "request.ft")
    @Mapping(target = "sortBy",       source = "request.sb")
    @Mapping(target = "sortOrder",    source = "request.so")
    @Mapping(target = "cursorValue",  source = "pagingRequest.cv")
    @Mapping(target = "cursorId",     source = "pagingRequest.ci")
    @Mapping(target = "cursorType",   source = "pagingRequest.ct")
    @Mapping(target = "pageSize",     source = "pagingRequest.ps")
    TrashListCommand toListCommand(TrashListRequest request, CursorPagingRequest pagingRequest);

}

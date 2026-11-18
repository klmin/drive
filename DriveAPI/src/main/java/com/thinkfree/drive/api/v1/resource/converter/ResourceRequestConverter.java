package com.thinkfree.drive.api.v1.resource.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.resource.request.ResourceDownloadRequest;
import com.thinkfree.drive.api.v1.resource.request.ResourceExistsRequest;
import com.thinkfree.drive.api.v1.resource.request.ResourceFindRequest;
import com.thinkfree.drive.api.v1.resource.request.ResourceListRequest;
import com.thinkfree.drive.application.resource.command.ResourceDownloadCommand;
import com.thinkfree.drive.application.resource.command.ResourceExistsCommand;
import com.thinkfree.drive.application.resource.command.ResourceFindCommand;
import com.thinkfree.drive.application.resource.command.ResourceSearchCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query 생성은 application의 ResourceCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface ResourceRequestConverter {

    @Mapping(target = "sharedByUserSeq", source = "request.ss")
    @Mapping(target = "searchKeyword",   source = "request.sk")
    @Mapping(target = "parentFolderSeq", source = "request.pfs")
    @Mapping(target = "resourceType",    source = "request.rt")
    @Mapping(target = "searchType",      source = "request.st")
    @Mapping(target = "ownerId",         source = "request.oi")
    @Mapping(target = "startDate",       source = "request.sd")
    @Mapping(target = "endDate",         source = "request.ed")
    @Mapping(target = "fileType",        source = "request.ft")
    @Mapping(target = "sortBy",          source = "request.sb")
    @Mapping(target = "sortOrder",       source = "request.so")
    @Mapping(target = "cursorValue",     source = "pagingRequest.cv")
    @Mapping(target = "cursorId",        source = "pagingRequest.ci")
    @Mapping(target = "cursorType",      source = "pagingRequest.ct")
    @Mapping(target = "pageSize",        source = "pagingRequest.ps")
    ResourceSearchCommand toSearchCommand(ResourceListRequest request, CursorPagingRequest pagingRequest);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    @Mapping(target = "resourceName",    source = "rn")
    @Mapping(target = "resourceType",    source = "rt")
    ResourceExistsCommand toExistsCommand(ResourceExistsRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "isDelete",        source = "d")
    ResourceFindCommand toFindCommand(ResourceFindRequest request);

    @Mapping(target = "items", source = "rl")
    ResourceDownloadCommand toDownloadCommand(ResourceDownloadRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "resourceSeq",     source = "rs")
    ResourceDownloadCommand.DownloadItem toDownloadItem(ResourceDownloadRequest.FileDownloadItem item);

}

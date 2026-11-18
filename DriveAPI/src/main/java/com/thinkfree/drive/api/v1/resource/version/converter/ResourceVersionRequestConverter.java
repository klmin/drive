package com.thinkfree.drive.api.v1.resource.version.converter;

import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.v1.resource.version.request.ResourceVersionDownloadRequest;
import com.thinkfree.drive.api.v1.resource.version.request.ResourceVersionListRequest;
import com.thinkfree.drive.application.version.command.VersionDownloadCommand;
import com.thinkfree.drive.application.version.command.VersionListCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 Request → application Command 변환 전담. 도메인 방향 매핑은 application의 VersionCommandConverter로 이관됨.
 */
@Mapper(componentModel = "spring")
public interface ResourceVersionRequestConverter {

    @Mapping(target = "resourceSeq", source = "request.resourceSeq")
    @Mapping(target = "sharedByUserSeq", source = "request.ss")
    @Mapping(target = "pageIndex", source = "pagingRequest.pi")
    @Mapping(target = "pageSize", source = "pagingRequest.ps")
    VersionListCommand toListCommand(ResourceVersionListRequest request, PagingRequest pagingRequest);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    VersionDownloadCommand toDownloadCommand(ResourceVersionDownloadRequest request);

}

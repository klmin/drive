package com.thinkfree.drive.application.version.converter;

import com.thinkfree.drive.api.page.query.PaginationQuery;
import com.thinkfree.drive.application.version.command.VersionListCommand;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.query.ResourceVersionFindQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionListQuery;
import com.thinkfree.drive.util.MapStructUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Command → 도메인 Dto/Query 변환 전담 — 구 api/v1 ResourceVersionRequestConverter의 도메인 방향 매핑을 그대로 인계.
 */
@Mapper(componentModel = "spring", imports = {PaginationQuery.class, ResourceVersionStatus.class, ResourceSaveType.class, MapStructUtil.class})
public interface VersionCommandConverter {

    @Mapping(target = "page", expression = "java(PaginationQuery.of(command.pageIndex(), command.pageSize()))")
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "resourceVersionStatus", expression = "java(ResourceVersionStatus.SUCCESS)")
    @Mapping(target = "userSeq", expression = "java(MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq))")
    @Mapping(target = "resourceSeq", source = "command.resourceSeq")
    ResourceVersionListQuery toListQuery(VersionListCommand command, int requestUserSeq);

    @Mapping(target = "status", expression = "java(ResourceVersionStatus.SUCCESS)")
    ResourceVersionFindQuery toFindQuery(Long resourceVersionSeq, Long resourceSeq, int userSeq);

    @Mapping(target = "resourceSaveType", expression = "java(ResourceSaveType.VERSION)")
    @Mapping(target = "ownerSeq", source = "requestUserSeq")
    FileUploadDto toFileUploadDto(Long resourceSeq, int userSeq, int requestUserSeq, Long fileSize);

}

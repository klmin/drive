package com.thinkfree.drive.application.resource.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.resource.command.ResourceExistsCommand;
import com.thinkfree.drive.application.resource.command.ResourceRenameCommand;
import com.thinkfree.drive.application.resource.command.ResourceSearchCommand;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.dto.ResourceRenameDto;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceExistsQuery;
import com.thinkfree.drive.resource.query.ResourceFindQuery;
import com.thinkfree.drive.resource.query.ResourceHierarchyQuery;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.SqlUtil;
import com.thinkfree.drive.util.file.FileExtractResult;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {DriveTime.class, CursorPaginationQuery.class, SqlUtil.class, ResourceType.class})
public interface ResourceCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "searchKeyword", expression = "java(SqlUtil.escape(command.searchKeyword()))")
    @Mapping(target = "userId", source = "command.ownerId")
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "from", expression = "java(DriveTime.startOfDay(command.startDate()))")
    @Mapping(target = "to", expression = "java(DriveTime.endExclusive(command.endDate()))")
    ResourceListQuery toListQuery(ResourceSearchCommand command, int userSeq);

    @Mapping(target = "isDelete", constant = "false")
    ResourceExistsQuery toExistsQuery(ResourceExistsCommand command, FileExtractResult extracted, int userSeq);

    default ResourceExistsQuery toExistsQuery(ResourceExistsCommand command, int userSeq) {
        FileExtractResult extracted = ResourceUtil.extract(command.resourceName());
        return toExistsQuery(command, extracted, userSeq);
    }

    ResourceFindQuery toFindQuery(Long resourceSeq, int userSeq, ResourceType resourceType, Boolean isDelete);

    ResourceHierarchyQuery toHierarchyQuery(List<Long> resourceSeqList, int userSeq);

    // 이동 usecase가 File/FolderUseCase → ResourceUseCase로 이사하면서 toMoveDto 매핑도 함께 인계 (구 File/FolderCommandConverter 소유)
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "resourceType", expression = "java(ResourceType.FILE)")
    ResourceMoveDto toFileMoveDto(Long resourceSeq, Long parentFolderSeq, int userSeq, int targetUserSeq);

    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "resourceType", expression = "java(ResourceType.FOLDER)")
    ResourceMoveDto toFolderMoveDto(Long resourceSeq, Long parentFolderSeq, int userSeq, int targetUserSeq);

    // 이름변경 usecase가 File/FolderUseCase → ResourceUseCase로 이사하면서 toRenameDto 매핑도 함께 인계 (구 File/FolderCommandConverter 소유)
    @Mapping(target = "resourceType", expression = "java(ResourceType.FILE)")
    @Mapping(target = "resourceName", source = "extracted.originalFileName")
    @Mapping(target = "nameConflictPolicy", source = "command.nameConflictPolicy")
    @Mapping(target = "originalFileName", source = "extracted.baseFileName")
    @Mapping(target = "isDelete", constant = "false")
    ResourceRenameDto toFileRenameDtoForNone(Long resourceSeq, ResourceRenameCommand command, FileExtractResult extracted, int userSeq);

    @Mapping(target = "resourceType", expression = "java(ResourceType.FILE)")
    @Mapping(target = "resourceName", source = "command.newResourceName")
    @Mapping(target = "nameConflictPolicy", source = "command.nameConflictPolicy")
    @Mapping(target = "isDelete", constant = "false")
    ResourceRenameDto toFileRenameDtoForAuto(Long resourceSeq, ResourceRenameCommand command, FileExtractResult extracted, int userSeq);

    default ResourceRenameDto toFileRenameDto(Long resourceSeq, ResourceRenameCommand command, int userSeq) {
        FileExtractResult extracted = ResourceUtil.extract(command.newResourceName());
        if(NameConflictPolicy.AUTO == command.nameConflictPolicy()){
            return toFileRenameDtoForAuto(resourceSeq, command, extracted, userSeq);
        }
        return toFileRenameDtoForNone(resourceSeq, command, extracted, userSeq);
    }

    @Mapping(target = "resourceType", expression = "java(ResourceType.FOLDER)")
    @Mapping(target = "resourceName", source = "extracted.originalFileName")
    @Mapping(target = "originalFileName", source = "extracted.baseFileName")
    @Mapping(target = "isDelete", constant = "false")
    ResourceRenameDto toFolderRenameDto(Long resourceSeq, FileExtractResult extracted, int userSeq);

    default ResourceRenameDto toFolderRenameDto(Long resourceSeq, ResourceRenameCommand command, int userSeq) {
        FileExtractResult extracted = ResourceUtil.extract(command.newResourceName());
        return toFolderRenameDto(resourceSeq, extracted, userSeq);
    }

}

package com.thinkfree.drive.application.folder.converter;

import com.thinkfree.drive.application.folder.command.FolderCreateCommand;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceHierarchyQuery;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.file.FileExtractResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Command → 도메인 Dto/Query 변환 전담 — 구 api/v1 FolderRequestConverter의 도메인 방향 매핑을 그대로 인계.
 */
@Mapper(componentModel = "spring", imports = {ResourceType.class, MapStructUtil.class})
public interface FolderCommandConverter {

    @Mapping(target = "userSeq", expression = "java(MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq))")
    @Mapping(target = "ownerSeq", source = "requestUserSeq")
    @Mapping(target = "parentFolderSeq", source = "command.parentFolderSeq")
    @Mapping(target = "baseFolderName", source = "extracted.baseFileName")
    @Mapping(target = "originalFolderName", source = "extracted.originalFileName")
    @Mapping(target = "copyFolder", source = "extracted.copyFile")
    FolderUploadDto toFolderUploadDto(FolderCreateCommand command, FileExtractResult extracted, int requestUserSeq);

    default FolderUploadDto toFolderUploadDto(FolderCreateCommand command, int requestUserSeq) {
        FileExtractResult extracted = ResourceUtil.extract(command.folderName());
        return toFolderUploadDto(command, extracted, requestUserSeq);
    }

    @Mapping(target = "resourceType", expression = "java(ResourceType.FOLDER)")
    ResourceHierarchyQuery toHierarchyQuery(List<Long> resourceSeqList, int userSeq);

}

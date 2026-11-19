package com.thinkfree.drive.application.file.converter;

import com.thinkfree.drive.application.file.command.FileUploadCommand;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceFindQuery;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.file.FileExtractResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.web.multipart.MultipartFile;

/**
 * Command → 도메인 Dto/Query 변환 전담 — 구 api/v1 FileRequestConverter의 도메인 방향 매핑을 그대로 인계.
 */
@Mapper(componentModel = "spring", imports = {ResourceType.class, MapStructUtil.class, ResourceSaveType.class})
public interface FileCommandConverter {

    @Mapping(target = "userSeq", expression = "java(MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq))")
    @Mapping(target = "ownerSeq", source = "requestUserSeq")
    @Mapping(target = "resourceSeq", source = "command.resourceSeq")
    @Mapping(target = "parentFolderSeq", source = "command.parentFolderSeq")
    @Mapping(target = "resourceSaveType", expression = "java(command.saveType() == null ? ResourceSaveType.DUPLICATE : command.saveType())")
    FileUploadDto toUploadDto(FileUploadCommand command, FileExtractResult extracted, int requestUserSeq, boolean lockedResourceCheck);

    default FileUploadDto toUploadDto(FileUploadCommand command, int requestUserSeq) {
        FileExtractResult extracted = ResourceUtil.extract(command.file());
        return toUploadDto(command, extracted, requestUserSeq, true);
    }

    @Mapping(target = "resourceType", expression = "java(ResourceType.FILE)")
    ResourceFindQuery toFindQuery(Long resourceSeq, int userSeq, Boolean isDelete);

    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "ownerSeq", source = "requestUserSeq")
    @Mapping(target = "resourceSaveType", expression = "java(ResourceSaveType.DUPLICATE)")
    FileUploadDto toCopyUploadDto(Long parentFolderSeq, Integer userSeq, int requestUserSeq, FileExtractResult extracted, String contentType, long fileSize);

    default FileUploadDto toCopyUploadDto(Long parentFolderSeq, Integer userSeq, int requestUserSeq, String fileName, String ext, String contentType, long fileSize) {
        FileExtractResult extracted = ResourceUtil.extract(fileName, ext);
        return toCopyUploadDto(parentFolderSeq, userSeq, requestUserSeq, extracted, contentType, fileSize);
    }

    // drive_upload_file용 — 복제와 같은 DUPLICATE 메타에 내용(file)을 함께 싣는다. 중복 넘버링은 업로드 세션 커밋(UploadCommitService → saveUploadedFile)이 처리
    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "ownerSeq", source = "requestUserSeq")
    @Mapping(target = "file", source = "file")
    @Mapping(target = "resourceSaveType", expression = "java(ResourceSaveType.DUPLICATE)")
    FileUploadDto toContentUploadDto(Long parentFolderSeq, Integer userSeq, int requestUserSeq, FileExtractResult extracted, String contentType, long fileSize, MultipartFile file);

    default FileUploadDto toContentUploadDto(Long parentFolderSeq, Integer userSeq, int requestUserSeq, String fileName, String contentType, MultipartFile file) {
        FileExtractResult extracted = ResourceUtil.extract(fileName);
        return toContentUploadDto(parentFolderSeq, userSeq, requestUserSeq, extracted, contentType, file.getSize(), file);
    }

}

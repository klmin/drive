package com.thinkfree.drive.api.v1.file.converter;

import com.thinkfree.drive.api.v1.file.request.FileCopyRequest;
import com.thinkfree.drive.api.v1.file.request.FileDownloadRequest;
import com.thinkfree.drive.api.v1.file.request.FileMoveRequest;
import com.thinkfree.drive.api.v1.file.request.FileRenameRequest;
import com.thinkfree.drive.api.v1.file.request.FileUploadRequest;
import com.thinkfree.drive.api.v1.file.request.FileViewRequest;
import com.thinkfree.drive.application.file.command.FileCopyCommand;
import com.thinkfree.drive.application.file.command.FileDownloadCommand;
import com.thinkfree.drive.application.resource.command.ResourceMoveCommand;
import com.thinkfree.drive.application.resource.command.ResourceRenameCommand;
import com.thinkfree.drive.application.file.command.FileUploadCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 Request → application Command 변환 전담. 도메인 방향 매핑은 application의 FileCommandConverter로 이관됨.
 */
@Mapper(componentModel = "spring")
public interface FileRequestConverter {

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    @Mapping(target = "resourceSeq", source = "rs")
    @Mapping(target = "saveType", source = "st")
    FileUploadCommand toUploadCommand(FileUploadRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "newResourceName", source = "rn")
    @Mapping(target = "nameConflictPolicy", source = "ncp")
    ResourceRenameCommand toRenameCommand(FileRenameRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "targetUserSeq", source = "ts")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    ResourceMoveCommand toMoveCommand(FileMoveRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "targetUserSeq", source = "ts")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    FileCopyCommand toCopyCommand(FileCopyRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    FileDownloadCommand toDownloadCommand(FileDownloadRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    FileDownloadCommand toDownloadCommand(FileViewRequest request);

}

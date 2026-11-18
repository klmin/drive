package com.thinkfree.drive.api.v1.folder.converter;

import com.thinkfree.drive.api.v1.folder.request.FolderDownloadRequest;
import com.thinkfree.drive.api.v1.folder.request.FolderMoveRequest;
import com.thinkfree.drive.api.v1.folder.request.FolderRenameRequest;
import com.thinkfree.drive.api.v1.folder.request.FolderUploadRequest;
import com.thinkfree.drive.application.folder.command.FolderCreateCommand;
import com.thinkfree.drive.application.folder.command.FolderDownloadCommand;
import com.thinkfree.drive.application.resource.command.ResourceMoveCommand;
import com.thinkfree.drive.application.resource.command.ResourceRenameCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 Request → application Command 변환 전담. 도메인 방향 매핑은 application의 FolderCommandConverter로 이관됨.
 */
@Mapper(componentModel = "spring")
public interface FolderRequestConverter {

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    @Mapping(target = "folderName", source = "fn")
    FolderCreateCommand toCreateCommand(FolderUploadRequest request);

    // 폴더 요청에는 ncp 필드가 없다(웹 시절 동일) — null 유지로 폴더 rename 경로에 정책 개념이 생기지 않게 한다
    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "newResourceName", source = "rn")
    @Mapping(target = "nameConflictPolicy", ignore = true)
    ResourceRenameCommand toRenameCommand(FolderRenameRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "targetUserSeq", source = "ts")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    ResourceMoveCommand toMoveCommand(FolderMoveRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    FolderDownloadCommand toDownloadCommand(FolderDownloadRequest request);

}

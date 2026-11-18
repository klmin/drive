package com.thinkfree.drive.application.document.converter;

import com.thinkfree.drive.application.document.command.DocumentCreateCommand;
import com.thinkfree.drive.document.dto.DocumentCreateDto;
import com.thinkfree.drive.util.MapStructUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Command → 도메인 Dto 변환 전담 — 구 api/v1 DocumentRequestConverter의 도메인 방향 매핑을 그대로 인계.
 */
@Mapper(componentModel = "spring", imports = MapStructUtil.class)
public interface DocumentCommandConverter {

    @Mapping(target = "userSeq", expression = "java(MapStructUtil.resolveUserSeq(command.targetUserSeq(), requestUserSeq))")
    @Mapping(target = "ownerSeq", source = "requestUserSeq")
    @Mapping(target = "parentFolderSeq", source = "command.parentFolderSeq")
    @Mapping(target = "templateType", source = "command.templateType")
    DocumentCreateDto toCreateDto(DocumentCreateCommand command, int requestUserSeq);

}

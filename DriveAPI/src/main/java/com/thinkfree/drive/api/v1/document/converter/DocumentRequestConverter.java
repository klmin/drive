package com.thinkfree.drive.api.v1.document.converter;

import com.thinkfree.drive.api.v1.document.request.DocumentCreateRequest;
import com.thinkfree.drive.application.document.command.DocumentCreateCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentRequestConverter {

    @Mapping(target = "targetUserSeq", source = "ss")
    @Mapping(target = "parentFolderSeq", source = "pfs")
    @Mapping(target = "templateType", source = "tt")
    DocumentCreateCommand toCreateCommand(DocumentCreateRequest request);
}

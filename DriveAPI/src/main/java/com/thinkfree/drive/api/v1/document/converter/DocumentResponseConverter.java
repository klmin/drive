package com.thinkfree.drive.api.v1.document.converter;

import com.thinkfree.drive.api.v1.document.response.DocumentCreateResponse;
import com.thinkfree.drive.application.document.info.DocumentCreateInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentResponseConverter {

    @Mapping(target = "userSeq", source = "ownerUserSeq")
    DocumentCreateResponse toCreateResponse(DocumentCreateInfo info);

}

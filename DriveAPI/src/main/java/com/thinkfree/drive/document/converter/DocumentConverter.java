package com.thinkfree.drive.document.converter;

import com.thinkfree.drive.document.dto.DocumentCreateResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DocumentConverter {
    DocumentCreateResult toCreateResult(Long resourceSeq, Integer userSeq);
}

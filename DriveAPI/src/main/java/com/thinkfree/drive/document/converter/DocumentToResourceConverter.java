package com.thinkfree.drive.document.converter;

import com.thinkfree.drive.document.dto.DocumentCreateDto;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.util.file.FileExtractResult;
import com.thinkfree.drive.util.ResourceUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {ResourceSaveType.class})
public interface DocumentToResourceConverter {

    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "resourceSaveType", expression = "java(ResourceSaveType.DUPLICATE)")
    FileUploadDto toFileUploadDto(DocumentCreateDto dto, FileExtractResult extracted, String contentType, long fileSize);
    
    default FileUploadDto toFileUploadDto(DocumentCreateDto dto, String fileName, String contentType, long fileSize) {
        FileExtractResult extracted = ResourceUtil.extract(fileName);
        return toFileUploadDto(dto, extracted, contentType, fileSize);
    }
}

package com.thinkfree.drive.resource.converter;

import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = ResourceSaveType.class)
public interface WebOfficeToResourceConverter {
    
    @Mapping(target = "resourceSaveType", expression = "java(ResourceSaveType.VERSION)")
    @Mapping(target = "updateUserSeq", source = "versionRegisterUserSeq")
    @Mapping(target = "versionRegisterUserSeq", source = "versionRegisterUserSeq")
    @Mapping(target = "ownerSeq", source = "versionRegisterUserSeq")
    FileUploadDto toFileUploadDto(Long resourceSeq, int userSeq, Long fileSize, int versionRegisterUserSeq);
}

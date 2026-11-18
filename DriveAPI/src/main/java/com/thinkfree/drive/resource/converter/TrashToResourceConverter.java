package com.thinkfree.drive.resource.converter;

import com.thinkfree.drive.resource.dto.ResourceRestoreDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TrashToResourceConverter {
    
    @Mapping(target = "parentExists", source = "pe")
    ResourceRestoreDto toRestoreDto(Long resourceSeq, int userSeq, ResourceType resourceType, boolean pe);
}

package com.thinkfree.drive.trash.converter;

import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.trash.dto.TrashSaveDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ResourceToTrashConverter {
    
    TrashSaveDto toSaveDto(ResourceDetailData resource);
}

package com.thinkfree.drive.trash.converter;

import com.thinkfree.drive.trash.dto.TrashSaveDto;
import com.thinkfree.drive.trash.entity.Trash;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TrashConverter {
    
    Trash toSaveTrash(TrashSaveDto dto);
}

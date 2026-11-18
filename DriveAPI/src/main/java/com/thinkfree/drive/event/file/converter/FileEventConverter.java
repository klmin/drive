package com.thinkfree.drive.event.file.converter;

import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessQuotaChangeDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileEventConverter {
    
    PostProcessQuotaChangeDto toPostProcessQuotaChangeDto(FileUploadedEvent event);
}

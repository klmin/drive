package com.thinkfree.drive.application.file.command;

import com.thinkfree.drive.resource.enums.ResourceSaveType;
import org.springframework.web.multipart.MultipartFile;

public record FileUploadCommand
(
    Integer sharedByUserSeq,
    Long parentFolderSeq,
    Long resourceSeq,
    MultipartFile file,
    ResourceSaveType saveType
)
{
}

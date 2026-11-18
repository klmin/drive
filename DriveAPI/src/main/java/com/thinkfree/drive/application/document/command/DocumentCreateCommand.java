package com.thinkfree.drive.application.document.command;

import com.thinkfree.drive.document.enums.DocumentTemplateType;
import lombok.Builder;

@Builder
public record DocumentCreateCommand
(
    Integer targetUserSeq,
    Long parentFolderSeq,
    DocumentTemplateType templateType
)
{
}

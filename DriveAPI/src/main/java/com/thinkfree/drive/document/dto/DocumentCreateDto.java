package com.thinkfree.drive.document.dto;

import com.thinkfree.drive.document.enums.DocumentTemplateType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DocumentCreateDto {
    private Integer userSeq;
    private Integer ownerSeq;
    private Long parentFolderSeq;
    private DocumentTemplateType templateType;
}

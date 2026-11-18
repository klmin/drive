package com.thinkfree.drive.document.dto;

import com.thinkfree.drive.document.enums.DocumentTemplateType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Builder
@Getter
public class DocumentDefaultCreateDto {
    
    private MultipartFile file;
    private DocumentTemplateType templateType;
}

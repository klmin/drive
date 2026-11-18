package com.thinkfree.drive.document.service;

import com.thinkfree.drive.document.dto.DocumentCreateDto;
import com.thinkfree.drive.document.dto.DocumentCreateResult;

public interface DocumentService {
    
    DocumentCreateResult create(DocumentCreateDto dto);
    
}

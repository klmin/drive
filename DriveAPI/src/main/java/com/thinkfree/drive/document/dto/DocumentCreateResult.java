package com.thinkfree.drive.document.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DocumentCreateResult {
    private Long resourceSeq;
    private Integer userSeq;
}

package com.thinkfree.drive.systemconfig.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileSizeLimitData {
    private Long maxFileUploadSize;
}

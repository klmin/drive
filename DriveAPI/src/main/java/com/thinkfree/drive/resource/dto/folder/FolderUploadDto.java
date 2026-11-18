package com.thinkfree.drive.resource.dto.folder;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FolderUploadDto {
    private Integer userSeq;
    private Integer ownerSeq;
    private Long parentFolderSeq;
    private String baseFolderName;
    private String originalFolderName;
    private int copyNumber;
    private boolean copyFolder;
    
}

package com.thinkfree.drive.pinnedfolder.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PinnedFolderSaveDto {
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
}

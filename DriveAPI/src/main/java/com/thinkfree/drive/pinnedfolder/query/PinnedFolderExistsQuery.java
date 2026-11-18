package com.thinkfree.drive.pinnedfolder.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PinnedFolderExistsQuery {
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
}

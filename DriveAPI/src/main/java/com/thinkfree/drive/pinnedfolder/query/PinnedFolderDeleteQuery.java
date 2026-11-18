package com.thinkfree.drive.pinnedfolder.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PinnedFolderDeleteQuery {
    private Integer userSeq;
    private Long pinnedFolderSeq;
}
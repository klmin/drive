package com.thinkfree.drive.trash.query;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrashDeleteQuery {

    private Long resourceSeq;
    private Integer userSeq;
    
}

package com.thinkfree.drive.starred.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class StarredDeleteQuery {

    private Long starredSeq;
    private Integer userSeq;
    
}

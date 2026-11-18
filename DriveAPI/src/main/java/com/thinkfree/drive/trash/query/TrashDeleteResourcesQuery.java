package com.thinkfree.drive.trash.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TrashDeleteResourcesQuery {

    private Integer userSeq;

    private List<Long> resourceSeqList;

}

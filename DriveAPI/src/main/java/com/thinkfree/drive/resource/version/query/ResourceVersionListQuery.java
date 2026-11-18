package com.thinkfree.drive.resource.version.query;

import com.thinkfree.drive.api.page.query.PaginationQuery;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ResourceVersionListQuery {
    
    private Integer userSeq;
    private Long resourceSeq;
    private Boolean isDelete;
    private ResourceVersionStatus resourceVersionStatus;
    private PaginationQuery page;
    private List<Long> resourceSeqList;

}

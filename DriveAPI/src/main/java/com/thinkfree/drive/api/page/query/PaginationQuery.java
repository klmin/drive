package com.thinkfree.drive.api.page.query;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaginationQuery {
    
    private Integer pageIndex;
    private Integer pageSize;
    
    public int getOffset() {
        return (pageIndex - 1) * pageSize;
    }
    
    public boolean isPaging() {
        return pageIndex != null && pageSize != null;
    }
    
    public static PaginationQuery of(Integer pageIndex, Integer pageSize) {
        return new PaginationQuery(pageIndex, pageSize);
    }
    
}

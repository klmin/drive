package com.thinkfree.drive.api.page;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiPage {
    
    private Long totalCount;
    private Integer pageIndex;
    private Integer pageSize;
    
    public static ApiPage of(long totalCount, Integer pageIndex, Integer pageSize) {
        return new ApiPage(totalCount, pageIndex, pageSize);
    }
}

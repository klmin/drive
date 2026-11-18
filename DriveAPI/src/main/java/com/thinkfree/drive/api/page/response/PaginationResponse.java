package com.thinkfree.drive.api.page.response;

import com.thinkfree.drive.api.page.ApiPage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PaginationResponse<T> {
    private List<T> list;
    private ApiPage page;
    
    public static <T> PaginationResponse<T> of(List<T> list, ApiPage page){
        return PaginationResponse.<T>builder()
                                 .list(list)
                                 .page(page)
                                 .build();
    }
}

package com.thinkfree.drive.api.page.response;

import com.thinkfree.drive.api.page.CursorApiPage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CursorPaginationResponse<T> {
    private List<T> list;
    private CursorApiPage<?> page;

    public static <T> CursorPaginationResponse<T> of(List<T> list, CursorApiPage<?> page){
        return CursorPaginationResponse.<T>builder()
                .list(list)
                .page(page)
                .build();
    }
}

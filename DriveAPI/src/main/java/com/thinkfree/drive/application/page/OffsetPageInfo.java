package com.thinkfree.drive.application.page;

import java.util.List;

/**
 * 오프셋(pi/ps) 페이지네이션 결과의 application 계층 표현 — 웹 어댑터는 ApiPage/PaginationResponse로,
 * MCP 어댑터는 툴 응답으로 복원한다 (CursorPageInfo의 오프셋 등가물).
 */
public record OffsetPageInfo<T>
(
    List<T> list,
    long totalCount,
    int pageIndex,
    int pageSize
)
{
    public static <T> OffsetPageInfo<T> of(List<T> list, long totalCount, int pageIndex, int pageSize) {
        return new OffsetPageInfo<>(list, totalCount, pageIndex, pageSize);
    }
}

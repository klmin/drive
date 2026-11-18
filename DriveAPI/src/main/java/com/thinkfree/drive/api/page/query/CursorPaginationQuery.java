package com.thinkfree.drive.api.page.query;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CursorPaginationQuery {

    private Object cursorValue; // 정렬 필드 커서 값
    private Long cursorId;      // Tie-breaker (cursorValue 기준으로 같을 때, 추가 정렬 조건)
    private ResourceType cursorType;
    private Integer pageSize;

    public static CursorPaginationQuery of(Object cursorValue, Long cursorId, ResourceType cursorType, Integer pageSize) {
        return new CursorPaginationQuery(cursorValue, cursorId, cursorType, pageSize);
    }
}


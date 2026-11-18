package com.thinkfree.drive.common.search.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SortOrder {
    ASC("오름차순"),
    DESC("내림차순")
    ;
    
    private final String description;
        
    @JsonCreator
    public static SortOrder from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }

    /**
     * 정렬 필드만 오고 방향이 없으면 ASC. 목록 리포는 정렬과 커서 조건을 sortBy·sortOrder 가 둘 다 있을 때만 맞춰 적용하므로,
     * 방향이 빠지면 ORDER BY 와 커서 비교 기준이 어긋나 다음 페이지가 끝나지 않는다.
     */
    public static SortOrder withSortBy(Object sortBy, SortOrder sortOrder) {
        return sortBy != null && sortOrder == null ? ASC : sortOrder;
    }
}

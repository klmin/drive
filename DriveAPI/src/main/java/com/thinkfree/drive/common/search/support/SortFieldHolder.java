package com.thinkfree.drive.common.search.support;

import com.thinkfree.drive.resource.enums.ResourceType;

import java.time.Instant;

public interface SortFieldHolder {

    String getResourceName();

    Long getSizeByte();

    Instant getUpdateDate();

    Instant getRecentUpdateDate();

    ResourceType getResourceType();

    Instant getRegisterDate();

    Instant getShareDate();

    /** SIZE 정렬 커서 값 — 폴더(크기 없음)는 0. 커서 게이트가 {@code COALESCE(size_byte, 0)} 로 비교하므로, null 을 내보내면 다음 요청에서 커서가 빠져 첫 페이지가 반복된다. */
    default Long getSizeCursorValue() {
        Long size = getSizeByte();
        return size == null ? 0L : size;
    }

}

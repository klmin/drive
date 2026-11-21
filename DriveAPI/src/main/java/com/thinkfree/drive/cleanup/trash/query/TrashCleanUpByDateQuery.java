package com.thinkfree.drive.cleanup.trash.query;

import java.time.Instant;
import java.util.List;

/**
 * 스케줄러 휴지통 자동정리 대상 조건.
 *
 * @param userSeqs null 이 아니면 이 사용자들의 휴지통 루트만 — 자동정리가 먼저 잠근 사용자 목록. 잠근 뒤 새로 기한이 지난 다른 사용자 항목은
 *                 잠그지 않았으므로 이번 실행에서 지우지 않는다(다음 실행에서 지운다)
 */
public record TrashCleanUpByDateQuery(
        Instant thresholdDate,
        Boolean isDelete,
        List<Integer> userSeqs
) {
    /** 등록일 기준 전부(사용자 제한 없음). */
    public TrashCleanUpByDateQuery(Instant thresholdDate, Boolean isDelete) {
        this(thresholdDate, isDelete, null);
    }
}

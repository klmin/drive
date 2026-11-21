package com.thinkfree.drive.cleanup.trash.query;

import java.time.Instant;

/**
 * 사용자 휴지통 영구삭제 대상 조건.
 *
 * @param registeredBefore null 이 아니면 이 시각까지 휴지통에 들어온 루트만 — 휴지통 비우기를 나눠 처리하는 동안 새로 들어온 항목은 지우지 않는다
 * @param rootLimit        null 이 아니면 휴지통 루트를 trash_seq 순으로 이만큼만(하위는 루트를 따라 전부) — 한 트랜잭션 크기 제한
 */
public record TrashCleanUpByUserQuery(
        Integer userSeq,
        Boolean isDelete,
        Instant registeredBefore,
        Integer rootLimit
) {
    /** 사용자 휴지통 루트 전부(시각·개수 제한 없음). */
    public TrashCleanUpByUserQuery(Integer userSeq, Boolean isDelete) {
        this(userSeq, isDelete, null, null);
    }
}

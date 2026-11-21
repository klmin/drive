package com.thinkfree.drive.cleanup.trash.query;

import java.util.List;

/**
 * 사용자 휴지통 영구삭제 대상 조건.
 *
 * @param trashSeqs null 이 아니면 이 휴지통 행(trash_seq)의 루트만(하위는 루트를 따라 전부) — 휴지통 비우기를 나눠 처리할 때
 *                  요청 시작 시 고정한 대상 목록의 한 묶음. 처리하는 동안 새로 버린 항목은 목록에 없어 지워지지 않는다
 */
public record TrashCleanUpByUserQuery(
        Integer userSeq,
        Boolean isDelete,
        List<Long> trashSeqs
) {
    /** 사용자 휴지통 루트 전부. */
    public TrashCleanUpByUserQuery(Integer userSeq, Boolean isDelete) {
        this(userSeq, isDelete, null);
    }
}

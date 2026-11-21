package com.thinkfree.drive.cleanup.trash.service;

import java.util.List;

public interface TrashCleanUpService {

    void cleanUpResourcesByDate();

    /** 사용자 휴지통 전부를 한 트랜잭션에서 영구삭제. */
    void cleanUpResourcesByUser(Integer userSeq);

    /** 휴지통 비우기 대상 고정 — 지금 사용자 휴지통에 있는 행(trash_seq) 전부, trash_seq 순. */
    List<Long> findTrashSeqsByUser(Integer userSeq);

    /**
     * 사용자 휴지통의 일부(휴지통 행 {@code trashSeqs} 의 루트와 그 하위)를 한 트랜잭션에서 영구삭제 — 휴지통 비우기를 나눠 처리할 때 한 묶음.
     * 그사이 복원·삭제되어 휴지통에 없는 행은 건너뛴다.
     *
     * @return 삭제한 리소스 수(루트 + 하위)
     */
    int cleanUpResourcesByUser(Integer userSeq, List<Long> trashSeqs);

}

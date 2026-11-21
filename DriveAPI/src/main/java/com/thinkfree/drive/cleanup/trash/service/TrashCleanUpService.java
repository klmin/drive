package com.thinkfree.drive.cleanup.trash.service;

import java.time.Instant;

public interface TrashCleanUpService {

    void cleanUpResourcesByDate();

    /** 사용자 휴지통 전부를 한 트랜잭션에서 영구삭제. */
    void cleanUpResourcesByUser(Integer userSeq);

    /**
     * 사용자 휴지통의 일부(루트 {@code rootLimit} 개와 그 하위)를 한 트랜잭션에서 영구삭제 — 휴지통 비우기를 나눠 처리할 때 한 번분.
     *
     * @param registeredBefore 이 시각까지 휴지통에 들어온 루트만
     * @return 삭제한 리소스 수(루트 + 하위). 0 이면 남은 대상 없음
     */
    int cleanUpResourcesByUser(Integer userSeq, Instant registeredBefore, int rootLimit);

}

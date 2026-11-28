package com.thinkfree.drive.auth.token.blacklist.store;

import java.time.Instant;

/**
 * JWT 블랙리스트의 빠른 조회 저장소 — DB(tb_blacklist_token)가 원본이고 이것은 사본이다(docs/design/scaling-strategy.md §3-2).
 * <ul>
 *   <li>Redis 꺼짐: {@link NoopBlacklistStore} — 항상 "모름", 매 요청 DB 조회(예전과 같다)</li>
 *   <li>Redis 켜짐: {@link RedisBlacklistStore} — 사본이 온전할 때만 답하고, 아니면 "모름" 으로 DB 에 넘긴다</li>
 * </ul>
 */
public interface BlacklistStore {

    /**
     * @return 블랙리스트면 TRUE, 아니면 FALSE, <b>확실하지 않으면 null</b>(호출부가 DB 로 확인). 예외를 던지지 않는다
     */
    Boolean contains(String tokenHash);

    /** 로그아웃 기록 — DB 저장이 커밋된 뒤 사본에도 넣는다. 실패해도 예외를 던지지 않는다 */
    void add(String tokenHash, Instant expiresAt);
}

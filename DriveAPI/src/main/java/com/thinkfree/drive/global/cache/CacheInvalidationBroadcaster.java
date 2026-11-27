package com.thinkfree.drive.global.cache;

/**
 * 다른 WAS 에 "이 캐시 항목을 비워라" 를 알린다 — docs/design/scaling-strategy.md §3-1.
 *
 * <p>각 로컬 캐시는 자기 항목을 먼저 지운 뒤 이것을 부른다. 구현은 트랜잭션 안이면 <b>커밋 뒤에</b> 보낸다 — 커밋 전에 보내면
 * 다른 WAS 가 아직 옛 값을 읽어 다시 담는다. 롤백이면 보내지 않는다(내 WAS 는 이미 지웠고, 값은 그대로라 다시 읽어도 같다).
 * 보내기 실패는 예외로 올리지 않는다 — 다른 WAS 는 TTL 안에 반영된다.
 * <ul>
 *   <li>Redis 꺼짐: {@link NoopCacheInvalidationBroadcaster} — 아무것도 안 함(TTL 로만 반영, 예전과 같다)</li>
 *   <li>Redis 켜짐: {@link RedisCacheInvalidationBroadcaster} — 채널 하나에 PUBLISH, 모든 WAS 가 구독해 해당 캐시를 비운다</li>
 * </ul>
 */
public interface CacheInvalidationBroadcaster {

    /** 전체 비우기를 나타내는 키 */
    String ALL = "*";

    /**
     * @param cacheName {@link LocallyInvalidatable#cacheName()}
     * @param key       비울 항목 키(문자열). 전체면 {@link #ALL}
     */
    void broadcast(String cacheName, String key);
}

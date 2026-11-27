package com.thinkfree.drive.global.cache;

/**
 * 다른 WAS 에서 온 무효화 메시지로 비울 수 있는 로컬 캐시. 구독자가 {@link #cacheName()} 으로 찾아 {@link #evictLocal(String)} 을 부른다.
 * 여기서는 다시 방송하지 않는다(메시지가 돌고 돌지 않게).
 */
public interface LocallyInvalidatable {

    /** 메시지에 실리는 캐시 이름 — WAS 사이에 같아야 하고 ':' 를 쓰지 않는다 */
    String cacheName();

    /** @param key {@link CacheInvalidationBroadcaster#broadcast} 의 key, 전체면 {@link CacheInvalidationBroadcaster#ALL} */
    void evictLocal(String key);
}

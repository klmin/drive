package com.thinkfree.drive.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 다른 WAS 가 보낸 무효화 메시지를 받아 해당 로컬 캐시를 비운다. 자기가 보낸 메시지는 건너뛴다.
 * 모르는 캐시 이름·형식이 틀린 메시지는 로그만 남긴다(버전이 다른 WAS 가 섞인 롤링 배포 중일 수 있다).
 */
@Slf4j
public class RedisCacheInvalidationListener implements MessageListener {

    private final Map<String, LocallyInvalidatable> caches;
    private final String selfOrigin;

    public RedisCacheInvalidationListener(List<LocallyInvalidatable> caches, String selfOrigin) {
        this.selfOrigin = selfOrigin;
        this.caches = caches.stream().collect(Collectors.toMap(LocallyInvalidatable::cacheName, Function.identity()));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        handle(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    void handle(String payload) {
        String[] parts = payload.split(":", 3);
        if (parts.length != 3) {
            log.warn("[CacheInvalidation] 형식이 틀린 메시지 무시: {}", payload);
            return;
        }
        if (selfOrigin.equals(parts[0])) {
            return;
        }
        LocallyInvalidatable cache = caches.get(parts[1]);
        if (cache == null) {
            log.warn("[CacheInvalidation] 모르는 캐시 이름 무시: {}", parts[1]);
            return;
        }
        try {
            cache.evictLocal(parts[2]);
        } catch (Exception e) {
            log.warn("[CacheInvalidation] 로컬 삭제 실패 — TTL 안에 반영된다: {}", payload, e);
        }
    }
}

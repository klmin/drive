package com.thinkfree.drive.auth.token.blacklist.store;

import com.thinkfree.drive.global.redis.ConditionalOnDriveRedis;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 DB 의 미만료 블랙리스트를 Redis 사본에 다시 싣는다 — 로그아웃 때 SET 과 표시 키 삭제가 모두 실패한 경우,
 * 영속화 설정(RDB)으로 표시 키는 살아남았는데 그 뒤 키가 유실된 경우를 1분 안에 메운다. 기동 직후 첫 적재도 이것이 한다.
 * Redis 는 공유라 WAS 중 하나만 돌면 된다(ShedLock). 배치 스케줄러 스위치({@code drive.scheduler.batch.enabled})와 무관하게 돈다 —
 * 블랙리스트 정합성은 보안 기능이라 배치 담당 파드 구성에 따라 꺼지면 안 된다.
 * 항목 수는 access 토큰 수명(1시간) 안의 로그아웃 수라 작다.
 */
@Component
@ConditionalOnDriveRedis
@RequiredArgsConstructor
public class BlacklistRedisSyncJob {

    static final String LOCK_NAME = "blacklistRedisSync";

    private final RedisBlacklistStore store;

    @Scheduled(initialDelay = 5_000, fixedDelay = 60_000)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "50s", lockAtLeastFor = "20s")
    public void sync() {
        store.reload();
    }
}

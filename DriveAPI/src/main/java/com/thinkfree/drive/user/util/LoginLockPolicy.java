package com.thinkfree.drive.user.util;

import java.time.Duration;
import java.time.Instant;

/**
 * 사용자 로그인 화면의 실패 잠금 규칙 — docs/design/scaling-strategy.md §3-5.
 * <ul>
 *   <li>연속 {@value #MAX_FAILURES}회 실패하면 잠긴다.</li>
 *   <li>마지막 실패(시도) 뒤 {@link #LOCK_DURATION} 이 지나면 잠금이 풀리고 다음 시도부터 1회로 다시 센다(스케줄러 없음).
 *       예전에는 비밀번호를 바꿀 때만 풀려, 아이디만 알면 계정을 계속 잠가 둘 수 있었다.</li>
 *   <li>{@code login_failed_at} 이 NULL 이면 오래전 실패로 본다(마이그레이션 V10 이 배포 시점 잠긴 계정에 현재 시각을 넣는다).</li>
 * </ul>
 * 같은 규칙을 SQL({@code DefaultUserRepository#countLoginAttempt})과 자바({@link #isLocked})가 함께 쓴다.
 */
public final class LoginLockPolicy {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private LoginLockPolicy() {
    }

    /** 이 시각 이전의 실패는 만료(잠금 해제·1부터 다시 셈) */
    public static Instant windowStart(Instant now) {
        return now.minus(LOCK_DURATION);
    }

    public static boolean isExpired(Instant loginFailedAt, Instant now) {
        return loginFailedAt == null || loginFailedAt.isBefore(windowStart(now));
    }

    public static boolean isLocked(Integer loginFailureCount, Instant loginFailedAt, Instant now) {
        return loginFailureCount != null && loginFailureCount >= MAX_FAILURES && !isExpired(loginFailedAt, now);
    }

    /** 읽어 둔 값으로 이번 시도를 셌을 때의 횟수(만료됐으면 1) — 원자적 카운트를 쓸 수 없을 때만 */
    public static int nextCount(Integer loginFailureCount, Instant loginFailedAt, Instant now) {
        if (isExpired(loginFailedAt, now) || loginFailureCount == null) {
            return 1;
        }
        return loginFailureCount + 1;
    }
}

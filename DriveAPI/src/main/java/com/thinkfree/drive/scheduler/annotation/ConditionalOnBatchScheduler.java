package com.thinkfree.drive.scheduler.annotation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 공유 DB 배치 스케줄러(ShedLock 대상) 빈을 {@code drive.scheduler.batch.enabled} 로 켜고 끈다.
 *
 * <p>
 * 쿠버네티스에서 일부 WAS(Deployment)만 배치를 돌릴 때 env {@code DRIVE_SCHEDULER_BATCH_ENABLED} 로 지정한다.
 * 빈 자체가 등록되지 않으므로 꺼진 WAS 는 트리거·ShedLock 락 시도도 하지 않는다.
 * 프로퍼티가 없으면 켜진다 — 설정 누락으로 정리·라이선스 작업이 조용히 멈추는 것을 막기 위함.
 *
 * <p>
 * 인스턴스별 캐시 갱신 작업(예: {@code OAuth2KeyManager} JWK 리프레시)에는 붙이지 않는다 — 모든 WAS 에서 돌아야 한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = "drive.scheduler.batch.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnBatchScheduler {
}

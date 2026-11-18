package com.thinkfree.drive.oauth2.scheduler;

import com.thinkfree.drive.scheduler.annotation.ConditionalOnBatchScheduler;
import com.thinkfree.drive.scheduler.constants.SchedulerConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.thinkfree.drive.jooq.Tables.OAUTH2_AUTHORIZATION;

/**
 * oauth2_authorization 만료 행 청소 스케줄러.
 *
 * SAS(JdbcOAuth2AuthorizationService)는 만료 행을 절대 삭제하지 않아 인가 건마다 행이
 * 영구 누적된다 — authorize 방치 건(코드 미교환) + refresh(30d) 만료 건이 대상.
 * SAS 서비스에 벌크 삭제 API가 없어 jOOQ 로 직접 DELETE 한다 (공식 스키마로 생성한 jOOQ 테이블 — pom jooq-codegen includes).
 *
 * 삭제 기준 — 행에 존재하는 가장 수명 긴 토큰이 만료된 행:
 *   ① refresh token 보유 행: refresh 만료 시 (access·code는 항상 그보다 먼저 만료)
 *   ② refresh 없이 access만 있는 행: access 만료 시
 *   ③ 코드만 발급되고 미교환된 행: 코드 만료 시
 * 동의 단계에서 방치된 행(state만 있고 토큰·만료 컬럼 전부 NULL)은 나이를 판별할
 * 타임스탬프 컬럼이 없어 건드리지 않는다 — 진행 중인 동의 플로우를 죽일 수 있음.
 *
 * ShedLock 필수 — 공유 DB 정리라 다중 WAS 동시 실행을 막아야 한다
 * (JWK 주기 리프레시는 인스턴스별 캐시 갱신이라 전원 실행이 맞는 것과 대비).
 */
@ConditionalOnBatchScheduler
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthorizationCleanupScheduler {

    private final DSLContext dsl;

    @Scheduled(cron = SchedulerConstant.CRON_CLEANUP_OAUTH2_AUTHORIZATIONS)
    @SchedulerLock(name = SchedulerConstant.LOCK_CLEANUP_OAUTH2_AUTHORIZATIONS)
    public void cleanupExpiredAuthorizations() {
        Instant now = Instant.now();
        int deleted = dsl.deleteFrom(OAUTH2_AUTHORIZATION)
                .where(OAUTH2_AUTHORIZATION.REFRESH_TOKEN_EXPIRES_AT.isNotNull()
                        .and(OAUTH2_AUTHORIZATION.REFRESH_TOKEN_EXPIRES_AT.lt(now)))
                .or(OAUTH2_AUTHORIZATION.REFRESH_TOKEN_EXPIRES_AT.isNull()
                        .and(OAUTH2_AUTHORIZATION.ACCESS_TOKEN_EXPIRES_AT.isNotNull())
                        .and(OAUTH2_AUTHORIZATION.ACCESS_TOKEN_EXPIRES_AT.lt(now)))
                .or(OAUTH2_AUTHORIZATION.REFRESH_TOKEN_EXPIRES_AT.isNull()
                        .and(OAUTH2_AUTHORIZATION.ACCESS_TOKEN_EXPIRES_AT.isNull())
                        .and(OAUTH2_AUTHORIZATION.AUTHORIZATION_CODE_EXPIRES_AT.isNotNull())
                        .and(OAUTH2_AUTHORIZATION.AUTHORIZATION_CODE_EXPIRES_AT.lt(now)))
                .execute();
        if (deleted > 0) {
            log.info("[OAuth2AuthorizationCleanupScheduler cleanupExpiredAuthorizations count : {}]", deleted);
        }
    }
}

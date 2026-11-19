package com.thinkfree.drive.upload.session.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * 업로드 세션 설정값(docs/design/upload-session-redesign.md §2) — 기본값이 설계 확정값이고 application.yml 의 drive.upload.* 로 조정한다.
 *
 * @param sessionTtl                PENDING 세션 TTL — last_activity 기준 슬라이딩(이 안에 돌아오면 이어 올리기 가능)
 * @param completingTimeout         COMPLETING 최대 시간 — 넘으면 스케줄러가 FAILED(COMMIT_ERROR) + ABORT·DELETE 등록
 * @param finishedRetention         끝난(COMPLETED·FAILED) 세션 행 보관 기간 — COMPLETED 는 complete 재요청 멱등 응답용
 * @param replacedKeyDeleteDelay    교체·밀려난 이전 key 삭제 유예
 * @param minPartSize               최소 파트 크기 — 파트 크기 = max(이 값, ceil(size / 10,000))
 * @param singleRequestThreshold    이 크기 미만은 단일 요청(0바이트 포함)
 * @param absoluteMaxSize           절대 상한(S3 객체 5TB) — 테넌트 최대 업로드 크기 정책은 이 안에서
 * @param maxConcurrentPartsPerSession 세션당 동시 파트(프론트 안내용)
 * @param maxActiveSessionsPerUser  사용자당 진행 중 세션 수 상한
 * @param cleanupBatchSize          세션 정리 스케줄러가 한 트랜잭션에서 처리할 행 수
 * @param manageBucketLifecycle     기동 시 버킷 lifecycle 에 미완성 멀티파트 정리 규칙을 보장할지(권한이 없는 설치처는 false — 운영자가 직접 설정)
 * @param abortIncompleteMultipartDays 버킷 lifecycle AbortIncompleteMultipartUpload 일수 — 최종 안전망, 세션 TTL 보다 길게
 */
@ConfigurationProperties("drive.upload")
public record UploadSessionProperty
(
    @DefaultValue("1h")
    Duration sessionTtl,

    @DefaultValue("30m")
    Duration completingTimeout,

    @DefaultValue("7d")
    Duration finishedRetention,

    @DefaultValue("24h")
    Duration replacedKeyDeleteDelay,

    @DefaultValue("16MB")
    DataSize minPartSize,

    @DefaultValue("32MB")
    DataSize singleRequestThreshold,

    @DefaultValue("5TB")
    DataSize absoluteMaxSize,

    @DefaultValue("4")
    int maxConcurrentPartsPerSession,

    @DefaultValue("50")
    int maxActiveSessionsPerUser,

    @DefaultValue("500")
    int cleanupBatchSize,

    @DefaultValue("true")
    boolean manageBucketLifecycle,

    @DefaultValue("1")
    int abortIncompleteMultipartDays
)
{
}

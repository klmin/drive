package com.thinkfree.drive.storage.config;

import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortIncompleteMultipartUpload;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;

/**
 * 기동 시 버킷 존재와 미완성 멀티파트 정리 lifecycle 규칙을 보장한다.
 * <p>
 * 기존 Ceph 전용 초기화는 {@code createBucket}만 호출했는데, Ceph RGW는 이미 있는 버킷에도 200을 주지만
 * RustFS(MinIO 계열)와 AWS S3는 409(BucketAlreadyOwnedByYou/BucketAlreadyExists)를 던진다.
 * 그래서 {@code headBucket}으로 먼저 확인하고 없을 때만 생성하며, 경합으로 409가 나도 무시한다.
 * <p>
 * lifecycle: 업로드 세션 정리(abort 등록)의 최종 안전망으로 {@code AbortIncompleteMultipartUpload} 규칙({@link #ABORT_RULE_ID})을 둔다.
 * PutBucketLifecycleConfiguration 은 전체 교체라 기존 규칙을 읽어 우리 규칙만 더한다(운영자가 넣은 규칙 유지). 지원하지 않거나 권한이 없으면 경고만 남긴다.
 * <ul>
 *   <li>운영자가 버킷 전체에 같거나 더 짧은 AbortIncompleteMultipartUpload 규칙을 이미 두었으면 우리 규칙을 더하지 않는다</li>
 *   <li>예전 형식(규칙 바로 아래 Prefix) 규칙은 같은 뜻의 Filter 형식으로 바꿔 다시 쓴다 — 두 형식이 섞이면 PUT 이 거절될 수 있다</li>
 *   <li>객체를 만료(삭제)하는 규칙이 있으면 경고만 한다 — Drive 객체에 걸리면 사용자 파일이 지워진다</li>
 * </ul>
 * 수동 설정 안내: DriveInstaller/docs/storage-bucket-lifecycle.md
 */
@ConditionalOnStorageType({StorageType.CEPH, StorageType.S3, StorageType.RUSTFS})
@Component
@RequiredArgsConstructor
@Slf4j
public class S3BucketInitializer {

    static final String ABORT_RULE_ID = "drive-abort-incomplete-multipart";

    private final S3Client s3Client;
    private final S3ConnectionProperty connection;
    private final StorageProperty storageProperty;
    private final UploadSessionProperty uploadSessionProperty;

    @PostConstruct
    public void init() {
        if (ensureBucket() && uploadSessionProperty.manageBucketLifecycle()) {
            ensureAbortIncompleteMultipartRule();
        }
    }

    /** @return 버킷이 있다고 확인됐거나 만들었으면 true */
    boolean ensureBucket() {
        String bucket = connection.bucketName();
        log.info("[Storage init] type: {}, bucket: {}, endpoint: {}, region: {}",
                 storageProperty.type(), bucket, connection.endpoint(), connection.region());

        try {
            s3Client.headBucket(req -> req.bucket(bucket));
            return true;
        } catch (NoSuchBucketException e) {
            log.info("[Storage init] bucket not found, creating: {}", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                // 권한 부족(403) 등은 생성 시도로 해결되지 않으므로 기동은 계속하고 첫 요청에서 드러나게 둔다.
                log.warn("[Storage init] headBucket failed, skip create. bucket: {}, status: {}", bucket, e.statusCode(), e);
                return false;
            }
        }

        try {
            s3Client.createBucket(req -> req.bucket(bucket));
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            log.info("[Storage init] bucket already exists: {}", bucket);
        }
        return true;
    }

    void ensureAbortIncompleteMultipartRule() {
        String bucket = connection.bucketName();
        int days = uploadSessionProperty.abortIncompleteMultipartDays();
        try {
            List<LifecycleRule> current = currentRules(bucket);
            warnObjectExpiration(bucket, current);

            boolean present = current.stream().anyMatch(r -> ABORT_RULE_ID.equals(r.id())
                    && r.abortIncompleteMultipartUpload() != null
                    && Integer.valueOf(days).equals(r.abortIncompleteMultipartUpload().daysAfterInitiation())
                    && r.status() == ExpirationStatus.ENABLED);
            if (present) {
                return;
            }
            // 운영자가 이미 버킷 전체에 같거나 더 짧은 미완성 멀티파트 정리 규칙을 두었으면 그 정책을 따른다(겹치는 규칙을 더하지 않음)
            var covering = current.stream()
                                  .filter(r -> !ABORT_RULE_ID.equals(r.id()) && r.status() == ExpirationStatus.ENABLED && coversWholeBucket(r))
                                  .filter(r -> r.abortIncompleteMultipartUpload() != null
                                          && r.abortIncompleteMultipartUpload().daysAfterInitiation() != null
                                          && r.abortIncompleteMultipartUpload().daysAfterInitiation() <= days)
                                  .findFirst();
            if (covering.isPresent()) {
                log.info("[Storage init] lifecycle: existing rule already aborts incomplete multipart uploads, keep it. bucket: {}, rule: {}, days: {}",
                         bucket, covering.get().id(), covering.get().abortIncompleteMultipartUpload().daysAfterInitiation());
                return;
            }

            List<LifecycleRule> rules = new ArrayList<>();
            for (LifecycleRule r : current) {
                if (!ABORT_RULE_ID.equals(r.id())) {
                    rules.add(withFilter(r));
                }
            }
            rules.add(LifecycleRule.builder()
                                   .id(ABORT_RULE_ID)
                                   .status(ExpirationStatus.ENABLED)
                                   .filter(LifecycleRuleFilter.builder().prefix("").build())
                                   .abortIncompleteMultipartUpload(AbortIncompleteMultipartUpload.builder().daysAfterInitiation(days).build())
                                   .build());
            s3Client.putBucketLifecycleConfiguration(req -> req.bucket(bucket).lifecycleConfiguration(c -> c.rules(rules)));
            log.info("[Storage init] lifecycle rule set. bucket: {}, rule: {}, days: {}", bucket, ABORT_RULE_ID, days);
        } catch (RuntimeException e) {
            // 스토리지가 lifecycle 을 지원하지 않거나 권한이 없음 — 세션 정리 스케줄러·삭제 워커가 1차 정리를 하므로 기동은 계속
            log.warn("[Storage init] lifecycle rule not set (configure AbortIncompleteMultipartUpload manually). bucket: {}, reason: {}",
                     bucket, e.getMessage());
        }
    }

    /**
     * 객체를 만료(삭제)하는 규칙이 켜져 있으면 경고한다 — Drive 객체 key 에 걸리면 사용자 파일이 지워진다(DB 행은 남아 다운로드 실패).
     * 고치지는 않는다: 다른 용도(다른 prefix)일 수 있어 운영자가 판단한다. 이전 버전 만료(Noncurrent*)·삭제 마커 정리는 현재 객체를 지우지 않아 제외.
     */
    private void warnObjectExpiration(String bucket, List<LifecycleRule> rules) {
        for (LifecycleRule r : rules) {
            if (r.status() == ExpirationStatus.ENABLED && r.expiration() != null
                    && (r.expiration().days() != null || r.expiration().date() != null)) {
                log.warn("[Storage init] lifecycle rule expires objects — if it matches Drive object keys, user files will be deleted. "
                                 + "bucket: {}, rule: {}, scope: {}, days: {}, date: {}",
                         bucket, r.id(), coversWholeBucket(r) ? "whole bucket" : describeFilter(r), r.expiration().days(), r.expiration().date());
            }
        }
    }

    /** 필터가 없거나 빈 prefix 뿐이면 버킷 전체 — 태그·크기·and 조건이 있으면 일부만 해당 */
    @SuppressWarnings("deprecation")
    static boolean coversWholeBucket(LifecycleRule rule) {
        LifecycleRuleFilter f = rule.filter();
        if (f == null) {
            return rule.prefix() == null || rule.prefix().isEmpty();
        }
        return (f.prefix() == null || f.prefix().isEmpty()) && f.tag() == null && f.and() == null
                && f.objectSizeGreaterThan() == null && f.objectSizeLessThan() == null;
    }

    /**
     * 예전 형식(규칙 바로 아래 Prefix, 필터 없음) 규칙을 같은 뜻의 Filter 형식으로 바꾼다.
     * 우리 규칙은 Filter 형식이라, 한 설정에 두 형식이 섞이면 스토리지가 PUT 을 거절(MalformedXML)할 수 있다.
     */
    @SuppressWarnings("deprecation")
    static LifecycleRule withFilter(LifecycleRule rule) {
        if (rule.filter() != null || rule.prefix() == null) {
            return rule;
        }
        return rule.toBuilder().prefix(null).filter(LifecycleRuleFilter.builder().prefix(rule.prefix()).build()).build();
    }

    @SuppressWarnings("deprecation")
    private static String describeFilter(LifecycleRule rule) {
        return rule.filter() != null ? rule.filter().toString() : "prefix=" + rule.prefix();
    }

    private List<LifecycleRule> currentRules(String bucket) {
        try {
            return s3Client.getBucketLifecycleConfiguration(req -> req.bucket(bucket)).rules();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                // NoSuchLifecycleConfiguration — 규칙 없음
                return List.of();
            }
            throw e;
        }
    }
}

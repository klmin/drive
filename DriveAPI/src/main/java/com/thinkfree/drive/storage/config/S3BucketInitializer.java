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
            List<LifecycleRule> rules = new ArrayList<>(currentRules(bucket));
            boolean present = rules.stream().anyMatch(r -> ABORT_RULE_ID.equals(r.id())
                    && r.abortIncompleteMultipartUpload() != null
                    && Integer.valueOf(days).equals(r.abortIncompleteMultipartUpload().daysAfterInitiation())
                    && r.status() == ExpirationStatus.ENABLED);
            if (present) {
                return;
            }
            rules.removeIf(r -> ABORT_RULE_ID.equals(r.id()));
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

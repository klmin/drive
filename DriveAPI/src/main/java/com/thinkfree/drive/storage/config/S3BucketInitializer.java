package com.thinkfree.drive.storage.config;

import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 기동 시 버킷 존재를 보장한다.
 * <p>
 * 기존 Ceph 전용 초기화는 {@code createBucket}만 호출했는데, Ceph RGW는 이미 있는 버킷에도 200을 주지만
 * RustFS(MinIO 계열)와 AWS S3는 409(BucketAlreadyOwnedByYou/BucketAlreadyExists)를 던진다.
 * 그래서 {@code headBucket}으로 먼저 확인하고 없을 때만 생성하며, 경합으로 409가 나도 무시한다.
 */
@ConditionalOnStorageType({StorageType.CEPH, StorageType.S3, StorageType.RUSTFS})
@Component
@RequiredArgsConstructor
@Slf4j
public class S3BucketInitializer {

    private final S3Client s3Client;
    private final S3ConnectionProperty connection;
    private final StorageProperty storageProperty;

    @PostConstruct
    public void ensureBucket() {
        String bucket = connection.bucketName();
        log.info("[Storage init] type: {}, bucket: {}, endpoint: {}, region: {}",
                 storageProperty.type(), bucket, connection.endpoint(), connection.region());

        try {
            s3Client.headBucket(req -> req.bucket(bucket));
            return;
        } catch (NoSuchBucketException e) {
            log.info("[Storage init] bucket not found, creating: {}", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                // 권한 부족(403) 등은 생성 시도로 해결되지 않으므로 기동은 계속하고 첫 요청에서 드러나게 둔다.
                log.warn("[Storage init] headBucket failed, skip create. bucket: {}, status: {}", bucket, e.statusCode(), e);
                return;
            }
        }

        try {
            s3Client.createBucket(req -> req.bucket(bucket));
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            log.info("[Storage init] bucket already exists: {}", bucket);
        }
    }
}

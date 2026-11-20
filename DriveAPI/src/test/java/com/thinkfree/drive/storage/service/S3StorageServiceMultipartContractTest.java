package com.thinkfree.drive.storage.service;

import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import com.thinkfree.drive.storage.result.StoragePart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.thinkfree.drive.common.TestS3Container;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스토리지 계약 테스트(docs/design/upload-session-redesign.md 단계 1) — 업로드 세션이 기대는 S3 동작을 실제 S3 호환 스토리지(MinIO)로 확인한다:
 * create·part·list·complete·abort(멱등)·range·copy·uploadPartCopy, NoSuchUpload 매핑, 같은 번호 재업로드 덮어쓰기.
 * Ceph·RustFS 는 같은 테스트를 이미지만 바꿔 돌린다(미결: 단계 1 결과로 확정).
 */
@DisplayName("S3StorageService 멀티파트 계약(MinIO)")
class S3StorageServiceMultipartContractTest {

    private static final String ACCESS_KEY = "mp-test-access";
    private static final String SECRET_KEY = "mp-test-secret-key";
    private static final String BUCKET = "mp-test-bucket";
    private static final int MIB = 1024 * 1024;
    /** S3 비마지막 파트 최소 크기 */
    private static final int PART = 5 * MIB;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MINIO = TestS3Container.create(ACCESS_KEY, SECRET_KEY);

    private static S3Client s3Client;
    private static S3StorageService storage;

    @BeforeAll
    static void start() {
        MINIO.start();
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        s3Client = S3Client.builder()
                           .endpointOverride(URI.create(endpoint))
                           .region(Region.US_EAST_1)
                           .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                           .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                           .build();
        s3Client.createBucket(b -> b.bucket(BUCKET));
        storage = new S3StorageService(s3Client, Mockito.mock(S3AsyncClient.class),
                new S3ConnectionProperty(endpoint, "us-east-1", ACCESS_KEY, SECRET_KEY, BUCKET), Mockito.mock(StorageProperty.class));
    }

    @AfterAll
    static void stop() {
        if (s3Client != null) {
            s3Client.close();
        }
        MINIO.stop();
    }

    private static String newKey() {
        return "1/" + UUID.randomUUID();
    }

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static byte[] readAll(String key) throws Exception {
        try (InputStream in = storage.getFileData(key).content()) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("create → 파트 병렬·역순 업로드 → listParts(번호순·크기) → complete → 내용·크기·Content-Type 일치")
    void fullCycle() throws Exception {
        String key = newKey();
        byte[] data = randomBytes(PART * 2 + 123, 1);
        String uploadId = storage.createMultipartUpload(key, "application/pdf");

        // 순서 무관 — 마지막 파트부터 올린다
        storage.uploadPart(key, uploadId, 3, new ByteArrayInputStream(data, PART * 2, 123), 123);
        storage.uploadPart(key, uploadId, 1, new ByteArrayInputStream(data, 0, PART), PART);
        storage.uploadPart(key, uploadId, 2, new ByteArrayInputStream(data, PART, PART), PART);

        List<StoragePart> parts = storage.listParts(key, uploadId);
        assertThat(parts).extracting(StoragePart::partNumber).containsExactly(1, 2, 3);
        assertThat(parts).extracting(StoragePart::size).containsExactly((long) PART, (long) PART, 123L);

        storage.completeMultipartUpload(key, uploadId, parts);

        assertThat(storage.headContentLength(key)).isEqualTo(data.length);
        assertThat(storage.getFileMetadata(key).contentType()).isEqualTo("application/pdf");
        assertThat(readAll(key)).isEqualTo(data);
    }

    @Test
    @DisplayName("같은 번호 재업로드는 덮어쓴다(마지막 것이 남는다)")
    void reuploadOverwritesPart() throws Exception {
        String key = newKey();
        String uploadId = storage.createMultipartUpload(key, "text/plain");
        storage.uploadPart(key, uploadId, 1, new ByteArrayInputStream("first".getBytes()), 5);
        storage.uploadPart(key, uploadId, 1, new ByteArrayInputStream("second".getBytes()), 6);

        List<StoragePart> parts = storage.listParts(key, uploadId);
        assertThat(parts).singleElement().satisfies(p -> assertThat(p.size()).isEqualTo(6));

        storage.completeMultipartUpload(key, uploadId, parts);
        assertThat(new String(readAll(key))).isEqualTo("second");
    }

    @Test
    @DisplayName("uploadPart 는 length 만큼만 읽고 원본 스트림을 닫지 않는다 — 한 스트림을 파트별로 잘라 쓸 수 있다")
    void uploadPartsFromStream() throws Exception {
        String key = newKey();
        byte[] data = randomBytes(PART * 2 + 7, 2);
        String uploadId = storage.createMultipartUpload(key, "application/octet-stream");

        List<StoragePart> parts = storage.uploadPartsFromStream(key, uploadId, new ByteArrayInputStream(data), data.length, PART);
        assertThat(parts).extracting(StoragePart::size).containsExactly((long) PART, (long) PART, 7L);

        storage.completeMultipartUpload(key, uploadId, parts);
        assertThat(readAll(key)).isEqualTo(data);
    }

    @Test
    @DisplayName("abort 뒤 listParts·uploadPart 는 NO_SUCH_UPLOAD, abort 재호출은 성공(멱등)")
    void abortIsIdempotentAndMapsNoSuchUpload() {
        String key = newKey();
        String uploadId = storage.createMultipartUpload(key, "text/plain");
        storage.uploadPart(key, uploadId, 1, new ByteArrayInputStream("x".getBytes()), 1);

        storage.abortMultipartUpload(key, uploadId);
        assertThatCode(() -> storage.abortMultipartUpload(key, uploadId)).doesNotThrowAnyException();

        assertThatThrownBy(() -> storage.listParts(key, uploadId))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getCode()).isEqualTo(StorageErrorCode.NO_SUCH_UPLOAD));
        assertThatThrownBy(() -> storage.uploadPart(key, uploadId, 2, new ByteArrayInputStream("y".getBytes()), 1))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getCode()).isEqualTo(StorageErrorCode.NO_SUCH_UPLOAD));
    }

    @Test
    @DisplayName("없는 uploadId 의 abort 도 성공(멱등)")
    void abortUnknownUpload() {
        assertThatCode(() -> storage.abortMultipartUpload(newKey(), "does-not-exist")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("putObject — 0바이트·작은 파일 단일 PUT")
    void putObjectSingle() throws Exception {
        String empty = newKey();
        storage.putObject(empty, "text/plain", new ByteArrayInputStream(new byte[0]), 0);
        assertThat(storage.headContentLength(empty)).isZero();

        String small = newKey();
        storage.putObject(small, "text/plain", new ByteArrayInputStream("hello".getBytes()), 5);
        assertThat(new String(readAll(small))).isEqualTo("hello");
    }

    @Test
    @DisplayName("readRange — 앞부분만, 객체보다 긴 범위는 있는 만큼, 0바이트 객체는 빈 배열")
    void readRange() {
        String key = newKey();
        byte[] data = randomBytes(1000, 3);
        storage.putObject(key, "application/octet-stream", new ByteArrayInputStream(data), data.length);

        assertThat(storage.readRange(key, 0, 99)).isEqualTo(Arrays.copyOfRange(data, 0, 100));
        assertThat(storage.readRange(key, 0, 65_535)).isEqualTo(data);

        String empty = newKey();
        storage.putObject(empty, "text/plain", new ByteArrayInputStream(new byte[0]), 0);
        assertThat(storage.readRange(empty, 0, 65_535)).isEmpty();
    }

    @Test
    @DisplayName("headContentLength — 없는 객체는 NO_SUCH_KEY")
    void headMissing() {
        assertThatThrownBy(() -> storage.headContentLength(newKey()))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getCode()).isEqualTo(StorageErrorCode.NO_SUCH_KEY));
    }

    @Test
    @DisplayName("버킷 lifecycle — MinIO 는 AbortIncompleteMultipartUpload 를 지원하지 않는다(400): 기동은 계속하고 기존 규칙은 건드리지 않는다")
    void bucketLifecycleRule() {
        s3Client.putBucketLifecycleConfiguration(req -> req.bucket(BUCKET).lifecycleConfiguration(c -> c.rules(
                software.amazon.awssdk.services.s3.model.LifecycleRule.builder()
                        .id("ops-rule")
                        .status(software.amazon.awssdk.services.s3.model.ExpirationStatus.ENABLED)
                        .filter(f -> f.prefix("tmp/"))
                        .expiration(e -> e.days(30))
                        .build())));
        var property = new com.thinkfree.drive.upload.session.properties.UploadSessionProperty(
                Duration.ofHours(1), Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofHours(24),
                org.springframework.util.unit.DataSize.ofMegabytes(16), org.springframework.util.unit.DataSize.ofMegabytes(32),
                org.springframework.util.unit.DataSize.ofTerabytes(5), 4, 50, 500, true, 1);
        var initializer = new com.thinkfree.drive.storage.config.S3BucketInitializer(s3Client,
                new S3ConnectionProperty("http://unused", "us-east-1", ACCESS_KEY, SECRET_KEY, BUCKET), Mockito.mock(StorageProperty.class), property);

        assertThatCode(initializer::init).doesNotThrowAnyException();

        // MinIO 는 미완성 업로드를 자체 정리(stale_uploads_expiry, 기본 24h)하고 lifecycle 의 이 동작은 거부한다 — Ceph·AWS 에서는 규칙이 들어간다(병합 로직은 S3BucketInitializerTest)
        var rules = s3Client.getBucketLifecycleConfiguration(req -> req.bucket(BUCKET)).rules();
        assertThat(rules).extracting(software.amazon.awssdk.services.s3.model.LifecycleRule::id).contains("ops-rule");
    }

    @Test
    @DisplayName("copyObjectAnySize — 한도 이하는 CopyObject, 넘으면 UploadPartCopy 로 같은 내용·Content-Type")
    void copyAnySize() throws Exception {
        String source = newKey();
        byte[] data = randomBytes(PART * 2 + 11, 4);
        storage.putObject(source, "image/png", new ByteArrayInputStream(data), data.length);

        String single = newKey();
        storage.copyObjectAnySize(source, single);
        assertThat(readAll(single)).isEqualTo(data);

        // 한도를 낮춰 멀티파트 경로(UploadPartCopy, 파트 5MiB → 3파트)를 탄다
        String multipart = newKey();
        storage.copyObjectAnySize(source, multipart, PART, PART);
        assertThat(readAll(multipart)).isEqualTo(data);
        assertThat(storage.getFileMetadata(multipart).contentType()).isEqualTo("image/png");
    }
}

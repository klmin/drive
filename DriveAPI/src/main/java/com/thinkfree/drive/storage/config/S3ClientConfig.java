package com.thinkfree.drive.storage.config;

import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * S3 프로토콜 클라이언트 구성. {@code storage.type}이 S3 프로토콜을 쓰는 종류(CEPH, S3, RUSTFS)일 때만 뜬다.
 * <p>
 * 세 종류는 모두 같은 AWS SDK {@link S3Client}로 접속하며, 접속 방식은 종류가 정한다.
 * <ul>
 *   <li>S3(AWS): endpoint 없음, region 필수</li>
 *   <li>CEPH, RUSTFS: endpoint 필수(path-style), region은 SDK 형식값</li>
 * </ul>
 * 조합이 어긋나면 기동 시 {@link IllegalStateException}으로 즉시 실패한다.
 * 용량 조회는 이 클래스와 무관하게 {@code StorageAdminService} 구현이 종류별로 담당한다.
 */
@ConditionalOnStorageType({StorageType.CEPH, StorageType.S3, StorageType.RUSTFS})
@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3ClientConfig {

    /** 엔드포인트 지정형 접속에서 SDK 필수값으로만 쓰이는 형식적 리전 */
    private static final Region ENDPOINT_DEFAULT_REGION = Region.US_EAST_1;

    private final StorageProperty storageProperty;
    private final S3ConnectionProperty connection;

    @PostConstruct
    void validate() {
        StorageType type = storageType();
        switch (type) {
            case S3 -> {
                require(!connection.isEndpointBased(),
                        "storage.type=s3 는 AWS S3 접속이라 storage.connection.endpoint(DRIVE_STORAGE_ENDPOINT)를 지정하지 않습니다. S3 호환 서버는 type=ceph 또는 rustfs 를 사용하세요.");
                require(connection.region() != null,
                        "storage.type=s3 는 storage.connection.region(DRIVE_STORAGE_REGION)이 필요합니다.");
            }
            case CEPH, RUSTFS -> require(connection.isEndpointBased(),
                    "storage.type=" + type.name().toLowerCase() + " 는 storage.connection.endpoint(DRIVE_STORAGE_ENDPOINT)가 필요합니다.");
        }
        log.info("[Storage] type: {}, endpoint: {}, region: {}, bucket: {}",
                 type, connection.endpoint(), connection.region(), connection.bucketName());
    }

    @Bean
    public StaticCredentialsProvider s3CredentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(connection.accessKey(), connection.secretKey())
        );
    }

    @Bean
    public S3Client s3Client(StaticCredentialsProvider credentials) {
        var builder = S3Client.builder()
                              .credentialsProvider(credentials)
                              .region(resolveRegion());
        if (connection.isEndpointBased()) {
            builder.endpointOverride(URI.create(connection.endpoint()))
                   .serviceConfiguration(pathStyle());
        }
        return builder.build();
    }

    @Bean
    public S3AsyncClient s3AsyncClient(StaticCredentialsProvider credentials) {
        var builder = S3AsyncClient.builder()
                                   .credentialsProvider(credentials)
                                   .region(resolveRegion())
                                   .overrideConfiguration(o -> o.retryStrategy(RetryMode.STANDARD));
        if (connection.isEndpointBased()) {
            builder.endpointOverride(URI.create(connection.endpoint()))
                   .serviceConfiguration(pathStyle());
        }
        return builder.build();
    }

    private StorageType storageType() {
        return storageProperty.type() == null ? StorageType.CEPH : storageProperty.type();
    }

    private Region resolveRegion() {
        if (connection.region() != null) {
            return Region.of(connection.region());
        }
        return ENDPOINT_DEFAULT_REGION;
    }

    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

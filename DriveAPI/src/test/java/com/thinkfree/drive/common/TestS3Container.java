package com.thinkfree.drive.common;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * 테스트용 S3 호환 스토리지(RustFS) 컨테이너.
 * <p>
 * 앱은 기동할 때 {@code S3BucketInitializer} 가 버킷을 확인하므로, 스프링 컨텍스트를 띄우는 테스트는 닿을 수 있는 S3 가 있어야 한다.
 * 사내망 Ceph 에 기대면 CI(GitHub 러너)에서 컨텍스트 로딩이 실패하므로 test 프로필은 이 컨테이너를 쓴다
 * ({@link TestS3ContainerEnvironmentPostProcessor}).
 * <p>
 * {@link #shared()} 는 JVM 당 하나를 띄워 모든 테스트 컨텍스트가 같이 쓴다(종료는 Testcontainers Ryuk 가 맡는다).
 * 버킷을 따로 비워야 하는 테스트는 {@link #create(String, String)} 로 자기 컨테이너를 띄운다.
 */
public final class TestS3Container {

    /** 프로젝트가 지원하는 S3 호환 스토리지(StorageType.RUSTFS) — docker-compose.local.yml 과 같은 이미지 */
    public static final String IMAGE = "rustfs/rustfs:1.0.0";
    public static final int PORT = 9000;
    public static final String ACCESS_KEY = "drive-test-access";
    public static final String SECRET_KEY = "drive-test-secret-key";

    private static GenericContainer<?> shared;

    private TestS3Container() {
    }

    /** 새 컨테이너(시작 전) — 호출한 쪽이 start/stop 한다 */
    @SuppressWarnings("resource")
    public static GenericContainer<?> create(String accessKey, String secretKey) {
        return new GenericContainer<>(IMAGE)
                .withEnv("RUSTFS_ACCESS_KEY", accessKey)
                .withEnv("RUSTFS_SECRET_KEY", secretKey)
                .withEnv("RUSTFS_VOLUMES", "/data")
                .withEnv("RUSTFS_ADDRESS", ":" + PORT)
                .withExposedPorts(PORT)
                .waitingFor(Wait.forHttp("/health").forPort(PORT).withStartupTimeout(Duration.ofMinutes(2)));
    }

    /** JVM 공용 컨테이너 — 처음 부를 때 띄운다 */
    public static synchronized GenericContainer<?> shared() {
        if (shared == null) {
            GenericContainer<?> container = create(ACCESS_KEY, SECRET_KEY);
            container.start();
            shared = container;
        }
        return shared;
    }

    public static String endpoint(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(PORT);
    }
}

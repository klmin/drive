package com.thinkfree.drive.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;

/**
 * {@code drive.test.s3-container.enabled=true}(application-test.yml) 이면 공용 RustFS 컨테이너({@link TestS3Container#shared()})를 띄우고
 * {@code storage.connection.*} 를 그 컨테이너로 덮어쓴다.
 * <p>
 * 설정 파일(ConfigData) 을 읽은 뒤에 돌아야 프로필별 값을 볼 수 있어 가장 늦은 순서로 둔다.
 * 테스트 클래스의 {@code @DynamicPropertySource} 는 이보다 나중에 앞쪽으로 붙으므로 자기 컨테이너를 쓰는 테스트가 이긴다.
 * 등록: src/test/resources/META-INF/spring.factories (테스트 클래스패스에만 있다).
 */
public class TestS3ContainerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String ENABLED = "drive.test.s3-container.enabled";
    static final String SOURCE_NAME = "testS3Container";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(ENABLED, Boolean.class, false)
                || environment.getPropertySources().contains(SOURCE_NAME)) {
            return;
        }
        GenericContainer<?> container = TestS3Container.shared();
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, Map.of(
                "storage.connection.endpoint", TestS3Container.endpoint(container),
                "storage.connection.access-key", TestS3Container.ACCESS_KEY,
                "storage.connection.secret-key", TestS3Container.SECRET_KEY
        )));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

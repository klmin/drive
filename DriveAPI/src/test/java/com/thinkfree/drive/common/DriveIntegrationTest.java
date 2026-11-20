package com.thinkfree.drive.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 스프링 컨텍스트를 띄우는 테스트의 공통 설정 — 항상 test 프로필(Testcontainers PostgreSQL, application-test.yml).
 * <p>
 * 테스트는 로컬·dev DB(.env 의 DRIVE_DATABASE_*)를 보지 않는다. 그쪽에 붙으면 PC·사람마다 결과가 다르고 CI 에서 돌지 않으며,
 * 쓰기 테스트가 공용 dev 데이터를 바꾼다. 필요한 데이터는 테스트가 직접 넣는다(Flyway 시드: 기본 테넌트 1·admin·superadmin).
 * IntelliJ 실행과 {@code mvn test} 가 같은 설정으로 돌도록 프로필을 어노테이션으로 고정한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@ActiveProfiles("test")
public @interface DriveIntegrationTest {
}

package com.thinkfree.drive.global.jpa.audit;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Spring Data JPA Auditing — JPA 엔티티의 감사 값 규칙. JdbcClient 경로({@link JdbcAudit})도 같은 규칙을 쓴다.
 * <ul>
 *   <li>누가: 시큐리티 사용자({@code CustomUserDetails}). 있으면 엔티티 값을 덮어쓰고, 없으면(스케줄러·웹오피스 어댑터 등) 엔티티에 선언한 값을 둔다.
 *       없을 때 슈퍼어드민 등으로 채우지 않는다 — 사용자 정보가 빠진 버그가 다른 사용자 기록으로 묻힌다.</li>
 *   <li>언제: 현재 시각 초 절삭</li>
 *   <li>INSERT 때 수정자·수정일은 비워 둔다({@code modifyOnCreate = false})</li>
 * </ul>
 * 롤백·복원처럼 이전 값을 그대로 되돌리는 쓰기는 감사를 거치지 않고 값을 직접 바인딩한다.
 * {@code @WebMvcTest} 슬라이스가 JPA 메타모델 없이 뜨도록 메인 애플리케이션 클래스가 아닌 별도 설정에 둔다.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditDateTimeProvider", modifyOnCreate = false)
public class AuditConfig {

    @Bean
    public AuditorAware<Integer> auditorAware() {
        return () -> Optional.ofNullable(JdbcAudit.currentUserSeq());
    }

    @Bean
    public DateTimeProvider auditDateTimeProvider() {
        return () -> Optional.of(JdbcAudit.now());
    }
}

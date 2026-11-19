package com.thinkfree.drive.auditlog.config;

import com.thinkfree.drive.auditlog.lookup.tenant.TenantNameResolver;
import com.thinkfree.drive.auditlog.sender.AuditLogSender;
import com.thinkfree.drive.auditlog.sender.impl.AuditLogPayloadToEventInMapper;
import com.thinkfree.drive.auditlog.sender.impl.GovernanceSdkAuditLogSender;
import com.thinkfree.drive.userlog.service.IpLocationService;
import com.thinkfree.governance.sdk.auth.GovernanceCredentials;
import com.thinkfree.governance.sdk.client.GovernanceClient;
import com.thinkfree.governance.sdk.client.GovernanceClientSettings;
import com.thinkfree.governance.sdk.http.JdkHttpTransport;
import com.thinkfree.governance.sdk.json.jackson.JacksonJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.URI;
import java.time.Duration;

/**
 * 감사 로그 Governance 전송 배선. {@code governance.audit.enabled=true}일 때만 활성화되어
 * {@link GovernanceClient}(싱글턴)·전송 전용 executor·매퍼·SDK sender를 등록한다.
 * enabled=false(기본)면 이 설정 자체가 로드되지 않고 기존 LoggingAuditLogSender가 유지된다.
 */
@Configuration
@EnableConfigurationProperties(GovernanceSdkProperties.class)
@ConditionalOnProperty(prefix = "governance.audit", name = "enabled", havingValue = "true")
@Slf4j
public class GovernanceSdkConfig {

    private static final String AUDIT_EXECUTOR = "governanceAuditExecutor";

    @Bean(destroyMethod = "close")
    public GovernanceClient governanceClient(GovernanceSdkProperties properties) {
        GovernanceSdkProperties.Sdk sdk = properties.getSdk();
        GovernanceClientSettings settings = GovernanceClientSettings.newBuilder()
                .setEndpoint(URI.create(sdk.getEndpoint()))
                .setCredentials(GovernanceCredentials.newBuilder()
                        .setTenantId(sdk.getTenantId())
                        .setKeyId(sdk.getKeyId())
                        .setBase64Secret(sdk.getBase64Secret())
                        .build())
                .setHttpTransport(JdkHttpTransport.newBuilder()
                        .setConnectTimeout(Duration.ofSeconds(sdk.getConnectTimeoutSeconds()))
                        .setReadTimeout(Duration.ofSeconds(sdk.getReadTimeoutSeconds()))
                        .build())
                .setJsonCodec(new JacksonJsonCodec())
                .build();
        log.info("[AuditLog] GovernanceClient initialized. endpoint={}", sdk.getEndpoint());
        return GovernanceClient.create(settings);
    }

    /** 전송 전용 executor. 큐가 차면 감사 로그는 best-effort라 드롭하고 로그만 남긴다(사용자 요청/다른 async에 영향 없음). */
    @Bean(name = AUDIT_EXECUTOR)
    public ThreadPoolTaskExecutor governanceAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("gov-audit-");
        // 제출한 스레드의 관측 컨텍스트(trace·MDC)를 넘긴다 — 감사 로그 전송 구간이 원래 요청 트레이스에 붙고 로그에도 같은 traceId 가 찍힌다
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("[AuditLog] governance send task rejected (queue full) - dropped"));
        executor.initialize();
        return executor;
    }

    @Bean
    public AuditLogPayloadToEventInMapper auditLogPayloadToEventInMapper(GovernanceSdkProperties properties,
                                                                         TenantNameResolver tenantNameResolver,
                                                                         IpLocationService ipLocationService) {
        return new AuditLogPayloadToEventInMapper(properties.getOrigin().getInstanceId(), tenantNameResolver,
                ipLocationService);
    }

    @Bean
    public AuditLogSender governanceSdkAuditLogSender(GovernanceClient governanceClient,
                                                      AuditLogPayloadToEventInMapper mapper,
                                                      @Qualifier(AUDIT_EXECUTOR) ThreadPoolTaskExecutor executor) {
        return new GovernanceSdkAuditLogSender(governanceClient, mapper, executor);
    }
}

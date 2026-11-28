package com.thinkfree.drive.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.security.properties.EmbedProperty;
import com.thinkfree.drive.tenant.addressing.StaticBundleIndex;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 테넌트 판별 필터 등록. 모드 차이는 주입되는 {@link TenantAddressingStrategy} 빈({@code tenant.resolution-mode} 로 택일)이 담당.
 */
@Configuration
public class GlobalFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantResolverFilterRegistration(
            TenantAddressingStrategy addressingStrategy,
            TenantSeqResolver tenantSeqResolver,
            ObjectMapper objectMapper,
            StaticBundleIndex staticBundleIndex
    ) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(
                new TenantFilter(addressingStrategy, tenantSeqResolver, objectMapper, staticBundleIndex)
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * 업로드 본문 동시 상한 — 테넌트 판별(경로 접두 제거) 뒤, 시큐리티(인증 DB 조회)·multipart 파싱 전.
     * 상한은 WAS 마다 따로다(전체 = 상한 × WAS 대수).
     */
    @Bean
    public FilterRegistrationBean<UploadConcurrencyLimitFilter> uploadConcurrencyLimitFilterRegistration(
            @Value("${drive.upload.max-concurrent-bodies:50}") int maxConcurrentBodies,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<UploadConcurrencyLimitFilter> registration = new FilterRegistrationBean<>(
                new UploadConcurrencyLimitFilter(maxConcurrentBodies, objectMapper, meterRegistry)
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    /** iframe 허용 범위 — 테넌트 판별 실패(404)·리다이렉트 응답에도 붙도록 TenantFilter 앞 */
    @Bean
    public FilterRegistrationBean<FrameAncestorsFilter> frameAncestorsFilterRegistration(EmbedProperty embedProperty) {
        FilterRegistrationBean<FrameAncestorsFilter> registration = new FilterRegistrationBean<>(
                new FrameAncestorsFilter(embedProperty.allowedOrigins())
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}

package com.thinkfree.drive.external.ceph.dashboard.config;

import com.thinkfree.drive.external.ceph.dashboard.auth.CephTokenManager;
import com.thinkfree.drive.external.ceph.dashboard.client.CephDashboardAuthClient;
import com.thinkfree.drive.external.ceph.dashboard.client.CephDashboardClient;
import com.thinkfree.drive.external.ceph.dashboard.interceptor.CephDashboardAuthInterceptor;
import com.thinkfree.drive.restclient.config.RestClientFactory;
import com.thinkfree.drive.storage.properties.StorageAdminProperty;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@ConditionalOnStorageType(StorageType.CEPH)
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CephDashboardConfig {
    
    private static final String BEAN_NAME = "cephTokenExecutor";
    private static final String THREAD_NAME = "ceph-token-";
    
    private final StorageAdminProperty storageAdminProperty;
    private final RestClientFactory restClientFactory;

    /** storage.admin.* 는 type=ceph 외 배포에서 비워둘 수 있게 선택값이라, ceph인데 비어 있으면 기동 시 경고로 알린다. */
    @PostConstruct
    void warnIfDashboardUrlMissing() {
        if (!StringUtils.hasText(storageAdminProperty.url())) {
            log.warn("[Ceph dashboard] storage.admin.url 이 비어 있습니다. 용량 조회(StorageAdminService)가 실패합니다. DRIVE_STORAGE_ADMIN_URL 을 설정하세요.");
        }
    }

    @Bean
    public CephDashboardAuthClient cephDashboardAuthRestClient() {
        return restClientFactory.createClient(CephDashboardAuthClient.class, storageAdminProperty.url());
    }
    
    @Bean
    public CephDashboardClient cephDashboardRestClient(CephTokenManager cephTokenManager) {
        var client = restClientFactory.createRestClient(storageAdminProperty.url())
                                      .mutate()
                                      .requestInterceptors(r -> r.add(new CephDashboardAuthInterceptor(cephTokenManager)))
                                      .build();
        
        return restClientFactory.createClient(CephDashboardClient.class, client);
    }
    
    @Bean
    public CephTokenManager cephTokenManager(CephDashboardAuthClient cephDashboardAuthRestClient, @Qualifier(BEAN_NAME) Executor cephTokenExecutor){
        return new CephTokenManager(cephDashboardAuthRestClient, storageAdminProperty, cephTokenExecutor);
    }
    
    @Bean(name = BEAN_NAME)
    public ThreadPoolTaskExecutor cephTokenExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(8);
        ex.setThreadNamePrefix(THREAD_NAME);
        // 제출한 스레드의 관측 컨텍스트(trace·MDC)를 넘긴다 — 토큰 발급 호출이 갱신을 시작시킨 요청 트레이스에 붙는다
        ex.setTaskDecorator(new ContextPropagatingTaskDecorator());
        ex.setAllowCoreThreadTimeOut(true);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}

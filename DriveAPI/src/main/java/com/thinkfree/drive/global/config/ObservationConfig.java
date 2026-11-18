package com.thinkfree.drive.global.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * 관측(메트릭·트레이스) 대상 조정.
 * <p>
 * Alloy 가 15초마다 관리 포트의 {@code /actuator/prometheus} 를 가져가는데, 이 요청까지 관측하면
 * 트레이스 저장소가 수집 요청으로 채워지고 요청 수 메트릭에도 섞인다 — {@code /actuator} 요청은 관측하지 않는다.
 * (Spring Security 자체 구간은 application.yml {@code management.observations.enable.spring.security=false} 로 끈다)
 */
@Configuration(proxyBeanMethods = false)
public class ObservationConfig {

    private static final String ACTUATOR_PATH_PREFIX = "/actuator";

    @Bean
    ObservationPredicate skipActuatorRequestObservation() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                return !serverContext.getCarrier().getRequestURI().startsWith(ACTUATOR_PATH_PREFIX);
            }
            return true;
        };
    }
}

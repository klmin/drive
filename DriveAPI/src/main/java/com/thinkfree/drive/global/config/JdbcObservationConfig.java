package com.thinkfree.drive.global.config;

import io.micrometer.observation.ObservationRegistry;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.JdbcObservationDocumentation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Set;

/**
 * SQL 실행을 트레이스 구간(span)으로 남긴다 — DataSource 를 datasource-proxy 로 감싸고 datasource-micrometer 리스너를 붙인다.
 * <ul>
 *   <li>쿼리 실행만 구간으로 남긴다(커넥션 획득·결과 읽기 구간은 요청마다 많아 트레이스를 가린다)</li>
 *   <li>바인딩 값은 남기지 않는다(개인정보·토큰이 트레이스 저장소에 남지 않게). SQL 문은 ? 그대로 남는다</li>
 * </ul>
 * datasource-micrometer-spring-boot 자동 설정은 p6spy 데코레이터(DataSourceDecoratorAutoConfiguration)와 빈 이름이 겹쳐 쓰지 않는다.
 * 끄기: {@code drive.observability.jdbc-tracing.enabled=false}
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "drive.observability.jdbc-tracing.enabled", havingValue = "true", matchIfMissing = true)
public class JdbcObservationConfig {

    @Bean
    static BeanPostProcessor jdbcObservationDataSourcePostProcessor(ObjectProvider<ObservationRegistry> observationRegistry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource) || bean instanceof ProxyDataSource) {
                    return bean;
                }
                DataSourceObservationListener listener = new DataSourceObservationListener(observationRegistry::getObject);
                listener.setSupportedTypes(Set.of(JdbcObservationDocumentation.QUERY));
                listener.setIncludeParameterValues(false);
                return ProxyDataSourceBuilder.create(beanName, dataSource)
                        .listener(listener)
                        .methodListener(listener)
                        .build();
            }
        };
    }
}

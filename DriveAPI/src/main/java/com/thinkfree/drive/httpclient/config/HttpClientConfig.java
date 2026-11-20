package com.thinkfree.drive.httpclient.config;


import com.thinkfree.drive.httpclient.HttpClientProperties;
import com.thinkfree.drive.httpclient.guard.GuardedDnsResolver;
import com.thinkfree.drive.httpclient.guard.GuardedHttpClient;
import com.thinkfree.drive.httpclient.guard.OutboundAddressGuard;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.DefaultBackoffStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {
    
    @Bean
    public HttpClientProperties httpClientProperties() {
        return HttpClientProperties.defaults();
    }
    
    @Bean
    public HttpClient httpClient(HttpClientProperties httpClientProperties){
        
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                                                            .setConnectTimeout(Timeout.ofMilliseconds(httpClientProperties.connectionTimeout()))
                                                            .setSocketTimeout(Timeout.ofMilliseconds(httpClientProperties.socketTimeout()))
                                                            .setValidateAfterInactivity(Timeout.ofMilliseconds(httpClientProperties.validateAfterInactivity()))
                                                            .build();
        
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(httpClientProperties.maxPool());
        connManager.setDefaultMaxPerRoute(httpClientProperties.maxPerRoute());
        connManager.setDefaultSocketConfig(SocketConfig.DEFAULT);
        connManager.setDefaultConnectionConfig(connectionConfig);

        
        RequestConfig requestConfig = RequestConfig.custom()
                                                   .setConnectionRequestTimeout(Timeout.ofMilliseconds(httpClientProperties.requestTimeout()))
                                                   .setResponseTimeout(Timeout.ofMilliseconds(httpClientProperties.responseTimeout()))
                                                   .build();
        return HttpClients.custom()
                          .setConnectionManager(connManager)
                          .setDefaultRequestConfig(requestConfig)
                          .setConnectionBackoffStrategy(new DefaultBackoffStrategy())
                          .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
                          .setRetryStrategy(new DefaultHttpRequestRetryStrategy(httpClientProperties.retryCount(), Timeout.ofMilliseconds(httpClientProperties.backoff())))
                          .evictIdleConnections(Timeout.ofMilliseconds(httpClientProperties.idleConnectionTimeout()))
                          .build();
    }

    /**
     * 사용자·테넌트 관리자가 입력한 주소(OIDC·JWKS) 전용 — 연결 직전 DNS 조회에서 내부망 가드를 적용하고 리다이렉트는 따라가지 않는다
     * (외부 주소가 302 로 내부 주소로 넘기는 우회 차단). 타임아웃·재시도는 기본 클라이언트와 같다.
     */
    @Bean
    public GuardedHttpClient guardedHttpClient(HttpClientProperties httpClientProperties, OutboundAddressGuard outboundAddressGuard) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                                                            .setConnectTimeout(Timeout.ofMilliseconds(httpClientProperties.connectionTimeout()))
                                                            .setSocketTimeout(Timeout.ofMilliseconds(httpClientProperties.socketTimeout()))
                                                            .setValidateAfterInactivity(Timeout.ofMilliseconds(httpClientProperties.validateAfterInactivity()))
                                                            .build();
        PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                                                                                                 .setDnsResolver(new GuardedDnsResolver(outboundAddressGuard))
                                                                                                 .setMaxConnTotal(httpClientProperties.maxPool())
                                                                                                 .setMaxConnPerRoute(httpClientProperties.maxPerRoute())
                                                                                                 .setDefaultSocketConfig(SocketConfig.DEFAULT)
                                                                                                 .setDefaultConnectionConfig(connectionConfig)
                                                                                                 .build();
        RequestConfig requestConfig = RequestConfig.custom()
                                                   .setConnectionRequestTimeout(Timeout.ofMilliseconds(httpClientProperties.requestTimeout()))
                                                   .setResponseTimeout(Timeout.ofMilliseconds(httpClientProperties.responseTimeout()))
                                                   .build();
        return new GuardedHttpClient(HttpClients.custom()
                                                .setConnectionManager(connManager)
                                                .setDefaultRequestConfig(requestConfig)
                                                .disableRedirectHandling()
                                                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(httpClientProperties.retryCount(), Timeout.ofMilliseconds(httpClientProperties.backoff())))
                                                .evictIdleConnections(Timeout.ofMilliseconds(httpClientProperties.idleConnectionTimeout()))
                                                .build());
    }
}

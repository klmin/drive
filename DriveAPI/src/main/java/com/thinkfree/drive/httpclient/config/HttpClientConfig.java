package com.thinkfree.drive.httpclient.config;


import com.thinkfree.drive.httpclient.HttpClientProperties;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.DefaultBackoffStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
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
}

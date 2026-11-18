package com.thinkfree.drive.external.ceph.dashboard.interceptor;

import com.thinkfree.drive.external.ceph.dashboard.auth.CephTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class CephDashboardAuthInterceptor implements ClientHttpRequestInterceptor {
    
    private final CephTokenManager tokenManager;
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte [] body, ClientHttpRequestExecution execution) throws IOException {
        
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, tokenManager.getBearerToken());
        ClientHttpResponse response = execution.execute(request, body);
        
        if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            try (response) { /* auto close */ }
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, tokenManager.forceRefreshBearerToken());
            return execution.execute(request, body);
        }
        
        return response;
    }
    
    

}

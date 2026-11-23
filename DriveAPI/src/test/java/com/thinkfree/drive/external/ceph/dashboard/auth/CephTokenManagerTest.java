package com.thinkfree.drive.external.ceph.dashboard.auth;

import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.thinkfree.drive.common.DriveIntegrationTest;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@EnabledIf(expression = "#{environment['storage.type'] == 'ceph'}", loadContext = true)
@DriveIntegrationTest
@Slf4j
@Transactional
@Disabled("실제 Ceph 대시보드 API 를 호출한다(외부 시스템) — CephDashboard 클라이언트를 Mock 으로 바꿀 때 다시 켠다. getBearerTokenConcurrent 는 예외를 삼켜 단언이 없다")
class CephTokenManagerTest {
    
    @Autowired
    public CephTokenManager cephTokenManager;
    
    @Test
    void getBearerToken() {
        var token = cephTokenManager.getBearerToken();
        log.info("token : {}", token);
        var token2 = cephTokenManager.getBearerToken();
        log.info("token2 : {}", token2);
        var forceRefreshToken = cephTokenManager.forceRefreshBearerToken();
        log.info("forceRefreshToken : {}", forceRefreshToken);
        var forceRefreshToken2 = cephTokenManager.forceRefreshBearerToken();
        log.info("forceRefreshToken : {}", forceRefreshToken2);
        var token3 = cephTokenManager.getBearerToken();
        log.info("token3 : {}", token3);
    }
    
    @Test
    void getBearerTokenConcurrent() {
        
        int threadCount = 20;
      
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();
        
        Executor executor = new DelegatingSecurityContextExecutor(
                Executors.newFixedThreadPool(threadCount)
        );
        
        for (int i = 0; i < threadCount; i++) {
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            
            try {
                
                   var token = cephTokenManager.getBearerToken();
                System.out.println("token : "+token);
                    successCnt.getAndIncrement();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor).exceptionally(e -> {
                failCnt.getAndIncrement();
                log.error("CompletableFuture Exception: {}", e.getMessage());
                return null;
            });
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("successCnt : {}", successCnt.get());
                    log.info("failCnt : {}", failCnt.get());
                }).join();
        
    }
}
package com.thinkfree.drive.user.quota.alert.state.service;

import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.user.quota.alert.state.dto.UserQuotaAlertStateEvaluateDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
@Slf4j
class UserQuotaAlertStateServiceTest {
    
    @Autowired
    public UserQuotaAlertStateService userQuotaAlertStateService;
    
    @WithMockUserCustom
    @Test
    void increaseQuotaAlertState() {
        
        Integer userSeq = 1;
        Instant occurredDatetime = Instant.now();
        Integer actorUserSeq = 2;
        
        UserQuotaAlertStateEvaluateDto dto = UserQuotaAlertStateEvaluateDto.builder()
                                                                                .userSeq(userSeq)
                                                                                .actorUserSeq(actorUserSeq)
                                                                                .occurredDatetime(occurredDatetime)
                                                                                .build();
        userQuotaAlertStateService.increaseQuotaAlertState(dto);
    }
    
    @WithMockUserCustom
    @Test
    void parallelIncreaseQuotaAlertState() {
        
        Integer userSeq = 1;
        Instant occurredDatetime = Instant.now();
        Long totalQuotaByte = 1000000000000L;
        AtomicReference<Long> useQuotaByte = new AtomicReference<>(899999985000L);
        Integer actorUserSeq = 2;
        long additionalBytes = 8000L;
        
        int threadCount = 100;
      
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();
        
        Executor executor = new DelegatingSecurityContextExecutor(
                Executors.newFixedThreadPool(threadCount)
        );
        
        for (int i = 0; i < threadCount; i++) {
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            
            try {
                
                UserQuotaAlertStateEvaluateDto dto = UserQuotaAlertStateEvaluateDto.builder()
                                                                                      .userSeq(userSeq)
                                                                                      .actorUserSeq(actorUserSeq)
                                                                                      .occurredDatetime(occurredDatetime)
                                                                                      .build();
                
                userQuotaAlertStateService.increaseQuotaAlertState(dto);
                    
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
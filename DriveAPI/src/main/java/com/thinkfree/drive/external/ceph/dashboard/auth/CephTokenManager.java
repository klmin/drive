package com.thinkfree.drive.external.ceph.dashboard.auth;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.external.ceph.dashboard.claims.CephJwtClaims;
import com.thinkfree.drive.external.ceph.dashboard.client.CephDashboardAuthClient;
import com.thinkfree.drive.external.ceph.dashboard.request.CephAuthRequest;
import com.thinkfree.drive.external.ceph.dashboard.response.CephAuthResponse;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.storage.properties.StorageAdminProperty;
import com.thinkfree.drive.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;


@RequiredArgsConstructor
@Slf4j
public class CephTokenManager {
    
    private final AtomicReference<Cached> cache = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Cached>> refreshInFlight = new AtomicReference<>();
    
    private final CephDashboardAuthClient cephDashboardAuthClient;
    private final StorageAdminProperty storageAdminProperty;
    private final Executor cephTokenExecutor;
    
    private record Cached(String token, Long exp) {}

    public String getBearerToken() {
        return DriveConstants.BEARER + getValidToken(false);
    }
    
    public String forceRefreshBearerToken() {
        return DriveConstants.BEARER + getValidToken(true);
    }
    
    public String getValidToken(boolean forceRefresh) {
        
        Cached snapshot = cache.get();
        
        boolean needRefresh = forceRefresh || snapshot == null || JwtUtils.shouldRefresh(snapshot.exp, storageAdminProperty.refreshThreshold());
        
        if (needRefresh) {

            CompletableFuture<Cached> candidate = new CompletableFuture<>();
    
            CompletableFuture<Cached> gate = refreshInFlight.updateAndGet(prev -> (prev == null || prev.isDone()) ? candidate : prev);
            
            if (gate == candidate) {
                
                CompletableFuture.supplyAsync(this::issue, cephTokenExecutor)
                                 .thenApply(res -> {
                                    cache.set(res);
                                    return res;
                                 })
                                 .whenComplete((res, err) -> {
                                    try {
                                        if (err != null) {
                                            gate.completeExceptionally(err);
                                        } else {
                                            gate.complete(res);
                                        }
                                    }finally {
                                        refreshInFlight.compareAndSet(gate, null);
                                    }
                                 });
            }
            
            try {
                Cached updated = gate.join();
                return updated.token();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                log.error("[CephTokenManager] refresh failed, reuse cached token temporarily", cause);
                if (isStillUsable(snapshot)){
                    return snapshot.token();
                }
                throw new ExternalApiException(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        
        return snapshot.token();
    }
    
    private boolean isStillUsable(Cached c) {
        if (c == null || c.exp() == null){
            return false;
        }
        return !JwtUtils.shouldRefresh(c.exp(), storageAdminProperty.refreshThreshold());
    }
    
    private Cached issue() {
        
        log.info("[CephTokenManager] issuing new token...");
        
        var token = auth().token();
        var claims = JwtUtils.decodeAs(token, CephJwtClaims.class);
        Long exp = claims.exp();

        log.info("[CephTokenManager] token issued. exp : {}", exp);
        return new Cached(token, exp);
    }
    
    private CephAuthResponse auth(){
        var req = new CephAuthRequest(storageAdminProperty.username(), storageAdminProperty.password());
        return cephDashboardAuthClient.auth(req);
    }
}

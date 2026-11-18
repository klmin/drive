package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.global.security.*;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL/Flyway in Testcontainers only. No production account or storage is contacted. */
@SpringBootTest
@ActiveProfiles("test")
class ManagedSingleQuotaIntegrationTest {
    @Autowired SuperAdminTenantUseCase tenants;
    @Autowired SystemConfigFindService config;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean QuotaService capacity;
    @MockitoBean BaseDomainFindService domains;
    @MockitoBean StorageService storage;
    @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
    @MockitoBean com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry postprocessing;

    @BeforeEach void setup() {
        TenantContext.set(1);
        var actor=mock(CustomUserDetails.class);when(actor.getUserSeq()).thenReturn(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
        jdbc.update("UPDATE tb_tenant SET tenant_type='SYSTEM_MANAGED', user_mode='SINGLE' WHERE tenant_seq=1");
        jdbc.update("UPDATE tb_user SET total_quota_byte=100, use_quota_byte=30 WHERE user_seq=2");
        jdbc.update("UPDATE tb_system_config SET value_text='100' WHERE tenant_seq=1 AND setting_key='tenant_total_quota_byte'");
        when(capacity.canIncrease(anyLong())).thenReturn(true);
        when(domains.findByBaseDomainSeqOrThrow(1)).thenReturn(new BaseDomainData(1,"example.invalid"));
    }
    @AfterEach void clear(){TenantContext.clear();SecurityContextHolder.clearContext();}
    void update(long bytes) { tenants.update(1,new SuperAdminTenantUpdateRequest("quota-test","quota-test",1,bytes,List.of()),1); }
    long userQuota(){return jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq=2",Long.class);}
    void assertQuotas(long expected){assertThat(config.findTenantTotalQuotaByte(1)).isEqualTo(expected);assertThat(userQuota()).isEqualTo(expected);}

    @Test void increaseDecreaseRepeatAndRepairAreCommittedTogether() {
        update(200);assertQuotas(200);update(30);assertQuotas(30);update(30);assertQuotas(30);
        jdbc.update("UPDATE tb_user SET total_quota_byte=40 WHERE user_seq=2");
        update(30);assertQuotas(30);
    }
    @Test void failureAfterBothQuotaWritesRollsBackBoth() {
        when(domains.findByBaseDomainSeqOrThrow(1)).thenThrow(new IllegalStateException("fixture failure after quota writes"));
        assertThatThrownBy(()->update(200)).isInstanceOf(RuntimeException.class);
        assertQuotas(100);
    }
    @Test void belowUsageAndUnavailableCapacityPreserveBothLimits() {
        assertThatThrownBy(()->update(29)).isInstanceOf(RuntimeException.class);assertQuotas(100);
        when(capacity.canIncrease(anyLong())).thenReturn(false);
        assertThatThrownBy(()->update(200)).isInstanceOf(RuntimeException.class);assertQuotas(100);
    }
    @Test void concurrentUsageIsReadAfterAcquiringUserLock() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var writer=executor.submit(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.queryForObject("SELECT user_seq FROM tb_user WHERE user_seq=2 FOR UPDATE",Integer.class);
                jdbc.update("UPDATE tb_user SET use_quota_byte=80 WHERE user_seq=2");locked.countDown();
                try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}
                catch(InterruptedException e){throw new RuntimeException(e);} return null;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var shrink=executor.submit(()->update(50));
            assertThatThrownBy(()->shrink.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();writer.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->shrink.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertQuotas(100);
        } finally {release.countDown();executor.shutdownNow();}
    }
}

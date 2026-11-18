package com.thinkfree.drive.tenant.service;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.converter.TenantConverter;
import com.thinkfree.drive.tenant.dto.TenantSaveDto;
import com.thinkfree.drive.tenant.dto.TenantUpdateDto;
import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.repository.TenantRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 합성 도메인({@code subdomain.base})의 허용 형식은 주소 전략이 정한다 — 서비스는 전략 판정만 따르고 형식 규칙을 직접 갖지 않는다.
 * 실제 규칙은 {@code SubdomainTenantAddressingTest}/{@code PathTenantAddressingTest} 의 isAcceptableTenantDomain 에서 검증.
 * 여기서는 save/update/rebindDomain/rebindToBaseDomain 진입점이 모두 전략을 거치고, 거부 시 저장·로드·중복 조회가 일어나지 않는지,
 * 수정 경로가 쓰기 리포에서 로드한 엔티티를 변경 메서드로 고치는지(W6 — 변경 감지, 리포 update 메서드 없음)를 본다.
 */
@DisplayName("DefaultTenantService — 도메인 검증은 전략 위임, 수정은 관리 엔티티 변경")
class DefaultTenantServiceTest {

    private static final String IPV4_COMPOSED = "acme.192.168.0.10";

    private TenantRepository repository;
    private TenantConverter converter;
    private TenantFindService findService;
    private HostTenantCache cache;
    private TenantAddressingStrategy strategy;
    private DefaultTenantService service;

    @BeforeEach
    void setUp() {
        repository = mock(TenantRepository.class);
        converter = mock(TenantConverter.class);
        findService = mock(TenantFindService.class);
        cache = mock(HostTenantCache.class);
        strategy = mock(TenantAddressingStrategy.class);
        service = new DefaultTenantService(repository, converter, findService, cache, strategy);

        when(converter.toSaveTenant(any(TenantSaveDto.class)))
                .thenAnswer(inv -> {
                    TenantSaveDto dto = inv.getArgument(0);
                    return Tenant.builder().tenantSeq(100).domain(dto.getDomain()).subdomain(dto.getSubdomain()).build();
                });
    }

    private static Tenant tenant(int seq, String name, String domain, String subdomain, Integer baseDomainSeq) {
        return Tenant.builder().tenantSeq(seq).tenantName(name).domain(domain).subdomain(subdomain).baseDomainSeq(baseDomainSeq)
                .loginType(LoginType.LOCAL).build();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("전략이 허용하면 중복 조회 후 insert 하고 캐시를 비운다 (PATH 모드에서 IPv4 베이스 위 합성값이 여기로 온다)")
        void insertsWhenStrategyAccepts() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(true);
            when(findService.existsByDomain(anyString(), any())).thenReturn(false);

            int seq = service.save(TenantSaveDto.builder().tenantName("acme").domain(IPV4_COMPOSED).subdomain("acme").build());

            assertThat(seq).isEqualTo(100);
            verify(findService).existsByDomain(IPV4_COMPOSED, null);
            verify(repository).insert(any(Tenant.class));
            verify(cache).invalidateAll();
        }

        @Test
        @DisplayName("전략이 거부하면 INVALID_TENANT_DOMAIN — 중복 조회·insert·캐시 무효화 없음 (SUBDOMAIN 모드에서 IPv4 계열이 여기로 온다)")
        void rejectsWhenStrategyDeclines() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(false);

            assertThatThrownBy(() -> service.save(TenantSaveDto.builder().tenantName("acme").domain(IPV4_COMPOSED).subdomain("acme").build()))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.INVALID_TENANT_DOMAIN));
            verify(findService, never()).existsByDomain(any(), any());
            verify(repository, never()).insert(any());
            verify(cache, never()).invalidateAll();
        }

        @Test
        @DisplayName("형식은 통과했지만 이미 쓰는 도메인이면 TENANT_DOMAIN_ALREADY_EXISTS — 형식 검사가 중복 검사보다 먼저")
        void rejectsDuplicateAfterFormatCheck() {
            when(strategy.isAcceptableTenantDomain("acme.drive.thinkfree.com")).thenReturn(true);
            when(findService.existsByDomain(anyString(), any())).thenReturn(true);

            assertThatThrownBy(() -> service.save(TenantSaveDto.builder().tenantName("acme").domain("acme.drive.thinkfree.com").subdomain("acme").build()))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.TENANT_DOMAIN_ALREADY_EXISTS));
            verify(strategy).isAcceptableTenantDomain("acme.drive.thinkfree.com");
            verify(repository, never()).insert(any());
        }

        @Test
        @DisplayName("domain 이 비어 있으면(베이스 도메인 미설정 단일 테넌트) 전략·중복 검사를 건너뛰고 insert 한다")
        void skipsValidationWhenDomainBlank() {
            service.save(TenantSaveDto.builder().tenantName("solo").domain(null).build());

            verify(strategy, never()).isAcceptableTenantDomain(any());
            verify(findService, never()).existsByDomain(any(), any());
            verify(repository).insert(any(Tenant.class));
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("전략이 거부하면 INVALID_TENANT_DOMAIN — 로드·캐시 무효화 없음")
        void rejectsWhenStrategyDeclines() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(false);

            assertThatThrownBy(() -> service.update(TenantUpdateDto.builder().tenantSeq(7).tenantName("acme").domain(IPV4_COMPOSED).subdomain("acme").build()))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.INVALID_TENANT_DOMAIN));
            verify(repository, never()).findById(anyInt());
            verify(cache, never()).invalidateAll();
        }

        @Test
        @DisplayName("전략이 허용하면 자기 seq 를 제외한 중복 조회 후 로드한 엔티티의 이름·도메인을 고치고 캐시를 비운다")
        void updatesWhenStrategyAccepts() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(true);
            when(findService.existsByDomain(anyString(), any())).thenReturn(false);
            Tenant managed = tenant(7, "old", "old.test", "old", null);
            when(repository.findById(7)).thenReturn(managed);

            service.update(TenantUpdateDto.builder().tenantSeq(7).tenantName("acme").domain(IPV4_COMPOSED).subdomain("acme").baseDomainSeq(3).build());

            verify(findService).existsByDomain(IPV4_COMPOSED, 7);
            assertThat(managed.getTenantName()).isEqualTo("acme");
            assertThat(managed.getDomain()).isEqualTo(IPV4_COMPOSED);
            assertThat(managed.getSubdomain()).isEqualTo("acme");
            assertThat(managed.getBaseDomainSeq()).isEqualTo(3);
            verify(cache).invalidateAll();
        }

        @Test
        @DisplayName("없는 테넌트면 TENANT_NOT_FOUND — 캐시 무효화 없음(10-13 W6 D3: 이전엔 아무것도 안 하고 통과)")
        void notFoundWhenTenantMissing() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(true);
            when(findService.existsByDomain(any(), any())).thenReturn(false);

            assertThatThrownBy(() -> service.update(TenantUpdateDto.builder().tenantSeq(999).tenantName("acme").domain(IPV4_COMPOSED).subdomain("acme").build()))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.TENANT_NOT_FOUND));
            verify(cache, never()).invalidateAll();
        }
    }

    @Nested
    @DisplayName("rebindDomain (일반 설정 — 단일 테넌트 클라이언트 도메인)")
    class Rebind {

        @Test
        @DisplayName("전략이 거부하면 INVALID_TENANT_DOMAIN — 로드 없음, 허용하면 바인딩 변경(이름 보존) + 이전 domain 이 비었으면 true (중복 검사·캐시 무효화는 호출부 몫)")
        void followsStrategy() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(false);
            assertThatThrownBy(() -> service.rebindDomain(7, "acme", null, IPV4_COMPOSED))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.INVALID_TENANT_DOMAIN));
            verify(repository, never()).findById(anyInt());

            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(true);
            Tenant managed = tenant(7, "name", null, null, null);
            when(repository.findById(7)).thenReturn(managed);

            assertThat(service.rebindDomain(7, "acme", 2, IPV4_COMPOSED)).as("이전 domain 없음 = 새로 설정").isTrue();
            assertThat(managed.getDomain()).isEqualTo(IPV4_COMPOSED);
            assertThat(managed.getSubdomain()).isEqualTo("acme");
            assertThat(managed.getBaseDomainSeq()).isEqualTo(2);
            assertThat(managed.getTenantName()).isEqualTo("name");

            assertThat(service.rebindDomain(7, "acme", 2, IPV4_COMPOSED)).as("이미 domain 있음 = 수정").isFalse();
            verify(findService, never()).existsByDomain(any(), any());
            verify(cache, never()).invalidateAll();
        }

        @Test
        @DisplayName("없는 테넌트면 TENANT_NOT_FOUND")
        void notFound() {
            when(strategy.isAcceptableTenantDomain(IPV4_COMPOSED)).thenReturn(true);

            assertThatThrownBy(() -> service.rebindDomain(999, "acme", null, IPV4_COMPOSED))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.TENANT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("rebindToBaseDomain (베이스 도메인 등록·변경 시 일괄 재계산)")
    class RebindToBaseDomain {

        @Test
        @DisplayName("첫 등록 — 도메인 있는 SELF_MANAGED 테넌트를 로드해 기존 domain 첫 라벨을 서브도메인으로 합성, 점 없는 domain 은 루트(base 자체)")
        void firstBinding() {
            when(strategy.isAcceptableTenantDomain(anyString())).thenReturn(true);
            Tenant a = tenant(11, "a", "acme.old.com", null, null);
            Tenant root = tenant(12, "r", "localhost", null, null);
            when(repository.findByTenantTypeAndDomainIsNotNull(TenantType.SELF_MANAGED)).thenReturn(List.of(a, root));

            service.rebindToBaseDomain(5, "drive.test", true);

            assertThat(a.getSubdomain()).isEqualTo("acme");
            assertThat(a.getDomain()).isEqualTo("acme.drive.test");
            assertThat(a.getBaseDomainSeq()).isEqualTo(5);
            assertThat(root.getSubdomain()).isNull();
            assertThat(root.getDomain()).isEqualTo("drive.test");
            verify(repository, never()).findByBaseDomainSeq(anyInt());
            verify(cache, never()).invalidateAll();
        }

        @Test
        @DisplayName("변경 — 이 베이스 도메인에 묶인 테넌트만 로드해 기존 서브도메인 유지·새 베이스로 합성")
        void changeBinding() {
            when(strategy.isAcceptableTenantDomain(anyString())).thenReturn(true);
            Tenant a = tenant(11, "a", "acme.old.com", "acme", 5);
            when(repository.findByBaseDomainSeq(5)).thenReturn(List.of(a));

            service.rebindToBaseDomain(5, "new.test", false);

            assertThat(a.getSubdomain()).isEqualTo("acme");
            assertThat(a.getDomain()).isEqualTo("acme.new.test");
            assertThat(a.getTenantName()).as("이름 보존").isEqualTo("a");
            verify(repository, never()).findByTenantTypeAndDomainIsNotNull(any());
        }

        @Test
        @DisplayName("합성 결과를 전략이 거부하면 INVALID_TENANT_DOMAIN")
        void rejectsWhenStrategyDeclines() {
            when(strategy.isAcceptableTenantDomain("acme.192.168.0.10")).thenReturn(false);
            when(repository.findByBaseDomainSeq(5)).thenReturn(List.of(tenant(11, "a", "acme.old.com", "acme", 5)));

            assertThatThrownBy(() -> service.rebindToBaseDomain(5, "192.168.0.10", false))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.INVALID_TENANT_DOMAIN));
        }
    }

    @Nested
    @DisplayName("updateLoginType")
    class UpdateLoginType {

        @Test
        @DisplayName("로드한 엔티티의 login_type 변경, 없는 테넌트면 TENANT_LOGIN_TYPE_UPDATE_FAIL(기존 0행 실패와 같은 오류)")
        void changesOrFails() {
            Tenant managed = tenant(7, "n", null, null, null);
            when(repository.findById(7)).thenReturn(managed);

            service.updateLoginType(7, LoginType.OIDC);
            assertThat(managed.getLoginType()).isEqualTo(LoginType.OIDC);

            assertThatThrownBy(() -> service.updateLoginType(999, LoginType.OIDC))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.TENANT_LOGIN_TYPE_UPDATE_FAIL));
        }
    }

    @Nested
    @DisplayName("delete — 호스트 캐시는 커밋 후에만 비운다")
    class Delete {

        @BeforeEach
        void startSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        @Test
        @DisplayName("삭제 직후(커밋 전)에는 비우지 않고, 커밋되면 비운다")
        void invalidatesAfterCommit() {
            when(repository.deleteById(7)).thenReturn(1);

            service.delete(7);
            verify(cache, never()).invalidateAll();

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(cache).invalidateAll();
        }

        @Test
        @DisplayName("롤백되면 비우지 않는다")
        void keepsCacheOnRollback() {
            when(repository.deleteById(7)).thenReturn(1);

            service.delete(7);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(cache, never()).invalidateAll();
        }

        @Test
        @DisplayName("없는 테넌트면 TENANT_NOT_FOUND, 커밋 후 작업도 등록하지 않는다")
        void notFound() {
            when(repository.deleteById(999)).thenReturn(0);

            assertThatThrownBy(() -> service.delete(999))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(TenantErrorCode.TENANT_NOT_FOUND));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }
    }
}

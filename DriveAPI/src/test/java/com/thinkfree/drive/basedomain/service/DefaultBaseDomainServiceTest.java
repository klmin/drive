package com.thinkfree.drive.basedomain.service;

import com.thinkfree.drive.basedomain.converter.BaseDomainConverter;
import com.thinkfree.drive.basedomain.entity.BaseDomain;
import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.basedomain.repository.BaseDomainRepository;
import com.thinkfree.drive.basedomain.result.BaseDomainUpsertResult;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 입력값 모양({@code [scheme://]host[:port]})은 {@code BaseDomainAddress} 가, 호스트 종류는 주소 전략이 정한다 —
 * 서비스는 둘을 차례로 적용하고 호스트·스킴·포트로 나눠 저장한다.
 * 호스트 규칙은 {@code SubdomainTenantAddressingTest}/{@code PathTenantAddressingTest}, 모양 규칙은 {@code BaseDomainAddressTest} 에서 검증.
 */
@DisplayName("DefaultBaseDomainService.upsert — 파싱 후 호스트를 전략의 isAcceptableBaseDomain 판정에 위임")
class DefaultBaseDomainServiceTest {

    private BaseDomainConverter converter;
    private BaseDomainRepository repository;
    private TenantAddressingStrategy strategy;
    private DefaultBaseDomainService service;

    @BeforeEach
    void setUp() {
        converter = mock(BaseDomainConverter.class);
        repository = mock(BaseDomainRepository.class);
        strategy = mock(TenantAddressingStrategy.class);
        service = new DefaultBaseDomainService(converter, repository, strategy);
        when(converter.toUpsertBaseDomain(anyString(), any(), any(), anyInt()))
                .thenAnswer(inv -> BaseDomain.builder()
                        .baseDomain(inv.getArgument(0)).scheme(inv.getArgument(1)).port(inv.getArgument(2))
                        .baseDomainSeq(inv.getArgument(3)).build());
    }

    @Test
    @DisplayName("전략이 허용하면 고정 seq 로 upsert 하고 그 seq·호스트를 결과로 돌려준다 (PATH 모드에서 IPv4 가 여기로 온다)")
    void upsertsWhenStrategyAccepts() {
        when(strategy.isAcceptableBaseDomain("192.168.0.10")).thenReturn(true);

        BaseDomainUpsertResult saved = service.upsert("192.168.0.10");

        assertThat(saved.baseDomain()).isEqualTo("192.168.0.10");
        assertThat(saved.baseDomainSeq()).isEqualTo(DefaultBaseDomainService.SINGLE_BASE_DOMAIN_SEQ);
        verify(repository).upsert(argThat((BaseDomain e) -> e.getBaseDomainSeq().equals(saved.baseDomainSeq())
                && e.getBaseDomain().equals(saved.baseDomain()) && e.getScheme() == null && e.getPort() == null));
    }

    @Test
    @DisplayName("스킴·포트가 붙은 입력 — 전략에는 호스트만 넘기고, 호스트·스킴·포트로 나눠 저장한다")
    void splitsSchemeHostPort() {
        when(strategy.isAcceptableBaseDomain("192.168.0.10")).thenReturn(true);

        BaseDomainUpsertResult saved = service.upsert("HTTP://192.168.0.10:8080/");

        assertThat(saved.baseDomain()).isEqualTo("192.168.0.10");
        verify(converter).toUpsertBaseDomain(eq("192.168.0.10"), eq("http"), eq(8080), eq(DefaultBaseDomainService.SINGLE_BASE_DOMAIN_SEQ));
        verify(strategy).isAcceptableBaseDomain("192.168.0.10");
    }

    @Test
    @DisplayName("스킴 기본 포트(https:443)는 저장하지 않는다")
    void dropsDefaultPort() {
        when(strategy.isAcceptableBaseDomain("drive.example.com")).thenReturn(true);

        service.upsert("https://drive.example.com:443");

        verify(converter).toUpsertBaseDomain(eq("drive.example.com"), eq("https"), isNull(), anyInt());
    }

    @Test
    @DisplayName("전략이 거부하면 INVALID_BASE_DOMAIN 예외, 저장 없음 (SUBDOMAIN 모드에서 IPv4 가 여기로 온다)")
    void rejectsWhenStrategyDeclines() {
        when(strategy.isAcceptableBaseDomain("192.168.0.10")).thenReturn(false);

        assertThatThrownBy(() -> service.upsert("192.168.0.10:8080"))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(BaseDomainErrorCode.INVALID_BASE_DOMAIN));
        verify(repository, never()).upsert(any());
        verify(converter, never()).toUpsertBaseDomain(anyString(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("모양이 틀리면(경로 포함) 전략을 부르지 않고 INVALID_BASE_DOMAIN")
    void rejectsMalformedBeforeStrategy() {
        assertThatThrownBy(() -> service.upsert("https://drive.example.com/app"))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(BaseDomainErrorCode.INVALID_BASE_DOMAIN));
        verify(strategy, never()).isAcceptableBaseDomain(anyString());
        verify(repository, never()).upsert(any());
    }
}

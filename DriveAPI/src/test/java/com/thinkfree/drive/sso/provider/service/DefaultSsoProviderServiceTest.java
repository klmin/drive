package com.thinkfree.drive.sso.provider.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.registry.SsoProtocolRegistry;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolService;
import com.thinkfree.drive.sso.provider.converter.SsoProviderConverter;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.dto.SsoProviderUpsertDto;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.exception.SsoProviderErrorCode;
import com.thinkfree.drive.sso.provider.repository.SsoProviderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * upsert(sync) 경로의 사전 검사.
 * <ul>
 *   <li>표시 이름 중복(uniq_sso_provider_scope_tenant_name 과 같은 키)이 DB 쓰기 전에 걸리는지 — MySQL 때는 ON DUPLICATE KEY 가 이 유니크에도 반응해
 *       신규 등록이 기존 공급자를 덮어썼고, PG 는 유니크 위반 예외가 나던 것을 SSO_PROVIDER_007 로 사전 차단한다.</li>
 *   <li>기존 공급자 소유 판정(W4) — PK 단건 DTO({@code findById}) + Java 판정. 다른 테넌트·다른 범위는 "없음" 과 같은 SSO_PROVIDER_NOT_FOUND.</li>
 *   <li>upsert 가 반환한 PK 로 프로토콜 설정을 upsert(W4 — 엔티티 write-back 없음).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultSsoProviderService — upsert 사전 검사(표시 이름 중복·소유 판정)")
class DefaultSsoProviderServiceTest {

    private static final int TENANT = 1;
    private static final int SEQ = 10;
    private static final String NAME = "Okta";

    @Mock
    private SsoProviderConverter converter;
    @Mock
    private SsoProviderRepository repository;
    @Mock
    private SsoProtocolRegistry registry;
    @Mock
    private SsoProviderFindService findService;
    @Mock
    private SsoProtocolService protocolService;

    @InjectMocks
    private DefaultSsoProviderService service;

    private static void assertCode(Throwable ex, SsoProviderErrorCode code) {
        assertThat(ex).isInstanceOf(ApiRuntimeException.class);
        assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(code);
    }

    private static SsoProviderUpsertDto.SsoProviderUpsertDtoBuilder existingJwt() {
        return SsoProviderUpsertDto.builder()
                .ssoProviderSeq(SEQ).tenantSeq(TENANT).displayName(NAME)
                .protocolType(SsoProviderProtocolType.JWT).scope(SsoProviderScope.TENANT);
    }

    private static SsoProviderData row(SsoProviderScope scope, Integer tenantSeq) {
        return SsoProviderData.builder().ssoProviderSeq(SEQ).scope(scope).tenantSeq(tenantSeq).build();
    }

    @Test
    @DisplayName("upsert(syncJwt) — 기존 공급자 수정 시 자기 seq 를 제외하고 검사, 중복이면 upsert 미실행")
    void upsert_existingProvider_rejectsDuplicateExcludingSelf() {
        var dto = existingJwt().build();
        when(findService.findById(SEQ)).thenReturn(row(SsoProviderScope.TENANT, TENANT));
        when(findService.existsDisplayName(SsoProviderScope.TENANT, TENANT, NAME, SEQ)).thenReturn(true);

        assertThatThrownBy(() -> service.syncJwt(dto))
                .satisfies(ex -> assertCode(ex, SsoProviderErrorCode.DUPLICATE_SSO_PROVIDER_DISPLAY_NAME));

        verify(repository, never()).upsert(any());
        verifyNoInteractions(registry, converter);
    }

    @Test
    @DisplayName("upsert(syncJwt) — 기존 공급자가 다른 테넌트 소유면 SSO_PROVIDER_NOT_FOUND, 이후 검사·upsert 미실행")
    void upsert_existingProvider_otherTenant_notFound() {
        var dto = existingJwt().build();
        when(findService.findById(SEQ)).thenReturn(row(SsoProviderScope.TENANT, TENANT + 1));

        assertThatThrownBy(() -> service.syncJwt(dto))
                .satisfies(ex -> assertCode(ex, SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND));

        verify(findService, never()).existsDisplayName(any(), any(), any(), any());
        verify(repository, never()).upsert(any());
        verifyNoInteractions(registry, converter);
    }

    @Test
    @DisplayName("upsert(syncGlobalJwt) — GLOBAL 요청인데 행이 TENANT 범위면 SSO_PROVIDER_NOT_FOUND")
    void upsert_globalRequest_tenantRow_notFound() {
        var dto = existingJwt().tenantSeq(null).scope(SsoProviderScope.GLOBAL).build();
        when(findService.findById(SEQ)).thenReturn(row(SsoProviderScope.TENANT, TENANT));

        assertThatThrownBy(() -> service.syncGlobalJwt(dto))
                .satisfies(ex -> assertCode(ex, SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND));

        verify(repository, never()).upsert(any());
    }

    @Test
    @DisplayName("upsert(syncGlobalJwt) — GLOBAL 행이면 소유 통과 후 upsert, 반환 PK 로 프로토콜 설정 upsert")
    void upsert_globalRow_passes() {
        var dto = existingJwt().tenantSeq(null).scope(SsoProviderScope.GLOBAL).build();
        var entity = SsoProvider.builder().build();
        when(findService.findById(SEQ)).thenReturn(row(SsoProviderScope.GLOBAL, null));
        when(findService.existsDisplayName(SsoProviderScope.GLOBAL, null, NAME, SEQ)).thenReturn(false);
        when(converter.toUpsertSsoProvider(dto)).thenReturn(entity);
        when(repository.upsert(entity)).thenReturn(SEQ);
        when(registry.get(SsoProviderProtocolType.JWT)).thenReturn(protocolService);

        service.syncGlobalJwt(dto);

        verify(protocolService).upsert(eq(SEQ), isNull());
    }

    @Test
    @DisplayName("upsert(syncJwt) — 신규(seq null)는 소유 판정 없이 제외 없이 검사하고, 유일하면 upsert 가 반환한 PK 로 진행한다")
    void upsert_newProvider_passesWhenUnique() {
        var dto = existingJwt().ssoProviderSeq(null).build();
        var entity = SsoProvider.builder().build();
        when(findService.existsActiveSingleType(TENANT, SsoProviderProtocolType.JWT)).thenReturn(false);
        when(findService.existsDisplayName(SsoProviderScope.TENANT, TENANT, NAME, null)).thenReturn(false);
        when(converter.toUpsertSsoProvider(dto)).thenReturn(entity);
        when(repository.upsert(entity)).thenReturn(SEQ);
        when(registry.get(SsoProviderProtocolType.JWT)).thenReturn(protocolService);

        service.syncJwt(dto);

        verify(findService, never()).findById(anyInt());
        verify(repository).upsert(entity);
        verify(protocolService).upsert(eq(SEQ), isNull());
    }
}

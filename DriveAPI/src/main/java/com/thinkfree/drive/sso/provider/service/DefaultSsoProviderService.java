package com.thinkfree.drive.sso.provider.service;

import java.util.stream.Stream;
import com.thinkfree.drive.sso.protocol.registry.SsoProtocolRegistry;
import com.thinkfree.drive.sso.provider.converter.SsoProviderConverter;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.dto.SsoProviderUpsertDto;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.exception.SsoProviderErrorCode;
import com.thinkfree.drive.sso.provider.repository.SsoProviderRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultSsoProviderService implements SsoProviderService{

    private final SsoProviderConverter ssoProviderConverter;
    private final SsoProviderRepository ssoProviderRepository;
    private final SsoProtocolRegistry ssoProtocolRegistry;
    private final SsoProviderFindService ssoProviderFindService;

    @Override
    public void syncJwt(SsoProviderUpsertDto dto) {
        upsertProviderAndConfig(dto);
    }

    @Override
    public void syncOidcList(int tenantSeq, List<SsoProviderUpsertDto> dtoList) {

        var listQuery = ssoProviderConverter.toSsoProviderListQuery(tenantSeq, SsoProviderProtocolType.OIDC);
        Set<Integer> existingSeqList = ssoProviderFindService.findAll(listQuery).stream()
                                                             .map(SsoProviderData::getSsoProviderSeq)
                                                             .collect(Collectors.toSet());

        syncProvidersAndDeleteRemoved(existingSeqList, dtoList);
    }

    @Override
    public void syncGlobalJwt(SsoProviderUpsertDto dto) {
        upsertProviderAndConfig(dto);
    }

    @Override
    public void syncGlobalOidcList(List<SsoProviderUpsertDto> dtoList) {

        Set<Integer> existingSeqList = ssoProviderFindService.findAllGlobalDetailsByProtocol(SsoProviderProtocolType.OIDC).stream()
                                                             .map(detail -> detail.getSsoProviderData().getSsoProviderSeq())
                                                             .collect(Collectors.toSet());

        syncProvidersAndDeleteRemoved(existingSeqList, dtoList);
    }

    private void syncProvidersAndDeleteRemoved(Set<Integer> existingSeqList, List<SsoProviderUpsertDto> dtoList) {

        Set<Integer> incomingSeqList = dtoList.stream()
                                              .map(SsoProviderUpsertDto::getSsoProviderSeq)
                                              .filter(Objects::nonNull)
                                              .collect(Collectors.toSet());

        List<Integer> seqListToDelete = existingSeqList.stream()
                                                       .filter(seq -> !incomingSeqList.contains(seq))
                                                       .toList();
        if (!seqListToDelete.isEmpty()) {
            deleteBySeqList(seqListToDelete);
        }

        for (var dto : dtoList) {
            upsertProviderAndConfig(dto);
        }
    }

    private void upsertProviderAndConfig(SsoProviderUpsertDto dto) {

        if (dto.getSsoProviderSeq() != null) {
            requireOwned(dto.getScope(), dto.getSsoProviderSeq(), dto.getTenantSeq());
        }

        if (SsoProviderProtocolType.JWT == dto.getProtocolType() && dto.getSsoProviderSeq() == null) {
            boolean isActiveProtocol = isActiveSingleType(dto.getScope(), dto.getTenantSeq(), dto.getProtocolType());
            DriveAssert.throwIfTrue(isActiveProtocol, SsoProviderErrorCode.ACTIVE_SSO_PROVIDER_ALREADY_EXISTS);
        }
        validateDisplayNameUniqueness(dto.getScope(), dto.getTenantSeq(), dto.getDisplayName(), dto.getSsoProviderSeq());

        var entity = ssoProviderConverter.toUpsertSsoProvider(dto);
        Integer ssoProviderSeq = ssoProviderRepository.upsert(entity);

        var ssoProtocolService = ssoProtocolRegistry.get(dto.getProtocolType());
        ssoProtocolService.upsert(ssoProviderSeq, dto.getProtocolUpsertDto());
    }

    /**
     * 같은 범위(scope + tenant) 안의 표시 이름 중복을 저장 전에 거른다.
     * DB 유니크 uniq_sso_provider_scope_tenant_name 과 같은 키. MySQL 때는 ON DUPLICATE KEY 가 이 유니크에도 반응해
     * 신규 등록이 기존 공급자를 덮어썼고, PG 는 ON CONFLICT (sso_provider_seq) 만 잡으므로 유니크 위반 예외가 났다 → 양쪽 모두 사전 차단.
     * excludeSsoProviderSeq = 수정 대상 자신(신규는 null).
     */
    private void validateDisplayNameUniqueness(SsoProviderScope scope, Integer tenantSeq, String displayName, Integer excludeSsoProviderSeq) {
        boolean duplicated = ssoProviderFindService.existsDisplayName(scope, tenantSeq, displayName, excludeSsoProviderSeq);
        DriveAssert.throwIfTrue(duplicated, SsoProviderErrorCode.DUPLICATE_SSO_PROVIDER_DISPLAY_NAME);
    }

    private boolean isActiveSingleType(SsoProviderScope scope, Integer tenantSeq, SsoProviderProtocolType protocolType) {
        if (scope == SsoProviderScope.GLOBAL) {
            return ssoProviderFindService.existsActiveSingleTypeByGlobal(protocolType);
        }
        return ssoProviderFindService.existsActiveSingleType(tenantSeq, protocolType);
    }

    /**
     * 기존 공급자 수정 시 범위·테넌트 소유 판정 — PK 단건 DTO + Java 판정(W4, 구 existsByIdAndTenant/Global).
     * 없는 행·다른 범위·다른 테넌트 모두 SSO_PROVIDER_NOT_FOUND(findById 가 없음을 같은 코드로 던진다).
     */
    private void requireOwned(SsoProviderScope scope, int ssoProviderSeq, Integer tenantSeq) {
        SsoProviderData provider = ssoProviderFindService.findById(ssoProviderSeq);
        boolean owned = scope == SsoProviderScope.GLOBAL
                ? provider.getScope() == SsoProviderScope.GLOBAL
                : provider.getScope() == SsoProviderScope.TENANT && tenantSeq != null && tenantSeq.equals(provider.getTenantSeq());
        DriveAssert.throwIfFalse(owned, SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND);
    }

    private void deleteBySeqList(List<Integer> ssoProviderSeqList) {
        var ssoProtocolService = ssoProtocolRegistry.get(SsoProviderProtocolType.OIDC);
        ssoProtocolService.deleteByProviders(ssoProviderSeqList);
        ssoProviderRepository.deleteBySeqList(ssoProviderSeqList);
    }
    /** 테넌트 삭제 — 테넌트 범위 공급자(OIDC·JWT)와 프로토콜 설정. 전역(GLOBAL) 공급자는 tenant_seq 가 없어 대상이 아니다 */
    @Override
    public void deleteByTenant(Integer tenantSeq) {
        List<Integer> seqList = Stream.of(SsoProviderProtocolType.OIDC, SsoProviderProtocolType.JWT)
                .flatMap(protocol -> ssoProviderFindService.findAllDetailsByTenantAndProtocol(tenantSeq, protocol).stream())
                .map(detail -> detail.getSsoProviderData().getSsoProviderSeq())
                .toList();
        if (seqList.isEmpty()) {
            return;
        }
        // 프로토콜 설정은 공급자 seq 로만 찾는다(공급자 행보다 먼저, FK 없음) — JWT·OIDC 둘 다
        ssoProtocolRegistry.get(SsoProviderProtocolType.JWT).deleteByProviders(seqList);
        ssoProtocolRegistry.get(SsoProviderProtocolType.OIDC).deleteByProviders(seqList);
        ssoProviderRepository.deleteBySeqList(seqList);
    }
}

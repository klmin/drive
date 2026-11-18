package com.thinkfree.drive.sso.protocol.oidc.service;

import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import com.thinkfree.drive.sso.protocol.oidc.converter.OidcConfigConverter;
import com.thinkfree.drive.sso.protocol.oidc.dto.SsoOidcConfigDto;
import com.thinkfree.drive.sso.protocol.oidc.repository.SsoOidcConfigRepository;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolService;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SsoOidcConfigService implements SsoProtocolService {
    
    private final OidcConfigConverter oidcConfigConverter;
    private final SsoOidcConfigRepository ssoOidcConfigRepository;
    
    @Override
    public SsoProviderProtocolType supports() {
        return SsoProviderProtocolType.OIDC;
    }
    
    @Override
    public void upsert(int ssoProviderSeq, SsoProtocolUpsertDto dto) {
        var entity = oidcConfigConverter.toUpsertOidcConfig((SsoOidcConfigDto.Upsert) dto, ssoProviderSeq);
        ssoOidcConfigRepository.upsert(entity);
    }
    
    @Override
    public void deleteByProviders(List<Integer> ssoProviderSeqList) {
        ssoOidcConfigRepository.deleteByProviders(ssoProviderSeqList);
    }
    
    
}

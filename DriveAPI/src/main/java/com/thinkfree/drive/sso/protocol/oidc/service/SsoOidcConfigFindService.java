package com.thinkfree.drive.sso.protocol.oidc.service;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.oidc.converter.OidcConfigConverter;
import com.thinkfree.drive.sso.protocol.oidc.exception.SsoProtocolOidcErrorCode;
import com.thinkfree.drive.sso.protocol.oidc.repository.SsoOidcConfigQueryRepository;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolFindService;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SsoOidcConfigFindService implements SsoProtocolFindService {
    
    private final OidcConfigConverter oidcConfigConverter;
    private final SsoOidcConfigQueryRepository ssoOidcConfigQueryRepository;

    @Override
    public SsoProviderProtocolType supports() {
        return SsoProviderProtocolType.OIDC;
    }

    @Override
    public SsoProtocolData findById(SsoProtocolFindQuery query) {
        var data = ssoOidcConfigQueryRepository.findById(query);
        DriveAssert.throwIfNull(data, SsoProtocolOidcErrorCode.OIDC_CONFIG_NOT_FOUND);
        return data;
    }

    @Override
    public List<SsoProtocolData> findAllByProviderSeqList(SsoProtocolBySeqListQuery query) {
        return ssoOidcConfigQueryRepository.findAllByProviderSeqList(query)
                .stream()
                .map(SsoProtocolData.class::cast)
                .toList();
    }
    
    @Override
    public SsoLoginConfigData findLoginConfig(SsoProtocolFindQuery query) {
        var data = ssoOidcConfigQueryRepository.findById(query);
        return oidcConfigConverter.toSsoLoginConfigData(data.getSsoProviderSeq(), null);
    }
}

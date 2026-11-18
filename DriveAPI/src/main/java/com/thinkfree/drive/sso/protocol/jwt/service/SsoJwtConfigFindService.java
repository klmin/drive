package com.thinkfree.drive.sso.protocol.jwt.service;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.jwt.converter.JwtConfigConverter;
import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.jwt.exception.SsoProtocolJwtErrorCode;
import com.thinkfree.drive.sso.protocol.jwt.repository.SsoJwtConfigQueryRepository;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolFindService;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SsoJwtConfigFindService implements SsoProtocolFindService {
    
    private final JwtConfigConverter jwtConfigConverter;
    private final SsoJwtConfigQueryRepository ssoJwtConfigQueryRepository;

    @Override
    public SsoProviderProtocolType supports() {
        return SsoProviderProtocolType.JWT;
    }

    @Override
    public SsoProtocolData findById(SsoProtocolFindQuery query) {
        var data = ssoJwtConfigQueryRepository.findById(query);
        DriveAssert.throwIfNull(data, SsoProtocolJwtErrorCode.JWT_CONFIG_NOT_FOUND);
        return data;
    }

    @Override
    public List<SsoProtocolData> findAllByProviderSeqList(SsoProtocolBySeqListQuery query) {
        return ssoJwtConfigQueryRepository.findAllByProviderSeqList(query)
                .stream()
                .map(SsoProtocolData.class::cast)
                .toList();
    }

    
    public SsoJwtConfigData findByIssuer(String issuer) {
        return ssoJwtConfigQueryRepository.findByIssuer(issuer);
    }

    public SsoJwtConfigData findByIssuerAndScope(String issuer, SsoProviderScope scope) {
        return ssoJwtConfigQueryRepository.findByIssuerAndScope(issuer, scope);
    }
    
    @Override
    public SsoLoginConfigData findLoginConfig(SsoProtocolFindQuery query) {
        var data = ssoJwtConfigQueryRepository.findById(query);
        return jwtConfigConverter.toSsoLoginConfigData(data.getSsoProviderSeq(), data.getErrorRedirectUrl());
    }
}

package com.thinkfree.drive.sso.protocol.jwt.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import com.thinkfree.drive.sso.protocol.jwt.converter.JwtConfigConverter;
import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.jwt.dto.SsoJwtConfigDto;
import com.thinkfree.drive.sso.protocol.jwt.exception.SsoProtocolJwtErrorCode;
import com.thinkfree.drive.sso.protocol.jwt.repository.SsoJwtConfigRepository;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolService;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SsoJwtConfigService implements SsoProtocolService {

    private final JwtConfigConverter jwtConfigConverter;
    private final SsoJwtConfigRepository ssoJwtConfigRepository;
    private final SsoJwtConfigFindService ssoJwtConfigFindService;

    @Override
    public SsoProviderProtocolType supports() {
        return SsoProviderProtocolType.JWT;
    }

    @Override
    public void upsert(int ssoProviderSeq, SsoProtocolUpsertDto dto) {
        var upsertDto = (SsoJwtConfigDto.Upsert) dto;
        validateIssuerUniqueness(upsertDto.getIssuer(), ssoProviderSeq);
        var entity = jwtConfigConverter.toUpsertJwtConfig(upsertDto, ssoProviderSeq);
        ssoJwtConfigRepository.upsert(entity);
    }

    @Override
    public void deleteByProviders(List<Integer> ssoProviderSeqList) {
        ssoJwtConfigRepository.deleteByProviders(ssoProviderSeqList);
    }

    private void validateIssuerUniqueness(String issuer, int ssoProviderSeq) {
        SsoJwtConfigData existing = ssoJwtConfigFindService.findByIssuer(issuer);
        if (existing != null && existing.getSsoProviderSeq() != ssoProviderSeq) {
            throw ApiRuntimeException.type(SsoProtocolJwtErrorCode.DUPLICATE_ISSUER);
        }
    }
}

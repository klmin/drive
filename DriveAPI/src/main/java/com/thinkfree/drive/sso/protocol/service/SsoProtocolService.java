package com.thinkfree.drive.sso.protocol.service;

import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;

import java.util.List;

public interface SsoProtocolService {
    SsoProviderProtocolType supports();
    void upsert(int ssoProviderSeq, SsoProtocolUpsertDto dto);

    void deleteByProviders(List<Integer> ssoProviderSeqList);
}

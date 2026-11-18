package com.thinkfree.drive.sso.provider.service;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.query.SsoProviderListQuery;

import java.util.List;

public interface SsoProviderFindService {
    List<SsoProviderData> findAll(SsoProviderListQuery query);
    SsoProviderData findById(int ssoProviderSeq);
    SsoProviderDetailData findDetailById(int ssoProviderSeq);
    List<SsoProviderDetailData> findAllDetailsByTenantAndProtocol(int tenantSeq, SsoProviderProtocolType protocolType);
    List<SsoProviderDetailData> findAllGlobalDetailsByProtocol(SsoProviderProtocolType protocolType);
    boolean existsActiveSingleType(int tenantSeq, SsoProviderProtocolType protocolType);
    boolean existsActiveSingleTypeByGlobal(SsoProviderProtocolType protocolType);
    boolean existsDisplayName(SsoProviderScope scope, Integer tenantSeq, String displayName, Integer excludeSsoProviderSeq);
    SsoLoginConfigData findActiveByTenantAndProtocolOrThrow(int tenantSeq, SsoProviderProtocolType protocolType);
    SsoLoginConfigData findActiveByGlobalAndProtocolOrThrow(SsoProviderProtocolType protocolType);
    SsoLoginConfigData findLoginConfigByProtocolOrNull(int tenantSeq, SsoProviderProtocolType protocolType);
    SsoLoginConfigData findLoginConfigByGlobalAndProtocolOrNull(SsoProviderProtocolType protocolType);
}

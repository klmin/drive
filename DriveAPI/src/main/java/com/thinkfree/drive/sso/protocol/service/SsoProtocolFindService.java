package com.thinkfree.drive.sso.protocol.service;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;

import java.util.List;

public interface SsoProtocolFindService {
    SsoProviderProtocolType supports();
    SsoProtocolData findById(SsoProtocolFindQuery query);
    List<SsoProtocolData> findAllByProviderSeqList(SsoProtocolBySeqListQuery query);
    SsoLoginConfigData findLoginConfig(SsoProtocolFindQuery query);
}

package com.thinkfree.drive.sso.protocol.oidc.repository;

import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;

import java.util.List;

/**
 * tb_sso_oidc_config 읽기 — {@code SsoOidcConfigFindService} 가 주입. 쓰기는 {@link SsoOidcConfigRepository}.
 */
public interface SsoOidcConfigQueryRepository {

    SsoOidcConfigData findById(SsoProtocolFindQuery query);

    List<SsoOidcConfigData> findAllByProviderSeqList(SsoProtocolBySeqListQuery query);
}

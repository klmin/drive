package com.thinkfree.drive.sso.protocol.oidc.repository;

import com.thinkfree.drive.sso.protocol.oidc.entity.SsoOidcConfig;

import java.util.List;

/**
 * tb_sso_oidc_config 쓰기 — {@code SsoOidcConfigService} 가 주입. 읽기는 {@link SsoOidcConfigQueryRepository}.
 */
public interface SsoOidcConfigRepository {

    int upsert(SsoOidcConfig entity);

    int deleteByProviders(List<Integer> ssoProviderSeqList);
}

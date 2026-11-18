package com.thinkfree.drive.sso.protocol.jwt.repository;

import com.thinkfree.drive.sso.protocol.jwt.entity.SsoJwtConfig;

import java.util.List;

/**
 * tb_sso_jwt_config 쓰기 — {@code SsoJwtConfigService} 가 주입. 읽기는 {@link SsoJwtConfigQueryRepository}.
 */
public interface SsoJwtConfigRepository {

    int upsert(SsoJwtConfig entity);

    int deleteByProviders(List<Integer> ssoProviderSeqList);
}

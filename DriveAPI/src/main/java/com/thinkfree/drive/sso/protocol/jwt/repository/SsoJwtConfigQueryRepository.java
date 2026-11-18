package com.thinkfree.drive.sso.protocol.jwt.repository;

import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;

import java.util.List;

/**
 * tb_sso_jwt_config 읽기 — {@code SsoJwtConfigFindService} 가 주입. 쓰기는 {@link SsoJwtConfigRepository}.
 */
public interface SsoJwtConfigQueryRepository {

    SsoJwtConfigData findById(SsoProtocolFindQuery query);

    SsoJwtConfigData findByIssuer(String issuer);

    SsoJwtConfigData findByIssuerAndScope(String issuer, SsoProviderScope scope);

    List<SsoJwtConfigData> findAllByProviderSeqList(SsoProtocolBySeqListQuery query);
}

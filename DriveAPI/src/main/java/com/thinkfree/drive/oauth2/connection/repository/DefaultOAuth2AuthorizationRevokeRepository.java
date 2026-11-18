package com.thinkfree.drive.oauth2.connection.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.thinkfree.drive.jooq.Tables.OAUTH2_AUTHORIZATION;
import static com.thinkfree.drive.jooq.Tables.OAUTH2_AUTHORIZATION_CONSENT;

/** SAS 소유 테이블(oauth2_authorization·consent)의 연결 해제 삭제 — SAS 서비스에 (principal, client) 벌크 삭제 API 가 없어 jOOQ 로 직접 DELETE. */
@Repository
@RequiredArgsConstructor
public class DefaultOAuth2AuthorizationRevokeRepository implements OAuth2AuthorizationRevokeRepository {

    private final DSLContext dsl;

    @Override
    public int deleteAuthorizations(String principalName, String registeredClientId) {
        return dsl.deleteFrom(OAUTH2_AUTHORIZATION)
                .where(OAUTH2_AUTHORIZATION.PRINCIPAL_NAME.eq(principalName))
                .and(OAUTH2_AUTHORIZATION.REGISTERED_CLIENT_ID.eq(registeredClientId))
                .execute();
    }

    @Override
    public int deleteConsents(String principalName, String registeredClientId) {
        return dsl.deleteFrom(OAUTH2_AUTHORIZATION_CONSENT)
                .where(OAUTH2_AUTHORIZATION_CONSENT.PRINCIPAL_NAME.eq(principalName))
                .and(OAUTH2_AUTHORIZATION_CONSENT.REGISTERED_CLIENT_ID.eq(registeredClientId))
                .execute();
    }

    @Override
    public int deleteAllByPrincipals(List<String> principalNames) {
        if (principalNames == null || principalNames.isEmpty()) {
            return 0;
        }
        return dsl.deleteFrom(OAUTH2_AUTHORIZATION).where(OAUTH2_AUTHORIZATION.PRINCIPAL_NAME.in(principalNames)).execute()
                + dsl.deleteFrom(OAUTH2_AUTHORIZATION_CONSENT).where(OAUTH2_AUTHORIZATION_CONSENT.PRINCIPAL_NAME.in(principalNames)).execute();
    }
}

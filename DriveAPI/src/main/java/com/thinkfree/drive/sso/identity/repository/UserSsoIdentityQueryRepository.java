package com.thinkfree.drive.sso.identity.repository;

import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.query.UserSsoIdentityFindQuery;

/**
 * tb_user_sso_identity 읽기 — {@code DefaultUserSsoIdentityFindService}(SSO JWT·OIDC 로그인의 identity 조회) 가 주입. 쓰기는 {@link UserSsoIdentityRepository}.
 */
public interface UserSsoIdentityQueryRepository {

    /** (ssoProviderSeq, tenantSeq, subject) 의 identity + 사용자 상태(tenant·status·mfa·signupType), 없으면 null */
    UserSsoIdentityData findWithUser(UserSsoIdentityFindQuery query);
}

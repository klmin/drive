package com.thinkfree.drive.sso.identity.service;

import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;

public interface UserSsoIdentityFindService {
    
    /** 같은 Provider·테넌트·subject 의 연결 — GLOBAL Provider 는 테넌트마다 연결이 따로라 테넌트로 좁힌다 */
    UserSsoIdentityData findWithUser(int ssoProviderSeq, int tenantSeq, String subject);
}

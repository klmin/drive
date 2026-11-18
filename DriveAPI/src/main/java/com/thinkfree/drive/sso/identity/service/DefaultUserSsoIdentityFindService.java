package com.thinkfree.drive.sso.identity.service;

import com.thinkfree.drive.sso.identity.converter.UserSsoIdentityConverter;
import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.repository.UserSsoIdentityQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultUserSsoIdentityFindService implements UserSsoIdentityFindService {
    
    private final UserSsoIdentityConverter userSsoIdentityConverter;
    private final UserSsoIdentityQueryRepository userSsoIdentityQueryRepository;

    @Override
    public UserSsoIdentityData findWithUser(int ssoProviderSeq, int tenantSeq, String subject) {
        var query = userSsoIdentityConverter.toSsoIdentityFindQuery(ssoProviderSeq, tenantSeq, subject);
        return userSsoIdentityQueryRepository.findWithUser(query);
    }
}

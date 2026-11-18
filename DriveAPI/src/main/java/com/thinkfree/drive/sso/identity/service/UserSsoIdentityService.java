package com.thinkfree.drive.sso.identity.service;

import com.thinkfree.drive.sso.identity.dto.UserSsoIdentitySaveDto;

import java.time.Instant;
import java.util.List;

public interface UserSsoIdentityService {

    void save(UserSsoIdentitySaveDto dto);
    void updateLastCallbackAt(long ssoUserIdentitySeq, Instant lastCallbackAt);
    void deleteByUsers(List<Integer> userSeqList);
}

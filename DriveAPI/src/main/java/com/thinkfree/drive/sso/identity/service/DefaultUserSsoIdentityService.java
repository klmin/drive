package com.thinkfree.drive.sso.identity.service;

import com.thinkfree.drive.sso.identity.converter.UserSsoIdentityConverter;
import com.thinkfree.drive.sso.identity.dto.UserSsoIdentitySaveDto;
import com.thinkfree.drive.sso.identity.repository.UserSsoIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUserSsoIdentityService implements UserSsoIdentityService {
    
    private final UserSsoIdentityConverter userSsoIdentityConverter;
    private final UserSsoIdentityRepository userSsoIdentityRepository;

    @Override
    public void save(UserSsoIdentitySaveDto dto) {
        var entity = userSsoIdentityConverter.toSaveUserSsoIdentity(dto);
        userSsoIdentityRepository.save(entity);
    }

    @Override
    public void updateLastCallbackAt(long ssoUserIdentitySeq, Instant lastCallbackAt) {
        userSsoIdentityRepository.updateLastCallbackAt(ssoUserIdentitySeq, lastCallbackAt);
    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        var result = userSsoIdentityRepository.deleteByUserSeqList(userSeqList);
        log.info("[userIdentity 삭제 : {}건]", result);
    }
}

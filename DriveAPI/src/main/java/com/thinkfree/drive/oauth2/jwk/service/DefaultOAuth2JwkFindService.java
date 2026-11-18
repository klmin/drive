package com.thinkfree.drive.oauth2.jwk.service;

import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;
import com.thinkfree.drive.oauth2.jwk.repository.OAuth2JwkQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultOAuth2JwkFindService implements OAuth2JwkFindService {

    private final OAuth2JwkQueryRepository oauth2JwkQueryRepository;

    @Override
    public List<OAuth2JwkData> findAllValid() {
        return oauth2JwkQueryRepository.findAllValid();
    }
}

package com.thinkfree.drive.auth.token.blacklist.service.impl;

import com.thinkfree.drive.auth.token.blacklist.converter.BlacklistTokenConverter;
import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistTokenExpireQuery;
import com.thinkfree.drive.auth.token.blacklist.repository.BlacklistTokenRepository;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenService;
import com.thinkfree.drive.auth.token.blacklist.store.BlacklistStore;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultBlacklistTokenService implements BlacklistTokenService {
    
    private final BlacklistTokenConverter blacklistTokenConverter;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final JwtProvider jwtProvider;
    private final BlacklistStore blacklistStore;
    
    @Override
    public void save(String accessToken) {
        var loginTokenClaims = jwtProvider.parseLoginTokenClaims(accessToken);
        var tokenHash = TokenHasher.hash(accessToken);
        BlacklistToken entity = blacklistTokenConverter.toSaveBlacklistToken(loginTokenClaims, tokenHash);
        blacklistTokenRepository.save(entity);
        blacklistStore.add(tokenHash, entity.getExpirationDate()); // DB 커밋 뒤 Redis 사본에도
    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        var result = blacklistTokenRepository.deleteByUserSeqList(userSeqList);
        log.info("[blacklist token 삭제 : {}건]", result);
    }
    
    @Override
    public int deleteExpiredTokens(Instant expirationThreshold) {
        return blacklistTokenRepository.deleteExpiredTokens(expirationThreshold);
    }
}

package com.thinkfree.drive.auth.token.blacklist.service.impl;

import com.thinkfree.drive.auth.token.blacklist.converter.BlacklistTokenConverter;
import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistTokenExpireQuery;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.repository.BlacklistTokenQueryRepository;
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
    private final BlacklistTokenQueryRepository blacklistQueryRepository;
    private final JwtProvider jwtProvider;
    private final BlacklistStore blacklistStore;
    
    @Override
    public void save(String accessToken) {
        var loginTokenClaims = jwtProvider.parseLoginTokenClaims(accessToken);
        var tokenHash = TokenHasher.hash(accessToken);
        // 이미 있으면 건너뛴다 — 원본(DB)으로 확인한다. 호출부의 확인은 Redis 사본으로 답할 수 있는데, 사본은 커밋 뒤에 쓰여
        // 같은 트랜잭션·직전 로그아웃의 기록이 아직 없을 수 있다(그대로 INSERT 하면 token_hash 유니크 위반)
        if (blacklistQueryRepository.existsByTokenHash(new BlacklistFindQuery(tokenHash))) {
            return;
        }
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

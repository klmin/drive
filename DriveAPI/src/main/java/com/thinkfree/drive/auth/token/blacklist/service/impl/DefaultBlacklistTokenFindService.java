package com.thinkfree.drive.auth.token.blacklist.service.impl;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.repository.BlacklistTokenQueryRepository;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import com.thinkfree.drive.auth.token.blacklist.store.BlacklistStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultBlacklistTokenFindService implements BlacklistTokenFindService {
    
    private final BlacklistTokenQueryRepository blacklistQueryRepository;
    private final BlacklistStore blacklistStore;
    
    @Override
    public boolean existsByTokenHash(BlacklistFindQuery query) {
        // Redis 사본이 확실히 답하면 그것으로, 아니면(Redis 꺼짐·장애·미적재) DB 원본으로
        Boolean cached = blacklistStore.contains(query.tokenHash());
        if (cached != null) {
            return cached;
        }
        return blacklistQueryRepository.existsByTokenHash(query);
    }
    
}

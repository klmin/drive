package com.thinkfree.drive.auth.token.blacklist.service.impl;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.repository.BlacklistTokenQueryRepository;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultBlacklistTokenFindService implements BlacklistTokenFindService {
    
    private final BlacklistTokenQueryRepository blacklistQueryRepository;
    
    @Override
    public boolean existsByTokenHash(BlacklistFindQuery query) {
        return blacklistQueryRepository.existsByTokenHash(query);
    }
    
}

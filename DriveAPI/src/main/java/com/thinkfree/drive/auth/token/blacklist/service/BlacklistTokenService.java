package com.thinkfree.drive.auth.token.blacklist.service;

import java.time.Instant;
import java.util.List;

public interface BlacklistTokenService {
    void save(String accessToken);
    void deleteByUsers(List<Integer> userSeqList);
    int deleteExpiredTokens(Instant expirationThreshold);
}

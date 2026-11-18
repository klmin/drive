package com.thinkfree.drive.auth.token.refresh.service;

import com.thinkfree.drive.auth.token.refresh.result.RefreshTokenReissueResult;

import java.time.Instant;
import java.util.List;

public interface RefreshTokenService {
    void saveRefreshToken(int userSeq, String sessionId, String refreshToken);
    void deleteRefreshTokenByUserSeq(int userSeq);
    void deleteRefreshTokenBySessionId(String sessionId);
    void deleteRefreshTokenByUserSeqList(List<Integer> userSeqList);
    RefreshTokenReissueResult reissueTokens(Integer userSeq, String refreshToken);
    int deleteExpiredTokens(Instant expirationThreshold);
}

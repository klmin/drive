package com.thinkfree.drive.user.token.repository;

import java.util.List;
import com.thinkfree.drive.user.token.entity.PasswordResetToken;

import java.time.Instant;

public interface PasswordResetTokenRepository {
    void save(PasswordResetToken entity);
    void deleteByUserSeq(Integer userSeq);
    PasswordResetToken findByTokenHash(String tokenHash);
    /** 삭제한 행 수 — 0 이면 이미 소비된 토큰(동시 소비 포함), 호출부가 거부한다 */
    int deleteByTokenHash(String tokenHash);
    int deleteExpiredTokens(Instant expirationThreshold);
    /** 계정 완전 삭제 cascade — 목록이 비면 0 */
    int deleteByUserSeqList(List<Integer> userSeqList);
}

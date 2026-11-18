package com.thinkfree.drive.auth.token.refresh.repository;

import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;

import java.time.Instant;
import java.util.List;

/**
 * tb_refresh_token 쓰기 + 쓰기에 묶인 조회 — {@code DefaultRefreshTokenService} 가 주입. 읽기 전용 조회는 없다(재발급 판정은 고칠 행을 한 번 로드해 Java 로).
 */
public interface RefreshTokenRepository {

    void save(RefreshToken token);

    /** 재발급(RTR)용 — session_id 의 관리 엔티티(고치면 커밋 때 변경 감지). 없으면 null */
    RefreshToken findBySessionId(String sessionId);

    void deleteByUserSeq(Integer userSeq);

    void deleteBySessionId(String sessionId);

    int deleteByUserSeqList(List<Integer> userSeqList);

    int deleteExpiredTokens(Instant expirationThreshold);
}

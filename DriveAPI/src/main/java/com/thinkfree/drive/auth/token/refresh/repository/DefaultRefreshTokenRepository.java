package com.thinkfree.drive.auth.token.refresh.repository;

import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * tb_refresh_token 쓰기 — JPA(2차 패스 10-07, 구 JdbcClient·구 MyBatis save·update·delete 4종).
 * <ul>
 *   <li>{@code save} = Spring Data save — IDENTITY 키라 저장 순간 INSERT·id 채움. session_id 유니크 위반은 그 자리에서 {@code DataIntegrityViolationException}</li>
 *   <li>{@code findBySessionId} = 관리 엔티티(W2 10-12). 서비스가 판정 후 {@code updateRefreshToken} → 커밋 때 변경 감지(감사 컬럼 없음). session_id 는 PK 가 아닌 유니크 키라 WHERE 조회</li>
 *   <li>삭제 4종 = JPQL {@code @Modifying} — 사용자별·목록·만료 정리는 조건부 다건, 세션별은 로드 없는 삭제라 변경 감지 대상이 아님. IN 목록이 비면 질의 없이 0</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    @Override
    public void save(RefreshToken token) {
        jpaRepository.save(token);
    }

    @Override
    public RefreshToken findBySessionId(String sessionId) {
        return jpaRepository.findBySessionId(sessionId).orElse(null);
    }

    @Override
    public void deleteByUserSeq(Integer userSeq) {
        jpaRepository.deleteByUserSeq(userSeq);
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        jpaRepository.deleteBySessionId(sessionId);
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public int deleteExpiredTokens(Instant expirationThreshold) {
        return jpaRepository.deleteExpiredBefore(expirationThreshold);
    }
}

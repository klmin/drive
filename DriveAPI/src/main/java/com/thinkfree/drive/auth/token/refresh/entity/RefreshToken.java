package com.thinkfree.drive.auth.token.refresh.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_refresh_token — JPA 매핑(2차 패스 10-07, §1 v2 "JPA 우선"). 저장은 save, RTR 은 서비스가 쓰기 리포 {@code findBySessionId} 의 관리 엔티티를 판정({@link #isValid}) 후 {@link #updateRefreshToken} → 커밋 때 변경 감지(W2 10-12),
 * 삭제 4종은 JPQL {@code @Modifying}({@code RefreshTokenJpaRepository}).
 * 감사 컬럼 없음. expiration_date(timestamptz)는 Instant(Hibernate 기본 매핑).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_refresh_token")
public class RefreshToken {
    public static final Long DEFAULT_TTL = 60L * 60 * 24 * 7; // 7일

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int userSeq;
    private String sessionId;
    private String refreshToken;
    private Instant expirationDate;

    private RefreshToken(int userSeq, String sessionId, String refreshToken) {
        this.userSeq = userSeq;
        this.sessionId = sessionId;
        this.refreshToken = refreshToken;
        this.expirationDate = Instant.now().plusSeconds(DEFAULT_TTL);
    }

    private RefreshToken(Long id, int userSeq, String sessionId, String refreshToken, Instant expirationDate) {
        this.id = id;
        this.userSeq = userSeq;
        this.sessionId = sessionId;
        this.refreshToken = refreshToken;
        this.expirationDate = expirationDate;
    }

    public static RefreshToken createRefreshToken(int userSeq, String sessionId, String refreshToken) {
        return new RefreshToken(userSeq, sessionId, refreshToken);
    }

    /** 값 지정 생성(만료 시각을 직접 줌) — 테스트·데이터 복원용. id 는 null 로 넘기면 저장 시 identity 로 채워진다. */
    public static RefreshToken restore(Long id, int userSeq, String sessionId, String refreshToken, Instant expirationDate) {
        return new RefreshToken(id, userSeq, sessionId, refreshToken, expirationDate);
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        this.expirationDate = Instant.now().plusSeconds(DEFAULT_TTL);
    }

    /** 재발급 판정 — 토큰 값이 같고 now 가 만료 시각을 넘지 않았으면(같은 순간 포함) 유효. 쓰기 리포 findBySessionId 로 로드한 관리 엔티티에서 판정한 뒤 {@link #updateRefreshToken}. */
    public boolean isValid(String refreshToken, Instant now) {
        return this.refreshToken.equals(refreshToken) && !now.isAfter(this.expirationDate);
    }
}

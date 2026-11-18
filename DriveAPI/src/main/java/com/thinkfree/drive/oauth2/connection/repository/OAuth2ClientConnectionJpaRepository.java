package com.thinkfree.drive.oauth2.connection.repository;

import java.util.Collection;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.thinkfree.drive.oauth2.connection.entity.OAuth2ClientConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * tb_oauth2_client_connection Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultOAuth2ClientConnectionRepository} 만 주입받아 위임한다.
 * (user, client) 조회는 파생 쿼리(두 컬럼 NOT NULL — null 인자도 IS NULL 0건이라 `=` 와 같음, 10-13). 연결 seq 단건은 {@code findById}, 삭제는 {@code delete}, last_used_date 는 관리 엔티티 변경 감지(W2 10-12).
 */
interface OAuth2ClientConnectionJpaRepository extends JpaRepository<OAuth2ClientConnection, Long> {

    Optional<OAuth2ClientConnection> findByUserSeqAndClientId(Integer userSeq, String clientId);

    /** 계정 완전 삭제 cascade — 대상 계정들의 MCP OAuth 연결 */
    @Transactional
    @Modifying
    @Query("delete from OAuth2ClientConnection o where o.userSeq in :userSeq")
    int deleteByUserSeqIn(@Param("userSeq") Collection<Integer> userSeq);
}

package com.thinkfree.drive.oauth2.connection.repository;

import java.util.List;
import com.thinkfree.drive.oauth2.connection.entity.OAuth2ClientConnection;

/**
 * tb_oauth2_client_connection 쓰기 + 쓰기에 묶인 조회 — {@code DefaultOAuth2ClientConnectionService} 가 주입. 목록 조회는 {@link OAuth2ClientConnectionQueryRepository}.
 */
public interface OAuth2ClientConnectionRepository {

    /** 토큰 발급 기록 — (user_seq, client_id) 가 있으면 scopes·token_issued_date 만 갱신(register_date 유지) */
    void upsert(int userSeq, String clientId, String scopes);

    /** RS 필터의 매 요청 존재 확인 + 마지막 사용일 기록용 관리 엔티티. 없으면 null */
    OAuth2ClientConnection findByUserSeqAndClientId(int userSeq, String clientId);

    /** 해제용 관리 엔티티(PK 로드) — 없으면 null, 소유자 판정은 Service */
    OAuth2ClientConnection findById(long connectionSeq);

    /** 관리 엔티티 삭제(커밋 때 DELETE) */
    void delete(OAuth2ClientConnection connection);
    /** 계정 완전 삭제 cascade — 목록이 비면 0 */
    int deleteByUserSeqList(List<Integer> userSeqList);
}

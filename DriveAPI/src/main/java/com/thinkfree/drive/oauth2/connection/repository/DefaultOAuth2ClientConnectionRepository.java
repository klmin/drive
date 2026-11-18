package com.thinkfree.drive.oauth2.connection.repository;

import java.util.List;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.oauth2.connection.entity.OAuth2ClientConnection;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;

import static com.thinkfree.drive.jooq.Tables.TB_OAUTH2_CLIENT_CONNECTION;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_oauth2_client_connection 쓰기 — JPA 위임 + jOOQ upsert(§1 v2 "JPA 우선", 구 OAuth2ClientConnectionMapper.xml). 목록 조회는 {@link DefaultOAuth2ClientConnectionQueryRepository}.
 * <ul>
 *   <li>{@code upsert} 만 jOOQ — {@code ON CONFLICT (user_seq, client_id) DO UPDATE SET scopes, token_issued_date = EXCLUDED.*}(register_date 는 유지, XML 동일) 는 JPQL 에 없는 문장이라 변경 감지 대상이 아님.
 *       register_date·token_issued_date 는 같은 현재 시각({@link JdbcAudit#now})</li>
 *   <li>{@code findByUserSeqAndClientId}(파생 쿼리)·{@code findById}(소유자 판정은 Service) = 관리 엔티티(W2 10-12). 서비스가 마지막 사용일을 변경 메서드로 고치거나(커밋 때 변경 감지) {@code delete} 로 지운다 — flush·detach 없음</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultOAuth2ClientConnectionRepository implements OAuth2ClientConnectionRepository {

    private final OAuth2ClientConnectionJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public void upsert(int userSeq, String clientId, String scopes) {
        Instant now = JdbcAudit.now();
        dsl.insertInto(TB_OAUTH2_CLIENT_CONNECTION,
                        TB_OAUTH2_CLIENT_CONNECTION.USER_SEQ, TB_OAUTH2_CLIENT_CONNECTION.CLIENT_ID, TB_OAUTH2_CLIENT_CONNECTION.SCOPES,
                        TB_OAUTH2_CLIENT_CONNECTION.REGISTER_DATE, TB_OAUTH2_CLIENT_CONNECTION.TOKEN_ISSUED_DATE)
                .values(userSeq, clientId, scopes, now, now)
                .onConflict(TB_OAUTH2_CLIENT_CONNECTION.USER_SEQ, TB_OAUTH2_CLIENT_CONNECTION.CLIENT_ID)
                .doUpdate()
                .set(TB_OAUTH2_CLIENT_CONNECTION.SCOPES, excluded(TB_OAUTH2_CLIENT_CONNECTION.SCOPES))
                .set(TB_OAUTH2_CLIENT_CONNECTION.TOKEN_ISSUED_DATE, excluded(TB_OAUTH2_CLIENT_CONNECTION.TOKEN_ISSUED_DATE))
                .execute();
    }

    /** (user_seq, client_id) 는 PK 가 아닌 유니크 키라 JPQL WHERE 조회 */
    @Override
    public OAuth2ClientConnection findByUserSeqAndClientId(int userSeq, String clientId) {
        return jpaRepository.findByUserSeqAndClientId(userSeq, clientId).orElse(null);
    }

    @Override
    public OAuth2ClientConnection findById(long connectionSeq) {
        return jpaRepository.findById(connectionSeq).orElse(null);
    }

    @Override
    public void delete(OAuth2ClientConnection connection) {
        jpaRepository.delete(connection);
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }
}

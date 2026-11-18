package com.thinkfree.drive.oauth2.connection.repository;

import com.thinkfree.drive.jooq.tables.Oauth2RegisteredClient;
import com.thinkfree.drive.jooq.tables.TbOauth2ClientConnection;
import com.thinkfree.drive.oauth2.connection.dto.OAuth2ClientConnectionData;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.OAUTH2_REGISTERED_CLIENT;
import static com.thinkfree.drive.jooq.Tables.TB_OAUTH2_CLIENT_CONNECTION;

/**
 * tb_oauth2_client_connection 읽기 — jOOQ(구 OAuth2ClientConnectionMapper.xml 의 findAllWithClientNameByUserSeq, 10-30 작업 B P1-③ 에서 JdbcClient → jOOQ).
 * <p>
 * JPA 로 가지 않은 이유 = {@code oauth2_registered_client} 는 Spring Authorization Server 가 관리하는 비엔티티 테이블이라 JPQL 로 조인할 수 없다(생성 코드에는 포함 — jooq-codegen.xml includes).
 * LEFT JOIN 인 이유: 클라이언트 행이 지워진 연결(재등록 등)도 목록·해제가 가능해야 한다.
 */
@Repository
@RequiredArgsConstructor
public class DefaultOAuth2ClientConnectionQueryRepository implements OAuth2ClientConnectionQueryRepository {

    private final DSLContext dsl;

    @Override
    public List<OAuth2ClientConnectionData> findAllWithClientNameByUserSeq(int userSeq) {
        TbOauth2ClientConnection a = TB_OAUTH2_CLIENT_CONNECTION.as("a");
        Oauth2RegisteredClient b = OAUTH2_REGISTERED_CLIENT.as("b");
        return dsl.select(a.CONNECTION_SEQ, a.CLIENT_ID, b.CLIENT_NAME, a.SCOPES, a.REGISTER_DATE, a.LAST_USED_DATE)
                .from(a)
                .leftJoin(b).on(b.CLIENT_ID.eq(a.CLIENT_ID))
                .where(a.USER_SEQ.eq(userSeq))
                .orderBy(a.REGISTER_DATE.desc())
                .fetch(r -> OAuth2ClientConnectionData.builder()
                        .connectionSeq(r.get(a.CONNECTION_SEQ))
                        .clientId(r.get(a.CLIENT_ID))
                        .clientName(r.get(b.CLIENT_NAME))
                        .scopes(r.get(a.SCOPES))
                        .registerDate(r.get(a.REGISTER_DATE))
                        .lastUsedDate(r.get(a.LAST_USED_DATE))
                        .build());
    }
}

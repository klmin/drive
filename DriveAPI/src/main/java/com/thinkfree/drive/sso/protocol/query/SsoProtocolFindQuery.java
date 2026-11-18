package com.thinkfree.drive.sso.protocol.query;

/**
 * 프로토콜별 설정 단건 조회 키 — 구현체({@code SsoJwtConfigQuery.Find}·{@code SsoOidcConfigQuery.Find})는 {@code @Getter} 로 충족.
 * MyBatis 때는 리플렉션 {@code #{ssoProviderSeq}} 로 읽었고, JdbcClient 리포는 이 getter 로 바인딩한다.
 */
public interface SsoProtocolFindQuery {

    Integer getSsoProviderSeq();
}

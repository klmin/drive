package com.thinkfree.drive.sso.protocol.oidc.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.sso.protocol.oidc.entity.SsoOidcConfig;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_SSO_OIDC_CONFIG;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_sso_oidc_config 쓰기 — upsert(jOOQ) + IN 삭제(JPQL), SsoJwtConfig 와 같은 꼴. 읽기는 {@link DefaultSsoOidcConfigQueryRepository}.
 * <ul>
 *   <li>{@code upsert} = {@code ON CONFLICT (sso_provider_seq) DO UPDATE SET … = EXCLUDED.*}(JPQL 에 없는 문장이라 jOOQ — 변경 감지 대상 아님).
 *       충돌 시 update_* 는 {@link JdbcAudit}(시큐리티 사용자 우선 → 선언 값, 시각은 선언 값 없으면 현재 시각). 인자 엔티티에 감사 값을 되쓰지 않음</li>
 *   <li>{@code deleteByProviders} = JPQL {@code @Modifying} IN 삭제(조건부 다건 — 행마다 로드할 이유 없음), 빈 목록 가드</li>
 * </ul>
 * {@code allow_idp_initiated} 는 NOT NULL char(1) — null 이면 DB 가 거부.
 */
@Repository
@RequiredArgsConstructor
public class DefaultSsoOidcConfigRepository implements SsoOidcConfigRepository {

    private final SsoOidcConfigJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public int upsert(SsoOidcConfig e) {
        return dsl.insertInto(TB_SSO_OIDC_CONFIG,
                        TB_SSO_OIDC_CONFIG.SSO_PROVIDER_SEQ, TB_SSO_OIDC_CONFIG.ISSUER_URL, TB_SSO_OIDC_CONFIG.CLIENT_ID, TB_SSO_OIDC_CONFIG.CLIENT_SECRET,
                        TB_SSO_OIDC_CONFIG.SCOPES, TB_SSO_OIDC_CONFIG.ALLOW_IDP_INITIATED, TB_SSO_OIDC_CONFIG.REGISTER_USER_SEQ, TB_SSO_OIDC_CONFIG.REGISTER_DATE)
                .values(e.getSsoProviderSeq(), e.getIssuerUrl(), e.getClientId(), e.getClientSecret(),
                        e.getScopes(), e.getAllowIdpInitiated(), JdbcAudit.userSeqOr(e.getRegisterUserSeq()), JdbcAudit.nowOr(e.getRegisterDate()))
                .onConflict(TB_SSO_OIDC_CONFIG.SSO_PROVIDER_SEQ)
                .doUpdate()
                .set(TB_SSO_OIDC_CONFIG.ISSUER_URL, excluded(TB_SSO_OIDC_CONFIG.ISSUER_URL))
                .set(TB_SSO_OIDC_CONFIG.CLIENT_ID, excluded(TB_SSO_OIDC_CONFIG.CLIENT_ID))
                .set(TB_SSO_OIDC_CONFIG.CLIENT_SECRET, excluded(TB_SSO_OIDC_CONFIG.CLIENT_SECRET))
                .set(TB_SSO_OIDC_CONFIG.SCOPES, excluded(TB_SSO_OIDC_CONFIG.SCOPES))
                .set(TB_SSO_OIDC_CONFIG.ALLOW_IDP_INITIATED, excluded(TB_SSO_OIDC_CONFIG.ALLOW_IDP_INITIATED))
                .set(TB_SSO_OIDC_CONFIG.UPDATE_USER_SEQ, JdbcAudit.userSeqOr(e.getUpdateUserSeq()))
                .set(TB_SSO_OIDC_CONFIG.UPDATE_DATE, JdbcAudit.nowOr(e.getUpdateDate()))
                .execute();
    }

    @Override
    public int deleteByProviders(List<Integer> ssoProviderSeqList) {
        if (ssoProviderSeqList == null || ssoProviderSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteAllBySsoProviderSeqIn(ssoProviderSeqList);
    }
}

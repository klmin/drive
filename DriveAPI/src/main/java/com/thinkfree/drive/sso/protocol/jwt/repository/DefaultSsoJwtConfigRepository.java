package com.thinkfree.drive.sso.protocol.jwt.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.sso.protocol.jwt.entity.SsoJwtConfig;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_SSO_JWT_CONFIG;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_sso_jwt_config 쓰기 — upsert(jOOQ) + IN 삭제(JPQL). 읽기는 {@link DefaultSsoJwtConfigQueryRepository}.
 * <ul>
 *   <li>{@code upsert} = {@code ON CONFLICT (sso_provider_seq) DO UPDATE SET … = EXCLUDED.*}(JPQL 에 없는 문장이라 jOOQ — 변경 감지 대상 아님), 충돌 시 update_* 에는 등록 감사 값을 넣는다.
 *       감사 값은 {@link JdbcAudit}(인자 엔티티에 되쓰지 않음)</li>
 *   <li>{@code deleteByProviders} = JPQL {@code @Modifying} IN 삭제(조건부 다건 — 행마다 로드할 이유 없음), 빈 목록 가드</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultSsoJwtConfigRepository implements SsoJwtConfigRepository {

    private final SsoJwtConfigJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public int upsert(SsoJwtConfig e) {
        return dsl.insertInto(TB_SSO_JWT_CONFIG,
                        TB_SSO_JWT_CONFIG.SSO_PROVIDER_SEQ, TB_SSO_JWT_CONFIG.ISSUER, TB_SSO_JWT_CONFIG.AUDIENCE, TB_SSO_JWT_CONFIG.KEY_SOURCE_TYPE,
                        TB_SSO_JWT_CONFIG.PUBLIC_KEY, TB_SSO_JWT_CONFIG.JWKS_URI, TB_SSO_JWT_CONFIG.EMAIL_CLAIM_NAME, TB_SSO_JWT_CONFIG.ERROR_REDIRECT_URL,
                        TB_SSO_JWT_CONFIG.REGISTER_USER_SEQ, TB_SSO_JWT_CONFIG.REGISTER_DATE)
                .values(e.getSsoProviderSeq(), e.getIssuer(), e.getAudience(), e.getKeySourceType(),
                        e.getPublicKey(), e.getJwksUri(), e.getEmailClaimName(), e.getErrorRedirectUrl(),
                        JdbcAudit.userSeqOr(e.getRegisterUserSeq()), JdbcAudit.nowOr(e.getRegisterDate()))
                .onConflict(TB_SSO_JWT_CONFIG.SSO_PROVIDER_SEQ)
                .doUpdate()
                .set(TB_SSO_JWT_CONFIG.ISSUER, excluded(TB_SSO_JWT_CONFIG.ISSUER))
                .set(TB_SSO_JWT_CONFIG.AUDIENCE, excluded(TB_SSO_JWT_CONFIG.AUDIENCE))
                .set(TB_SSO_JWT_CONFIG.KEY_SOURCE_TYPE, excluded(TB_SSO_JWT_CONFIG.KEY_SOURCE_TYPE))
                .set(TB_SSO_JWT_CONFIG.PUBLIC_KEY, excluded(TB_SSO_JWT_CONFIG.PUBLIC_KEY))
                .set(TB_SSO_JWT_CONFIG.JWKS_URI, excluded(TB_SSO_JWT_CONFIG.JWKS_URI))
                .set(TB_SSO_JWT_CONFIG.EMAIL_CLAIM_NAME, excluded(TB_SSO_JWT_CONFIG.EMAIL_CLAIM_NAME))
                .set(TB_SSO_JWT_CONFIG.ERROR_REDIRECT_URL, excluded(TB_SSO_JWT_CONFIG.ERROR_REDIRECT_URL))
                .set(TB_SSO_JWT_CONFIG.UPDATE_USER_SEQ, excluded(TB_SSO_JWT_CONFIG.REGISTER_USER_SEQ))
                .set(TB_SSO_JWT_CONFIG.UPDATE_DATE, excluded(TB_SSO_JWT_CONFIG.REGISTER_DATE))
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

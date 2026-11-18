package com.thinkfree.drive.sso.provider.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_SSO_PROVIDER;
import static org.jooq.impl.DSL.defaultValue;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.val;

/**
 * tb_sso_provider 쓰기 — upsert(jOOQ) + IN 삭제(JPQL). 읽기는 {@link DefaultSsoProviderQueryRepository}.
 * <ul>
 *   <li>{@code upsert} = ON CONFLICT 라 jOOQ(JPQL 에 없는 문장 — 변경 감지 대상 아님): 컬럼을 고정한 {@code values()} 에 XML {@code <choose>} 의 seq 유무를 값으로(있으면 그 값, 없으면 {@code DEFAULT}),
 *       충돌 시 EXCLUDED 값으로 갱신하고 update_* 에는 등록 감사 값을 넣는다(XML 동일).
 *       생성/대상 키는 RETURNING 으로 회수해 반환. 감사 값은 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭)</li>
 *   <li>{@code deleteBySeqList} = JPQL {@code @Modifying}(조건부 다건 삭제 — 행마다 로드할 이유 없음)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultSsoProviderRepository implements SsoProviderRepository {

    private final SsoProviderJpaRepository ssoProviderJpaRepository;
    private final DSLContext dsl;

    @Override
    public Integer upsert(SsoProvider e) {
        Field<Integer> seq = e.getSsoProviderSeq() == null
                ? defaultValue(TB_SSO_PROVIDER.SSO_PROVIDER_SEQ)
                : val(e.getSsoProviderSeq(), TB_SSO_PROVIDER.SSO_PROVIDER_SEQ);
        return dsl.insertInto(TB_SSO_PROVIDER, TB_SSO_PROVIDER.SSO_PROVIDER_SEQ, TB_SSO_PROVIDER.TENANT_SEQ, TB_SSO_PROVIDER.DISPLAY_NAME,
                        TB_SSO_PROVIDER.ICON_IMAGE_KEY, TB_SSO_PROVIDER.PROTOCOL_TYPE, TB_SSO_PROVIDER.STATUS, TB_SSO_PROVIDER.SCOPE,
                        TB_SSO_PROVIDER.SORT_ORDER, TB_SSO_PROVIDER.REGISTER_USER_SEQ, TB_SSO_PROVIDER.REGISTER_DATE)
                .values(seq, val(e.getTenantSeq(), TB_SSO_PROVIDER.TENANT_SEQ), val(e.getDisplayName(), TB_SSO_PROVIDER.DISPLAY_NAME),
                        val(e.getIconImageKey(), TB_SSO_PROVIDER.ICON_IMAGE_KEY), val(e.getProtocolType(), TB_SSO_PROVIDER.PROTOCOL_TYPE),
                        val(e.getStatus(), TB_SSO_PROVIDER.STATUS), val(e.getScope(), TB_SSO_PROVIDER.SCOPE),
                        val(e.getSortOrder(), TB_SSO_PROVIDER.SORT_ORDER),
                        val(JdbcAudit.userSeqOr(e.getRegisterUserSeq()), TB_SSO_PROVIDER.REGISTER_USER_SEQ),
                        val(JdbcAudit.nowOr(e.getRegisterDate()), TB_SSO_PROVIDER.REGISTER_DATE))
                .onConflict(TB_SSO_PROVIDER.SSO_PROVIDER_SEQ)
                .doUpdate()
                .set(TB_SSO_PROVIDER.DISPLAY_NAME, excluded(TB_SSO_PROVIDER.DISPLAY_NAME))
                .set(TB_SSO_PROVIDER.ICON_IMAGE_KEY, excluded(TB_SSO_PROVIDER.ICON_IMAGE_KEY))
                .set(TB_SSO_PROVIDER.STATUS, excluded(TB_SSO_PROVIDER.STATUS))
                .set(TB_SSO_PROVIDER.SORT_ORDER, excluded(TB_SSO_PROVIDER.SORT_ORDER))
                .set(TB_SSO_PROVIDER.UPDATE_USER_SEQ, excluded(TB_SSO_PROVIDER.REGISTER_USER_SEQ))
                .set(TB_SSO_PROVIDER.UPDATE_DATE, excluded(TB_SSO_PROVIDER.REGISTER_DATE))
                .returningResult(TB_SSO_PROVIDER.SSO_PROVIDER_SEQ)
                .fetchOne(TB_SSO_PROVIDER.SSO_PROVIDER_SEQ);
    }

    /** 빈 목록은 {@code IN ()} 를 만들지 않도록 0. */
    @Override
    public int deleteBySeqList(List<Integer> ssoProviderSeqList) {
        if (ssoProviderSeqList == null || ssoProviderSeqList.isEmpty()) {
            return 0;
        }
        return ssoProviderJpaRepository.deleteBySsoProviderSeqIn(ssoProviderSeqList);
    }
}

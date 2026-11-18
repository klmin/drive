package com.thinkfree.drive.basedomain.repository;

import com.thinkfree.drive.basedomain.entity.BaseDomain;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.thinkfree.drive.jooq.Tables.TB_BASE_DOMAIN;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_base_domain 쓰기 — upsert 는 jOOQ(구 BaseDomainMapper.xml 의 upsert — ON CONFLICT 는 JPQL 에 없는 문장이라 변경 감지 대상 아님). 조회는 {@link DefaultBaseDomainQueryRepository}(JPQL 생성자 식).
 * <p>
 * 현재 단일 베이스 운영: base_domain_seq 는 1 고정({@code chk_base_domain_singleton}). 멀티 베이스 확장 시 고정 1 을 풀고 일반 PK 기반으로 전환한다.
 * upsert 는 PK(1) 충돌 → BASE_DOMAIN 갱신 + update_* 를 register 바인딩 값으로(MyBatis 때 규칙 그대로).
 * 감사 컬럼은 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭).
 */
@Repository
@RequiredArgsConstructor
public class DefaultBaseDomainRepository implements BaseDomainRepository {

    /** 단일 베이스 운영 단계의 고정 PK. */
    private static final int SINGLE_BASE_DOMAIN_SEQ = 1;

    private final DSLContext dsl;

    /** seq 는 엔티티 값과 무관하게 1 고정(XML 과 동일). */
    @Override
    public void upsert(BaseDomain baseDomain) {
        dsl.insertInto(TB_BASE_DOMAIN,
                        TB_BASE_DOMAIN.BASE_DOMAIN_SEQ, TB_BASE_DOMAIN.BASE_DOMAIN, TB_BASE_DOMAIN.SCHEME, TB_BASE_DOMAIN.PORT,
                        TB_BASE_DOMAIN.REGISTER_USER_SEQ, TB_BASE_DOMAIN.REGISTER_DATE)
                .values(SINGLE_BASE_DOMAIN_SEQ, baseDomain.getBaseDomain(), baseDomain.getScheme(), baseDomain.getPort(),
                        JdbcAudit.userSeqOr(baseDomain.getRegisterUserSeq()), JdbcAudit.nowOr(baseDomain.getRegisterDate()))
                .onConflict(TB_BASE_DOMAIN.BASE_DOMAIN_SEQ)
                .doUpdate()
                .set(TB_BASE_DOMAIN.BASE_DOMAIN, excluded(TB_BASE_DOMAIN.BASE_DOMAIN))
                .set(TB_BASE_DOMAIN.SCHEME, excluded(TB_BASE_DOMAIN.SCHEME))
                .set(TB_BASE_DOMAIN.PORT, excluded(TB_BASE_DOMAIN.PORT))
                .set(TB_BASE_DOMAIN.UPDATE_USER_SEQ, excluded(TB_BASE_DOMAIN.REGISTER_USER_SEQ))
                .set(TB_BASE_DOMAIN.UPDATE_DATE, excluded(TB_BASE_DOMAIN.REGISTER_DATE))
                .execute();
    }
}

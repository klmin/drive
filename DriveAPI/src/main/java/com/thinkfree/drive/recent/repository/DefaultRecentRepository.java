package com.thinkfree.drive.recent.repository;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.recent.entity.Recent;
import com.thinkfree.drive.recent.exception.RecentErrorCode;
import com.thinkfree.drive.recent.query.*;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Row2;
import org.jooq.Row3;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_RECENT;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.row;

/**
 * tb_recent 쓰기 — 삭제 10종은 JPA(JPQL, {@link RecentJpaRepository}), upsert·행값 IN 삭제는 jOOQ(구 RecentMapper.xml 의 save(upsert)·delete 12). 읽기는 {@link DefaultRecentQueryRepository}.
 * <p>
 * jOOQ 로 둔 것: {@code save} = {@code ON CONFLICT (user_seq, resource_seq, shared_by_user_seq) DO UPDATE SET update_date} upsert,
 * (user_seq, resource_seq) 행값 IN({@code deleteByShareTargets}), (user_seq, shared_by_user_seq, resource_seq) 행값 IN({@code deleteByUserResources}) — JPQL 은 튜플 목록을 파라미터 하나로 바인딩하지 못한다.
 * INSERT 는 컬럼을 고정한 {@code values()} — recent_seq 는 항상 DEFAULT(XML {@code <choose>} 의 seq 지정 분기는 넘기는 호출부가 없어 뺐다). 등록 감사는 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭) 로 정해 바인딩(엔티티 write-back 없음), update_date 는 엔티티 값 그대로(converter 가 채움).
 * DELETE(JPQL — 조건부 다건이라 변경 감지·remove 대상이 아님) — 선택 조건(excludeUserSeqList, 구 {@code andIfPresent})은 있음/없음 JPQL 두 벌 중 하나를 고르고, 테넌트 조건은 User 서브쿼리, shareTypes 는 enum 으로 바꿔 넘긴다.
 * IN 목록이 비면 쿼리 없이 0.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DefaultRecentRepository implements RecentRepository {

    private final RecentJpaRepository jpaRepository;
    private final DSLContext dsl;

    /** ON CONFLICT upsert 는 JPQL 에 없는 문장이라 jOOQ. 등록 감사는 {@link JdbcAudit} 로 정해 바인딩만 한다(호출부가 엔티티 감사 값을 읽지 않아 write-back 없음, W3 10-13). recent_seq 는 되돌려 받지 않는다. */
    @Override
    public int save(Recent e) {
        return dsl.insertInto(TB_RECENT, TB_RECENT.USER_SEQ, TB_RECENT.RESOURCE_SEQ, TB_RECENT.SHARED_BY_USER_SEQ,
                        TB_RECENT.REGISTER_USER_SEQ, TB_RECENT.REGISTER_DATE, TB_RECENT.UPDATE_DATE)
                .values(e.getUserSeq(), e.getResourceSeq(), e.getSharedByUserSeq(),
                        JdbcAudit.userSeqOr(e.getRegisterUserSeq()), JdbcAudit.nowOr(e.getRegisterDate()), e.getUpdateDate())
                .onConflict(TB_RECENT.USER_SEQ, TB_RECENT.RESOURCE_SEQ, TB_RECENT.SHARED_BY_USER_SEQ)
                .doUpdate()
                .set(TB_RECENT.UPDATE_DATE, excluded(TB_RECENT.UPDATE_DATE))
                .execute();
    }

    @Override
    public int deleteByResource(RecentDeleteResourceQuery q) {
        return jpaRepository.deleteBySharedByUserSeqAndResourceSeq(q.getSharedByUserSeq(), q.getResourceSeq());
    }

    @Override
    public int deleteByResources(RecentDeleteResourcesQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteBySharedByUserSeqAndResourceSeqIn(q.getSharedByUserSeq(), q.getResourceSeqList());
    }

    /** 소유자(userSeq) 제외 + 선택적으로 excludeUserSeqList 제외. */
    @Override
    public int deleteByResourceExcludingOwner(RecentDeleteResourceQuery q) {
        if (isEmpty(q.getExcludeUserSeqList())) {
            return jpaRepository.deleteByResourceExcludingOwner(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeq());
        }
        return jpaRepository.deleteByResourceExcludingOwnerAndUsers(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeq(), q.getExcludeUserSeqList());
    }

    @Override
    public int deleteByResourcesExcludingOwner(RecentDeleteResourcesQuery q) {
        if (isEmpty(q.getResourceSeqList())) {
            return 0;
        }
        if (isEmpty(q.getExcludeUserSeqList())) {
            return jpaRepository.deleteByResourcesExcludingOwner(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeqList());
        }
        return jpaRepository.deleteByResourcesExcludingOwnerAndUsers(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeqList(), q.getExcludeUserSeqList());
    }

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자 + 선택적으로 excludeUserSeqList 제외. */
    @Override
    public int deleteOtherTenantByResources(RecentDeleteResourcesQuery q) {
        if (isEmpty(q.getResourceSeqList())) {
            return 0;
        }
        if (isEmpty(q.getExcludeUserSeqList())) {
            return jpaRepository.deleteOtherTenantByResources(q.getSharedByUserSeq(), q.getResourceSeqList(), q.getTenantSeq());
        }
        return jpaRepository.deleteOtherTenantByResourcesExcludingUsers(q.getSharedByUserSeq(), q.getResourceSeqList(), q.getTenantSeq(), q.getExcludeUserSeqList());
    }

    /** (user_seq, resource_seq) 파티션 키 행값 IN — 키의 (sharedByUserSeq, resourceSeq) 를 그 순서로 (user_seq, resource_seq) 에 맞춘다(구 SQL 과 같음), 1만 행씩 끊어 실행. */
    @Override
    public int deleteByShareTargets(RecentDeleteShareTargetsQuery q) {
        if (q.getKeys() == null || q.getKeys().isEmpty()) {
            return 0;
        }
        List<Row2<Integer, Long>> keys = q.getKeys().stream().map((PartitionKey k) -> row(k.sharedByUserSeq(), k.resourceSeq())).toList();
        return JooqBatches.executeInRowChunks(keys, chunk -> dsl.deleteFrom(TB_RECENT)
                .where(TB_RECENT.SHARED_BY_USER_SEQ.eq(q.getSharedByUserSeq()))
                .and(row(TB_RECENT.USER_SEQ, TB_RECENT.RESOURCE_SEQ).in(chunk))
                .execute());
    }

    /**
     * 사용자별 하위 목록 삭제 — 항목(사용자 × 리소스 목록)을 (user_seq, shared_by_user_seq, resource_seq) 행으로 펼쳐 행값 IN 한 문장
     * ({@link JooqBatches#executeInRowChunks} 묶음마다 한 번 왕복 — 구 배치는 IN 길이가 다른 항목마다 따로 묶였다). 빈 목록 항목은 건너뜀. 실패는 DELETE_FAIL(구 task 의 exceptionSupplier 와 동일).
     */
    @Override
    public void deleteByUserResources(List<RecentDeleteUserResourcesQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }
        try {
            List<Row3<Integer, Integer, Long>> rows = queries.stream()
                    .filter(q -> q.getResourceSeqList() != null && !q.getResourceSeqList().isEmpty())
                    .flatMap(q -> q.getResourceSeqList().stream().map(resourceSeq -> row(q.getUserSeq(), q.getSharedByUserSeq(), resourceSeq)))
                    .toList();
            JooqBatches.executeInRowChunks(rows, chunk -> dsl.deleteFrom(TB_RECENT)
                    .where(row(TB_RECENT.USER_SEQ, TB_RECENT.SHARED_BY_USER_SEQ, TB_RECENT.RESOURCE_SEQ).in(chunk))
                    .execute());
        } catch (RuntimeException e) {
            log.error("[최근 문서 사용자별 리소스 삭제 실패]", e);
            throw ApiRuntimeException.type(RecentErrorCode.DELETE_FAIL);
        }
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        if (sharedByUserSeqList == null || sharedByUserSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteBySharedByUserSeqIn(sharedByUserSeqList);
    }

    @Override
    public int deleteAllSharedByTenant(Integer tenantSeq) {
        return jpaRepository.deleteAllSharedByTenant(tenantSeq);
    }

    /** shareTypes 는 ShareType name() 목록(호출부 계약) — enum 파라미터로 바꿔 넘긴다. */
    @Override
    public int deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (isEmpty(shareTypes)) {
            return 0;
        }
        return jpaRepository.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes.stream().map(ShareType::valueOf).toList());
    }

    @Override
    public int deleteOtherTenantSharedByTenant(Integer tenantSeq) {
        return jpaRepository.deleteOtherTenantSharedByTenant(tenantSeq);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}

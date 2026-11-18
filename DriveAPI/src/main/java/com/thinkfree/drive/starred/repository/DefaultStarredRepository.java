package com.thinkfree.drive.starred.repository;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.starred.entity.Starred;
import com.thinkfree.drive.starred.exception.StarredErrorCode;
import com.thinkfree.drive.starred.query.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Row2;
import org.jooq.Row3;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_STARRED;
import static org.jooq.impl.DSL.row;

/**
 * tb_starred 쓰기 — JPA 위임 + JdbcClient 혼합(구 StarredMapper.xml 의 save 1·delete 13). 읽기는 {@link DefaultStarredQueryRepository}.
 * <p>
 * {@code save} 는 {@link StarredJpaRepository#save}(IDENTITY 라 저장 순간 INSERT) — {@link Starred} 는 변경 메서드 없는 불변 엔티티(§1 규칙: identity INSERT 단건은 JPA). identity PK·감사 컬럼이 엔티티에 채워진다.
 * uniq_starred 위반은 Spring 이 {@code DataIntegrityViolationException}(또는 그 하위 DuplicateKeyException) 으로 번역 — 호출부({@code DefaultStarredService.save})가 상위 타입으로 잡는다.
 * DELETE 13종 중 11종은 JPQL {@code @Modifying}({@link StarredJpaRepository} — 조건부 다건 또는 로드 없는 키 삭제(삭제 행 수로 판정)라 load→remove 로 바꾸지 않음) — 선택 조건(excludeUserSeqList, 구 {@code andIfPresent})은 있음/없음 JPQL 두 벌 중 하나를 고르고,
 * 테넌트 조건은 User 서브쿼리, shareTypes 는 enum 으로 바꿔 넘긴다. jOOQ 로 둔 것: 행값 IN 삭제 2종({@code deleteByShareTargets}·{@code deleteByUserResources} — JPQL 은 튜플 목록을 파라미터 하나로 바인딩하지 못한다).
 * IN 목록이 비면 쿼리 없이 0.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DefaultStarredRepository implements StarredRepository {

    private final StarredJpaRepository starredJpaRepository;
    private final DSLContext dsl;

    /** IDENTITY 키라 save 순간 INSERT — uniq_starred 위반도 이 호출 안에서 DataIntegrityViolationException 으로 나와 호출부(DefaultStarredService.save)의 try 에서 CONFLICT 로 바뀐다(flush 불필요) */
    @Override
    public void save(Starred entity) {
        starredJpaRepository.save(entity);
    }

    @Override
    public int delete(StarredDeleteQuery q) {
        return starredJpaRepository.deleteByStarredSeqAndUserSeq(q.getStarredSeq(), q.getUserSeq());
    }

    @Override
    public int deleteByResource(StarredDeleteResourceQuery q) {
        return starredJpaRepository.deleteBySharedByUserSeqAndResourceSeq(q.getSharedByUserSeq(), q.getResourceSeq());
    }

    @Override
    public int deleteByResources(StarredDeleteResourcesQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return starredJpaRepository.deleteBySharedByUserSeqAndResourceSeqIn(q.getSharedByUserSeq(), q.getResourceSeqList());
    }

    /** 소유자(userSeq) 제외 + 선택적으로 excludeUserSeqList 제외. */
    @Override
    public int deleteByResourceExcludingOwner(StarredDeleteResourceQuery q) {
        if (isEmpty(q.getExcludeUserSeqList())) {
            return starredJpaRepository.deleteByResourceExcludingOwner(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeq());
        }
        return starredJpaRepository.deleteByResourceExcludingOwnerAndUsers(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeq(), q.getExcludeUserSeqList());
    }

    @Override
    public int deleteByResourcesExcludingOwner(StarredDeleteResourcesQuery q) {
        if (isEmpty(q.getResourceSeqList())) {
            return 0;
        }
        if (isEmpty(q.getExcludeUserSeqList())) {
            return starredJpaRepository.deleteByResourcesExcludingOwner(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeqList());
        }
        return starredJpaRepository.deleteByResourcesExcludingOwnerAndUsers(q.getUserSeq(), q.getSharedByUserSeq(), q.getResourceSeqList(), q.getExcludeUserSeqList());
    }

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자 + 선택적으로 excludeUserSeqList 제외. */
    @Override
    public int deleteOtherTenantByResources(StarredDeleteResourcesQuery q) {
        if (isEmpty(q.getResourceSeqList())) {
            return 0;
        }
        if (isEmpty(q.getExcludeUserSeqList())) {
            return starredJpaRepository.deleteOtherTenantByResources(q.getSharedByUserSeq(), q.getResourceSeqList(), q.getTenantSeq());
        }
        return starredJpaRepository.deleteOtherTenantByResourcesExcludingUsers(q.getSharedByUserSeq(), q.getResourceSeqList(), q.getTenantSeq(), q.getExcludeUserSeqList());
    }

    /** (user_seq, resource_seq) 파티션 키 행값 IN — 키의 (sharedByUserSeq, resourceSeq) 를 그 순서로 (user_seq, resource_seq) 에 맞춘다(구 SQL 과 같음), 1만 행씩 끊어 실행. */
    @Override
    public int deleteByShareTargets(StarredDeleteShareTargetsQuery q) {
        if (q.getKeys() == null || q.getKeys().isEmpty()) {
            return 0;
        }
        List<Row2<Integer, Long>> keys = q.getKeys().stream().map((PartitionKey k) -> row(k.sharedByUserSeq(), k.resourceSeq())).toList();
        return JooqBatches.executeInRowChunks(keys, chunk -> dsl.deleteFrom(TB_STARRED)
                .where(TB_STARRED.SHARED_BY_USER_SEQ.eq(q.getSharedByUserSeq()))
                .and(row(TB_STARRED.USER_SEQ, TB_STARRED.RESOURCE_SEQ).in(chunk))
                .execute());
    }

    /**
     * 사용자별 하위 목록 삭제 — 항목(사용자 × 리소스 목록)을 (user_seq, shared_by_user_seq, resource_seq) 행으로 펼쳐 행값 IN 한 문장
     * ({@link JooqBatches#executeInRowChunks} 묶음마다 한 번 왕복 — 구 배치는 IN 길이가 다른 항목마다 따로 묶였다). 빈 목록 항목은 건너뜀. 실패는 DELETE_FAIL(구 task 의 exceptionSupplier 와 동일).
     */
    @Override
    public void deleteByUserResources(List<StarredDeleteUserResourcesQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }
        try {
            List<Row3<Integer, Integer, Long>> rows = queries.stream()
                    .filter(q -> q.getResourceSeqList() != null && !q.getResourceSeqList().isEmpty())
                    .flatMap(q -> q.getResourceSeqList().stream().map(resourceSeq -> row(q.getUserSeq(), q.getSharedByUserSeq(), resourceSeq)))
                    .toList();
            JooqBatches.executeInRowChunks(rows, chunk -> dsl.deleteFrom(TB_STARRED)
                    .where(row(TB_STARRED.USER_SEQ, TB_STARRED.SHARED_BY_USER_SEQ, TB_STARRED.RESOURCE_SEQ).in(chunk))
                    .execute());
        } catch (RuntimeException e) {
            log.error("[별표 사용자별 리소스 삭제 실패]", e);
            throw ApiRuntimeException.type(StarredErrorCode.DELETE_FAIL);
        }
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return starredJpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        if (sharedByUserSeqList == null || sharedByUserSeqList.isEmpty()) {
            return 0;
        }
        return starredJpaRepository.deleteBySharedByUserSeqIn(sharedByUserSeqList);
    }

    @Override
    public int deleteAllSharedByTenant(Integer tenantSeq) {
        return starredJpaRepository.deleteAllSharedByTenant(tenantSeq);
    }

    /** shareTypes 는 ShareType name() 목록(호출부 계약) — enum 파라미터로 바꿔 넘긴다. */
    @Override
    public int deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (isEmpty(shareTypes)) {
            return 0;
        }
        return starredJpaRepository.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes.stream().map(ShareType::valueOf).toList());
    }

    @Override
    public int deleteOtherTenantSharedByTenant(Integer tenantSeq) {
        return starredJpaRepository.deleteOtherTenantSharedByTenant(tenantSeq);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}

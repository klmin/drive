package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.pinnedfolder.entity.PinnedFolder;
import com.thinkfree.drive.pinnedfolder.exception.PinnedFolderErrorCode;
import com.thinkfree.drive.pinnedfolder.query.*;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Row3;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_PINNED_FOLDER;
import static org.jooq.impl.DSL.row;

/**
 * tb_pinned_folder 쓰기 — JPA + 행값 IN DELETE 1종(jOOQ)(구 PinnedFolderMapper.xml 의 insert 1·FOR UPDATE 건수 1·delete 11). 읽기는 {@link DefaultPinnedFolderQueryRepository}.
 * <p>
 * {@code insertPinnedFolder} 는 {@link PinnedFolderJpaRepository#save}(IDENTITY 라 저장 순간 INSERT) — {@link PinnedFolder} 는 변경 메서드 없는 불변 엔티티(§1 규칙). identity PK·register_date(Instant) 가 엔티티에 채워진다.
 * {@code countByUserForUpdate} 는 저장 흐름 안의 잠금(사용자 행 PESSIMISTIC_WRITE → 별도 문장 COUNT JPQL).
 * DELETE 11종 중 10종은 JPQL {@code @Modifying}({@link PinnedFolderJpaRepository} — 조건부 다건 또는 로드 없는 키 삭제(삭제 행 수로 판정)라 load→remove 로 바꾸지 않음) — 선택 조건(excludeUserSeqList, 구 {@code andIfPresent})은 있음/없음 JPQL 두 벌 중 하나를 고르고,
 * 테넌트 조건은 User 서브쿼리, shareTypes 는 enum 으로 바꿔 넘긴다. IN 목록이 비면 쿼리 없이 0.
 * {@code deleteByUserResources} 는 (user_seq, shared_by_user_seq, resource_seq) 행값 IN(jOOQ — JPQL 은 튜플 목록을 파라미터 하나로 바인딩하지 못한다).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DefaultPinnedFolderRepository implements PinnedFolderRepository {

    private final PinnedFolderJpaRepository pinnedFolderJpaRepository;
    private final DSLContext dsl;

    @Override
    public void insertPinnedFolder(PinnedFolder entity) {
        pinnedFolderJpaRepository.save(entity);
    }

    /**
     * 사용자 행을 먼저 잠근 뒤 <b>별도 문장</b>으로 센다. PG(READ COMMITTED)는 문장마다 스냅샷을 새로 잡으므로, 잠금을 기다린 요청도
     * 앞 요청이 커밋한 고정 폴더까지 보고 센다. 고정 폴더 행을 잠그면서 같은 문장에서 세면(구 방식) 잠금 대기 뒤에도 문장 시작 시점
     * 스냅샷이라 2개 상태의 동시 요청 둘이 모두 2로 읽어 4개가 저장된다(코드 리뷰 P2, MySQL 8.4 는 3개). 0개일 때는 잠글 행조차 없었다.
     * 잠금 = {@link PinnedFolderJpaRepository#lockUser}({@code @Lock(PESSIMISTIC_WRITE)} JPQL — 행 잠금만 목적, 받은 사용자는 고치지 않음, 잠금은 트랜잭션 끝까지 유지. W3 10-13, 구 em.find+detach).
     * 사용자 행이 없으면 잠금 없이 센다(구 JdbcClient 와 같음).
     */
    @Override
    public int countByUserForUpdate(PinnedFolderCountQuery q) {
        pinnedFolderJpaRepository.lockUser(q.getUserSeq());
        return (int) pinnedFolderJpaRepository.countByUserSeq(q.getUserSeq());
    }

    @Override
    public int deletePinnedFolder(PinnedFolderDeleteQuery q) {
        return pinnedFolderJpaRepository.deleteByUserSeqAndPinnedFolderSeq(q.getUserSeq(), q.getPinnedFolderSeq());
    }

    @Override
    public int deleteByResource(PinnedFolderDeleteResourceQuery q) {
        return pinnedFolderJpaRepository.deleteBySharedByUserSeqAndResourceSeq(q.sharedByUserSeq(), q.resourceSeq());
    }

    @Override
    public int deleteByResources(PinnedFolderDeleteResourcesQuery q) {
        if (q.resourceSeqList() == null || q.resourceSeqList().isEmpty()) {
            return 0;
        }
        return pinnedFolderJpaRepository.deleteBySharedByUserSeqAndResourceSeqIn(q.sharedByUserSeq(), q.resourceSeqList());
    }

    /** 소유자(userSeq) 제외 + 선택적으로 excludeUserSeqList 제외. */
    @Override
    public int deleteByResourcesExcludingOwner(PinnedFolderDeleteResourcesQuery q) {
        if (isEmpty(q.resourceSeqList())) {
            return 0;
        }
        if (isEmpty(q.excludeUserSeqList())) {
            return pinnedFolderJpaRepository.deleteByResourcesExcludingOwner(q.userSeq(), q.sharedByUserSeq(), q.resourceSeqList());
        }
        return pinnedFolderJpaRepository.deleteByResourcesExcludingOwnerAndUsers(q.userSeq(), q.sharedByUserSeq(), q.resourceSeqList(), q.excludeUserSeqList());
    }

    /**
     * 사용자별 하위 목록 삭제 — 항목(사용자 × 리소스 목록)을 (user_seq, shared_by_user_seq, resource_seq) 행으로 펼쳐 행값 IN 한 문장
     * ({@link JooqBatches#executeInRowChunks} 묶음마다 한 번 왕복 — 구 배치는 IN 길이가 다른 항목마다 따로 묶였다). 빈 목록 항목은 건너뜀. 실패는 DELETE_FAIL(구 task 의 exceptionSupplier 와 동일).
     */
    @Override
    public void deleteByUserResources(List<PinnedFolderDeleteUserResourcesQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }
        try {
            List<Row3<Integer, Integer, Long>> rows = queries.stream()
                    .filter(q -> q.resourceSeqList() != null && !q.resourceSeqList().isEmpty())
                    .flatMap(q -> q.resourceSeqList().stream().map(resourceSeq -> row(q.userSeq(), q.sharedByUserSeq(), resourceSeq)))
                    .toList();
            JooqBatches.executeInRowChunks(rows, chunk -> dsl.deleteFrom(TB_PINNED_FOLDER)
                    .where(row(TB_PINNED_FOLDER.USER_SEQ, TB_PINNED_FOLDER.SHARED_BY_USER_SEQ, TB_PINNED_FOLDER.RESOURCE_SEQ).in(chunk))
                    .execute());
        } catch (RuntimeException e) {
            log.error("[고정 폴더 사용자별 리소스 삭제 실패]", e);
            throw ApiRuntimeException.type(PinnedFolderErrorCode.DELETE_FAIL);
        }
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return pinnedFolderJpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        if (sharedByUserSeqList == null || sharedByUserSeqList.isEmpty()) {
            return 0;
        }
        return pinnedFolderJpaRepository.deleteBySharedByUserSeqIn(sharedByUserSeqList);
    }

    @Override
    public int deleteAllSharedByTenant(Integer tenantSeq) {
        return pinnedFolderJpaRepository.deleteAllSharedByTenant(tenantSeq);
    }

    /** shareTypes 는 ShareType name() 목록(호출부 계약) — enum 파라미터로 바꿔 넘긴다. */
    @Override
    public int deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (isEmpty(shareTypes)) {
            return 0;
        }
        return pinnedFolderJpaRepository.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes.stream().map(ShareType::valueOf).toList());
    }

    @Override
    public int deleteOtherTenantSharedByTenant(Integer tenantSeq) {
        return pinnedFolderJpaRepository.deleteOtherTenantSharedByTenant(tenantSeq);
    }

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자 + 선택적으로 excludeUserSeqList 제외. */
    @Override
    public int deleteOtherTenantByResources(PinnedFolderDeleteResourcesQuery q) {
        if (isEmpty(q.resourceSeqList())) {
            return 0;
        }
        if (isEmpty(q.excludeUserSeqList())) {
            return pinnedFolderJpaRepository.deleteOtherTenantByResources(q.sharedByUserSeq(), q.resourceSeqList(), q.tenantSeq());
        }
        return pinnedFolderJpaRepository.deleteOtherTenantByResourcesExcludingUsers(q.sharedByUserSeq(), q.resourceSeqList(), q.tenantSeq(), q.excludeUserSeqList());
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}

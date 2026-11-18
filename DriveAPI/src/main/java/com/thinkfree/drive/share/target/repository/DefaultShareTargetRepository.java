package com.thinkfree.drive.share.target.repository;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.entity.ShareTarget;
import com.thinkfree.drive.share.target.exception.ShareTargetErrorCode;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteKeysQuery;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteResourceQuery;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteResourcesQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Row2;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_SHARE_TARGET;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.row;

/**
 * tb_share_target 쓰기 — 삭제 7종은 JPA(JPQL, {@link ShareTargetJpaRepository}), 나머지는 jOOQ(구 ShareTargetMapper.xml 의 insert·upsert(배치)·delete 8). 읽기는 {@link DefaultShareTargetQueryRepository}.
 * <p>
 * jOOQ 로 둔 것: 단건 INSERT 가 없고 전부 배치 INSERT / ON CONFLICT upsert(Hibernate 는 IDENTITY 를 배치하지 못함), (user_seq, share_target_seq) 행값 IN.
 * 테넌트 삭제 3종은 User 서브쿼리 JPQL, shareTypes(name() 목록)는 enum 으로 바꿔 넘긴다.
 * 배치는 {@link JooqBatches#batched} — 행마다 문장을 실행하면 같은 SQL 이 이어지는 동안 PreparedStatement 하나로 모아 {@code executeBatch}, 1000행씩(구 MyBatis 배치 executor 와 같이 처리 건수 = 항목 수, 실패는 INSERT_FAIL).
 * INSERT 는 컬럼을 고정한 {@code values()} — share_target_seq 는 항상 DEFAULT(XML {@code <choose>} 의 seq 지정 분기는 넘기는 호출부가 없어 뺐다).
 * 등록 감사는 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭)로 정해 바인딩만 한다(엔티티 write-back 없음 — 저장 뒤 엔티티를 읽는 호출부 0, W9). upsert 의 update_* 는 converter 가 넣어 둔 값을 그대로 바인딩(인터셉터는 INSERT 문에서 update_* 를 채우지 않았음).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DefaultShareTargetRepository implements ShareTargetRepository {

    private final ShareTargetJpaRepository jpaRepository;
    private final DSLContext dsl;

    // ------------------------------------------------------------------ 배치 INSERT / UPSERT

    @Override
    public int insertAll(List<ShareTarget> entities) {
        int result = batch(entities, false);
        log.info("[공유 대상 데이터 저장] : {}건", result);
        return result;
    }

    @Override
    public int upsertAll(List<ShareTarget> entities) {
        int result = batch(entities, true);
        log.info("[공유 대상 데이터 저장/업데이트] : {}건", result);
        return result;
    }

    /** 처리 건수 = 항목 수. 실패는 INSERT_FAIL(구 배치 task 의 exceptionSupplier 와 동일). */
    private int batch(List<ShareTarget> entities, boolean upsert) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        try {
            JooqBatches.batched(dsl, entities, (batchedDsl, e) -> write(batchedDsl, e, upsert));
        } catch (RuntimeException e) {
            log.error("[공유 대상 배치 저장 실패]", e);
            throw ApiRuntimeException.type(ShareTargetErrorCode.INSERT_FAIL);
        }
        return entities.size();
    }

    /**
     * 한 행 INSERT(upsert 면 ON CONFLICT (user_seq, resource_seq, shared_by_user_seq) 때 6 권한 + update_*) — batched 안에서는 실행이 모였다가 한 번에 나간다.
     * share_target_seq 는 항상 DEFAULT(identity). 등록 감사는 {@link JdbcAudit} 로 정해 바인딩만 한다(저장 뒤 엔티티를 읽는 호출부가 없어 write-back 없음, W9)
     */
    private static void write(DSLContext batchedDsl, ShareTarget e, boolean upsert) {
        var insert = batchedDsl.insertInto(TB_SHARE_TARGET, TB_SHARE_TARGET.USER_SEQ, TB_SHARE_TARGET.SHARE_SEQ, TB_SHARE_TARGET.RESOURCE_SEQ,
                        TB_SHARE_TARGET.SHARED_BY_USER_SEQ, TB_SHARE_TARGET.SHARE_DATE,
                        TB_SHARE_TARGET.CAN_VIEW, TB_SHARE_TARGET.CAN_EDIT, TB_SHARE_TARGET.CAN_DOWNLOAD,
                        TB_SHARE_TARGET.CAN_UPLOAD, TB_SHARE_TARGET.CAN_DELETE, TB_SHARE_TARGET.CAN_SHARE,
                        TB_SHARE_TARGET.REGISTER_USER_SEQ, TB_SHARE_TARGET.REGISTER_DATE)
                .values(e.getUserSeq(), e.getShareSeq(), e.getResourceSeq(),
                        e.getSharedByUserSeq(), e.getShareDate(),
                        e.getCanView(), e.getCanEdit(), e.getCanDownload(),
                        e.getCanUpload(), e.getCanDelete(), e.getCanShare(),
                        JdbcAudit.userSeqOr(e.getRegisterUserSeq()), JdbcAudit.nowOr(e.getRegisterDate()));
        if (!upsert) {
            insert.execute();
            return;
        }
        insert.onConflict(TB_SHARE_TARGET.USER_SEQ, TB_SHARE_TARGET.RESOURCE_SEQ, TB_SHARE_TARGET.SHARED_BY_USER_SEQ)
                .doUpdate()
                .set(TB_SHARE_TARGET.CAN_VIEW, excluded(TB_SHARE_TARGET.CAN_VIEW))
                .set(TB_SHARE_TARGET.CAN_EDIT, excluded(TB_SHARE_TARGET.CAN_EDIT))
                .set(TB_SHARE_TARGET.CAN_DOWNLOAD, excluded(TB_SHARE_TARGET.CAN_DOWNLOAD))
                .set(TB_SHARE_TARGET.CAN_UPLOAD, excluded(TB_SHARE_TARGET.CAN_UPLOAD))
                .set(TB_SHARE_TARGET.CAN_DELETE, excluded(TB_SHARE_TARGET.CAN_DELETE))
                .set(TB_SHARE_TARGET.CAN_SHARE, excluded(TB_SHARE_TARGET.CAN_SHARE))
                .set(TB_SHARE_TARGET.UPDATE_USER_SEQ, e.getUpdateUserSeq())
                .set(TB_SHARE_TARGET.UPDATE_DATE, e.getUpdateDate())
                .execute();
    }

    // ------------------------------------------------------------------ DELETE

    @Override
    public int deleteByResource(ShareTargetDeleteResourceQuery q) {
        return jpaRepository.deleteBySharedByUserSeqAndResourceSeq(q.getSharedByUserSeq(), q.getResourceSeq());
    }

    @Override
    public int deleteByResources(ShareTargetDeleteResourcesQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteBySharedByUserSeqAndResourceSeqIn(q.getSharedByUserSeq(), q.getResourceSeqList());
    }

    /** (user_seq, share_target_seq) 파티션 키 튜플 IN — NamedParameter 가 Object[] 목록을 (?, ?), (?, ?) 로 펼친다. */
    @Override
    public int deleteByShareTargetDeleteKeys(ShareTargetDeleteKeysQuery q) {
        if (q.getDeleteKeys() == null || q.getDeleteKeys().isEmpty()) {
            return 0;
        }
        List<Row2<Integer, Long>> keys = q.getDeleteKeys().stream().map((ShareTargetDeleteKey k) -> row(k.userSeq(), k.shareTargetSeq())).toList();
        return JooqBatches.executeInRowChunks(keys, chunk -> dsl.deleteFrom(TB_SHARE_TARGET)
                .where(row(TB_SHARE_TARGET.USER_SEQ, TB_SHARE_TARGET.SHARE_TARGET_SEQ).in(chunk))
                .and(TB_SHARE_TARGET.SHARED_BY_USER_SEQ.eq(q.getSharedByUserSeq()))
                .execute());
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
    public int deleteByTenant(Integer tenantSeq) {
        return jpaRepository.deleteByTenant(tenantSeq);
    }

    /** shareTypes 는 ShareType name() 목록(호출부 계약) — enum 파라미터로 바꿔 넘긴다. */
    @Override
    public int deleteByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByTenantAndShareTypes(tenantSeq, shareTypes.stream().map(ShareType::valueOf).toList());
    }

    @Override
    public int deleteOtherTenantByTenant(Integer tenantSeq) {
        return jpaRepository.deleteOtherTenantByTenant(tenantSeq);
    }
}

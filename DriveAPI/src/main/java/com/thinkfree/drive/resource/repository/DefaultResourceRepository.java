package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.global.jooq.JooqConditions;
import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.resource.data.ResourceCopyNumbers;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.*;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.when;

/**
 * tb_resource 쓰기 + 쓰기 흐름 안의 읽기(잠금·고칠 행 로드·복사본 번호 집계). 읽기는 {@link DefaultResourceQueryRepository}.
 * <ul>
 *   <li>INSERT = {@code save}(IDENTITY 라 그 순간 INSERT·id·등록 감사, 저장 뒤 관리 상태 — 호출부가 바로 고치는 저장 이름·폴더 경로는 커밋 때 변경 감지, W10).
 *       파일/폴더 컬럼 차이는 엔티티의 {@code @DynamicInsert}</li>
 *   <li>단건 변경 = Service 가 {@link #findByUserSeqAndResourceSeq}·{@link #findVisibleByUserSeqAndResourceSeq}·{@link #lockVisibleByUserSeqAndResourceSeq} 로 받은 관리 엔티티를
 *       변경 메서드로 고친다(W10 — 구 리포 안 로드 → 변경 → flush → detach 와 단건 JPQL 삭제). 감사는 Auditing. 파티션 키(user_seq) 변경(다른 소유자로 이동)도 변경 감지</li>
 *   <li>JPQL {@code @Modifying} 유지 = 덮어쓰기·되돌리기(감사 값 지정·현재 값 조건), 하위 파일 소유자 일괄 이전·만료 잠금 일괄 해제·삭제(조건부 다건),
 *       하위 경로 치환·하위 복원·폴더 휴지통·버전 롤백(W10d — 구 JdbcClient 동적 SET·UPDATE … FROM 을 case·coalesce·서브쿼리로)</li>
 *   <li>jOOQ = 집계 + FOR UPDATE 서브쿼리(복사본 번호 — JPQL 불가). 버전 작업 잠금은 {@code @Lock} 생성자 식(W10)</li>
 * </ul>
 * 수정 감사는 {@link UpdateStamp}({@code JdbcAudit} 규칙 — 시큐리티 사용자 우선, 없으면 선언 값; 시각은 선언 값 없으면 현재 시각 초 절삭).
 * 인자 엔티티·쿼리 객체에 감사 값을 되써 주지 않는다(W10 H7 — 덮어쓰기만 쓴 stamp 를 반환). record 쿼리(잠금·만료·되돌리기)와 버전 롤백은 호출부 값 그대로(이전 값 복원 — 감사를 거치지 않는다).
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourceRepository implements ResourceRepository {

    private final ResourceJpaRepository jpaRepository;
    private final DSLContext dsl;

    // ------------------------------------------------------------------ INSERT

    /** 파일·폴더 INSERT — IDENTITY 라 이 호출 안에서 INSERT·id 채움, 반환 인스턴스는 관리 상태(이어서 고친 값은 커밋 때 변경 감지). */
    @Override
    public Resource save(Resource e) {
        return jpaRepository.save(e);
    }

    // ------------------------------------------------------------------ 고칠 행 로드(관리 엔티티)

    @Override
    public Resource findByUserSeqAndResourceSeq(int userSeq, long resourceSeq) {
        return jpaRepository.findByUserSeqAndResourceSeq(userSeq, resourceSeq).orElse(null);
    }

    @Override
    public Resource findVisibleByUserSeqAndResourceSeq(int userSeq, long resourceSeq) {
        return jpaRepository.findVisibleByUserSeqAndResourceSeq(userSeq, resourceSeq, UserStatus.displayableStatuses()).orElse(null);
    }

    @Override
    public Resource lockVisibleByUserSeqAndResourceSeq(int userSeq, long resourceSeq) {
        return jpaRepository.lockVisibleByUserSeqAndResourceSeq(userSeq, resourceSeq, UserStatus.displayableStatuses()).orElse(null);
    }

    /**
     * 관리 엔티티의 미반영 변경을 지금 DB 로 내보낸다 — 이어서 같은 트랜잭션에서 그 행을 <b>JdbcClient</b> 로 읽는 곳이 있을 때만(JdbcClient 는 자동 flush 가 없음, W10 결정 Y·R3).
     * 예: 이동·복원·이름 변경 뒤 감사의 공유자 경로(재귀 CTE)·이동 후처리. 호출부에 이유 주석 필수.
     */
    @Override
    public void flush() {
        jpaRepository.flush();
    }

    // ------------------------------------------------------------------ FOR UPDATE

    /**
     * PG 는 집계 + FOR UPDATE 불가 → 대상 행을 서브쿼리에서 잠근 뒤 바깥에서 집계(한 문장 — JPQL {@code @Lock} 은 바깥 쿼리에만 걸려 표현 불가).
     * resourceSeq 가 있으면 자기 행은 빼고, 부모가 없으면 루트({@code parent_folder_seq IS NULL}).
     */
    @Override
    public ResourceCopyNumbers lockMaxCopyNumbers(ResourceLockQuery q) {
        Condition condition = TB_RESOURCE.USER_SEQ.eq(q.getUserSeq())
                .and(TB_RESOURCE.RESOURCE_TYPE.eq(q.getResourceType()))
                .and(TB_RESOURCE.IS_DELETE.eq(q.getIsDelete()))
                .and(q.getResourceSeq() == null ? noCondition() : TB_RESOURCE.RESOURCE_SEQ.ne(q.getResourceSeq()))
                .and(q.getParentFolderSeq() == null ? TB_RESOURCE.PARENT_FOLDER_SEQ.isNull() : TB_RESOURCE.PARENT_FOLDER_SEQ.eq(q.getParentFolderSeq()))
                .and(TB_RESOURCE.RESOURCE_NAME.eq(q.getOriginalFileName()).or(TB_RESOURCE.ORIGINAL_FILE_NAME.eq(q.getOriginalFileName())));
        Table<?> s = select(TB_RESOURCE.RESOURCE_NAME, TB_RESOURCE.ORIGINAL_FILE_NAME, TB_RESOURCE.COPY_NUMBER)
                .from(TB_RESOURCE)
                .where(condition)
                .forUpdate()
                .asTable("s");
        Field<Integer> copyNumber = s.field(TB_RESOURCE.COPY_NUMBER);
        return dsl.select(
                        max(when(Objects.requireNonNull(s.field(TB_RESOURCE.RESOURCE_NAME))
                                        .eq(q.getOriginalFileName()), copyNumber)),
                        max(when(Objects.requireNonNull(s.field(TB_RESOURCE.ORIGINAL_FILE_NAME))
                                        .eq(q.getOriginalFileName()), copyNumber)))
                .from(s)
                .fetchSingle(r -> new ResourceCopyNumbers(r.value1(), r.value2()));
    }

    /**
     * 버전 작업 전 미삭제 리소스 행 잠금 — {@code @Lock} JPQL 생성자 식(W10, 구 JdbcClient FOR UPDATE + 부분 엔티티). 값으로 돌려준다: 잠금 직후 같은 행을
     * 벌크 JPQL 로 되돌리는 경로({@link #rollbackResourceVersionData} — 1차 캐시를 안 고침)가 있어 관리 엔티티로 쥐면 캐시가 옛 값이 된다. 고칠 엔티티는 그 뒤에 따로 로드한다.
     * (구 (parent, name, type) 잠금 분기는 main 호출부가 전부 resourceSeq 를 넘겨 삭제 — W10 결정 K)
     */
    @Override
    public ResourceDetailData lockResourceForVersioning(long resourceSeq, int userSeq) {
        return jpaRepository.lockDetailByUserSeqAndResourceSeq(userSeq, resourceSeq, false).orElse(null);
    }

    /** 휴지통 보내기·영구 삭제 전 대상 잠금 + 상세(미삭제·소유 계정 노출 가능) — {@code @Lock} DTO 생성자 식. 없으면 null */
    @Override
    public ResourceDetailData lockVisibleActiveDetail(int userSeq, long resourceSeq) {
        return jpaRepository.lockVisibleActiveDetail(userSeq, resourceSeq, UserStatus.displayableStatuses()).orElse(null);
    }

    /** 폴더 하위 트리 잠금 — {@code @Lock} 스칼라 JPQL(엔티티를 컨텍스트에 올리지 않음). 구 TrashUseCase 직접 JdbcTemplate FOR UPDATE 대체 */
    @Override
    public List<Long> lockSubtree(int userSeq, String fullPathSeq) {
        return jpaRepository.lockSubtreeResourceSeqs(userSeq, fullPathSeq + "%");
    }

    /** 행 잠금만 — {@code @Lock} 스칼라 JPQL(엔티티를 컨텍스트에 올리지 않음) */
    @Override
    public void lockRow(int userSeq, long resourceSeq) {
        jpaRepository.lockResourceSeq(userSeq, resourceSeq);
    }

    // ------------------------------------------------------------------ UPDATE — JPQL(@Modifying 유지 예외)

    /**
     * 덮어쓰기 — {@code @Modifying} 유지(같은 크기면 바뀌는 컬럼이 감사뿐이라 변경 감지로는 수정일이 안 바뀜, 되돌리기 {@link #revertOverwrite} 의 "현재 값 그대로일 때만" 조건과 짝 — 계획 Q7).
     * 실제로 쓴 감사 값(stamp)을 돌려준다 — 되돌리기 조건과 이벤트 시각이 이 값을 쓴다(W10 H7, 구 인자 엔티티 write-back). 없는 행이면 null.
     */
    @Override
    public UpdateStamp overwrite(int userSeq, long resourceSeq, long sizeByte) {
        UpdateStamp stamp = UpdateStamp.of(null, null);
        return jpaRepository.overwrite(userSeq, resourceSeq, sizeByte, stamp) == 0 ? null : stamp;
    }

    /** 폴더 이동의 하위 파일 소유자 일괄 이전 — 조건부 다건(행마다 로드할 이유 없음) */
    @Override
    public int updateUserSeqForResources(ResourcesUserUpdateQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.updateUserSeqForResources(q.getUserSeq(), q.getResourceSeqList(), q.getChangeUserSeq(),
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    /** 덮어쓰기 되돌리기 — 감사 값 직접 지정 + 현재 값 조건(변경 감지는 Auditing 이 덮어써 불가) */
    @Override
    public int revertOverwrite(ResourceRevertOverWriteQuery q) {
        return jpaRepository.revertOverwrite(q.userSeq(), q.resourceSeq(), q.restoreSizeByte(), q.restoreUpdateUserSeq(), q.restoreUpdateDate(),
                q.currentSizeByte(), q.currentUpdateUserSeq(), q.currentUpdateDate());
    }

    /** 만료 잠금 일괄 해제(스케줄러) — 조건부 다건 */
    @Override
    public int updateExpiredLocks(ResourceLockExpireQuery q) {
        return jpaRepository.updateExpiredLocks(q.threshold(), Boolean.TRUE, Boolean.FALSE, q.updateUserSeq(), q.updateDate());
    }

    /** 공유 링크 토큰 — 단건 JPQL(리스너를 타지 않아 수정 감사 그대로, 변경 감지로 바꾸면 Auditing 이 수정자·수정일을 덮는다) */
    @Override
    public int applyShareTokenIfAbsent(int userSeq, long resourceSeq, String shareToken) {
        return jpaRepository.applyShareTokenIfAbsent(userSeq, resourceSeq, shareToken);
    }

    // ------------------------------------------------------------------ UPDATE — JPQL 벌크(하위 일괄·롤백, W10d — 구 JdbcClient 동적 SET·UPDATE … FROM)
    // 벌크 JPQL 은 1차 캐시를 고치지 않는다 — 대상은 자식 행·롤백 행이라 호출부가 쥔 관리 엔티티(자기 행)와 겹치지 않는다. 실행 전 Hibernate 가 미반영 변경을 자동 flush

    /** 폴더 휴지통 — 자기 자신 + full_path_seq 접두 폴더들의 자식(미삭제만), 자기 행의 삭제 부모는 NULL. 감사 = {@link UpdateStamp} */
    @Override
    public int updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery q) {
        return jpaRepository.updateIsDeleteByFullPathSeq(q.getUserSeq(), q.getResourceSeq(), q.getFullPathSeq(), q.getIsDelete(), Boolean.FALSE,
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    /**
     * 버전 데이터 되돌리기(감사 보정 없음 — 넘어온 값 그대로).
     * 저장 실패 롤백({@code restoreUpdateMetadata})은 수정자·수정일을 NULL 포함 저장 전 값으로 복원하고,
     * 미완료 버전 정리는 값이 있을 때만 바꾼다(구 XML {@code <set>} — JPQL 은 coalesce).
     */
    @Override
    public int rollbackResourceVersionData(ResourceVersionRollbackQuery q) {
        if (q.isRestoreUpdateMetadata()) {
            return jpaRepository.rollbackVersionDataRestoringUpdate(q.getUserSeq(), q.getResourceSeq(), q.getLatestOwnerSeq(), q.getCurrentVersionNumber(),
                    q.getVersionRegisterDate(), q.getSizeByte(), q.getUpdateUserSeq(), q.getUpdateDate());
        }
        return jpaRepository.rollbackVersionData(q.getUserSeq(), q.getResourceSeq(), q.getLatestOwnerSeq(), q.getCurrentVersionNumber(),
                q.getVersionRegisterDate(), q.getSizeByte(), q.getUpdateUserSeq(), q.getUpdateDate());
    }

    /** 폴더 이동/이름변경 후 하위 경로 치환 — full_path 는 항상, full_path_seq 는 새/옛 seq 경로가 둘 다 있을 때, user_seq 는 changeUserSeq 가 있을 때. 자기 행은 제외(자기는 변경 감지). */
    @Override
    public int updateFullPathForChildFolders(ResourcePathUpdateQuery q) {
        return jpaRepository.updateFullPathForChildFolders(q.getUserSeq(), q.getResourceSeq(), q.getOldFullPath(), q.getNewFullPath(),
                q.getOldFullPathSeq(), seqPathToReplace(q.getOldFullPathSeq(), q.getNewFullPathSeq()), q.getChangeUserSeq(), q.getIsDelete(),
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    /** 휴지통 복원 하위 — 파일은 경로 NULL, 폴더는 경로 치환. full_path_seq 는 새/옛 seq 경로가 둘 다 있을 때만. 자식 행만(복원 폴더 자신은 delete_parent 가 NULL 이라 안 걸림 — 자신은 변경 감지). */
    @Override
    public int restoreChildResources(ResourceRestoreChildQuery q) {
        // 하위는 복원 폴더와 같은 user_seq — 파티션 조건(5차 패스 E1)
        return jpaRepository.restoreChildResources(q.getUserSeq(), q.getResourceSeq(), q.getOldFullPath(), q.getNewFullPath(),
                q.getOldFullPathSeq(), seqPathToReplace(q.getOldFullPathSeq(), q.getNewFullPathSeq()), ResourceType.FILE, Boolean.TRUE, Boolean.FALSE,
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    /** 치환할 새 seq 경로 — 새/옛이 둘 다 있을 때만({@link JooqConditions#present} 두 개 조건), 아니면 null(JPQL 이 full_path_seq 를 그대로 둔다) */
    private static String seqPathToReplace(String oldFullPathSeq, String newFullPathSeq) {
        return JooqConditions.present(newFullPathSeq) && JooqConditions.present(oldFullPathSeq) ? newFullPathSeq : null;
    }

    // ------------------------------------------------------------------ DELETE — JPQL

    @Override
    public int delete(ResourceDeleteQuery q) {
        if (q.getResourceSeq() == null) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqAndResourceSeqIn(q.getUserSeq(), List.of(q.getResourceSeq()));
    }

    @Override
    public int deleteByResourceSeqList(ResourceDeleteSeqListQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqAndResourceSeqIn(q.getUserSeq(), q.getResourceSeqList());
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }
}

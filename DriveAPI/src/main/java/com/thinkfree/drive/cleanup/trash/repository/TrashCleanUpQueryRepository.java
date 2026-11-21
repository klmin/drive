package com.thinkfree.drive.cleanup.trash.repository;

import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;

import java.time.Instant;
import java.util.List;

/**
 * 휴지통 영구삭제 대상 읽기(구 TrashCleanUpMapper.xml findTrashCleanUpTargets) — {@code DefaultTrashCleanUpService} 가 주입. 쓰기 없음.
 * tb_trash ⋈ tb_resource 루트 + delete_parent_folder_seq 하위(UNION ALL) ⋈ tb_resource_version(LEFT) ⋈ tb_user 의 비엔티티 조인을
 * 사용자 → 리소스 → 버전 3단으로 묶는 DTO 목록이라 JdbcClient.
 */
public interface TrashCleanUpQueryRepository {

    /** 스케줄러: 휴지통 등록일이 {@code thresholdDate} 이전인 루트와 그 하위(is_delete = isDelete). */
    List<TrashCleanUpTargetsData> findTrashCleanUpTargets(TrashCleanUpByDateQuery query);

    /** 휴지통 비우기: 사용자 {@code userSeq} 의 휴지통 루트 전부와 그 하위(is_delete = isDelete). */
    List<TrashCleanUpTargetsData> findTrashCleanUpTargets(TrashCleanUpByUserQuery query);

    /** 휴지통 비우기 대상 고정: 사용자 {@code userSeq} 의 지금 휴지통 행(trash_seq) 전부, trash_seq 순. */
    List<Long> findTrashSeqs(Integer userSeq);

    /** 사용자 {@code userSeq} 의 휴지통 행 중 {@code trashSeqs} 에 든 것이 지금 몇 개 남았나 — 비우기 묶음이 잠금 전후로 비교한다. */
    int countTrashRows(Integer userSeq, List<Long> trashSeqs);

    /** 스케줄러 자동정리 잠금 대상: 휴지통 등록일이 {@code thresholdDate} 이전인 행이 있는 사용자, user_seq 순(null 이면 빈 목록). */
    List<Integer> findTrashUserSeqs(Instant thresholdDate);
}

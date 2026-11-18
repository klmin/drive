package com.thinkfree.drive.trash.repository;

import com.thinkfree.drive.trash.entity.Trash;
import com.thinkfree.drive.trash.query.TrashDeleteQuery;
import com.thinkfree.drive.trash.query.TrashDeleteResourcesQuery;

import java.util.List;

/**
 * tb_trash 쓰기 — {@code DefaultTrashService}·휴지통 정리 배치({@code DefaultTrashCleanUpService})가 주입. 읽기(목록)는 {@link TrashQueryRepository}.
 */
public interface TrashRepository {

    /** INSERT — identity 키·register_* 가 엔티티에 채워진다. 반환 1 */
    int save(Trash trash);

    /** 사용자 × 리소스 1건 삭제(복원·영구 삭제) */
    int delete(TrashDeleteQuery query);

    /** 사용자 × 리소스 목록 삭제(휴지통 정리 배치) — 빈 목록이면 0 */
    int deleteByResources(TrashDeleteResourcesQuery query);

    int deleteByUserSeqList(List<Integer> userSeqList);
}

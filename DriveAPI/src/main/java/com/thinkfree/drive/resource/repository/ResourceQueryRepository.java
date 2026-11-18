package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.resource.data.*;
import com.thinkfree.drive.resource.query.*;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). {@code *Data} DTO·스칼라 — JPQL(단건·생성자 식)·JdbcClient(목록·재귀 CTE·커서). <b>엔티티를 반환하지 않는다</b>(W10c — 고칠 행 로드는 쓰기 리포의 관리 엔티티).
 * 쓰기와 쓰기 흐름 안의 읽기(FOR UPDATE 잠금·복사본 번호 집계)는 {@link ResourceRepository}.
 */
public interface ResourceQueryRepository {

    /** 내 드라이브 목록 — 재귀 CTE(조상 공유 상속)·동적 커서/정렬({@link ResourceJooq}·{@link ResourceCursorJooq}) */
    List<ResourceData> findAll(ResourceListQuery query);
    ResourceData findByIdAndIsDelete(ResourceFindQuery query);
    /** 소유자 기준 단건(선택 조건 resourceType·isDelete, 소유 계정 노출 가능 상태) — DTO(10-12 단계 4). 없으면 null */
    ResourceDetailData findDetail(ResourceFindQuery query);
    boolean existsResourceName(ResourceExistsQuery query);
    ResourceExistsData findResourceByExists(ResourceExistsQuery query);
    boolean existsByShareToken(String shareToken);
    ResourceTokenAccessData findResourceByShareToken(String shareToken, boolean isDelete);
    ResourceLinkTokenData findShareTokenForLink(int userSeq, long resourceSeq);
    List<ResourceDeleteParentData> findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery query);
    List<ResourceHierarchyData> findResourceHierarchy(ResourceHierarchyQuery query);
    List<ResourceHierarchyData> findResourceHierarchyByPartitionKeys(ResourceHierarchyPartitionKeysQuery query);
    List<ResourceVersionHierarchyData> findResourceVersionsByFolderHierarchy(ResourceVersionHierarchyQuery query);
    List<ResourceTreeData> findResourceTree(ResourceFindQuery query);
    List<ResourcePathData> findFolderAndDescendants(ResourcePathQuery query);
    List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList);
    ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq);
    /** 파일이면 자기 잠금, 폴더면 하위 트리에 잠금 상태 {@code isLocked} 인 리소스가 하나라도 있는지 */
    boolean isLockedResource(ResourceLockUnlockQuery query);
}

package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.pinnedfolder.entity.PinnedFolder;
import com.thinkfree.drive.pinnedfolder.query.*;

import java.util.List;

/**
 * 쓰기 전용(Service 가 주입) — INSERT 는 JPA save(identity PK·register_date 가 엔티티에 채워짐), 저장 흐름 안의 사용자 행 잠금(@Lock) 후 건수·DELETE 11종은 JPQL.
 * 읽기(목록·존재 여부)는 {@link PinnedFolderQueryRepository}.
 */
public interface PinnedFolderRepository {

    void insertPinnedFolder(PinnedFolder entity);
    /** 사용자의 고정 폴더 행을 잠근 뒤 건수(최대 개수 검사) */
    int countByUserForUpdate(PinnedFolderCountQuery query);
    int deletePinnedFolder(PinnedFolderDeleteQuery query);
    int deleteByResource(PinnedFolderDeleteResourceQuery query);
    int deleteByResources(PinnedFolderDeleteResourcesQuery query);
    int deleteByResourcesExcludingOwner(PinnedFolderDeleteResourcesQuery query);
    /** 항목별 호출(구 MyBatis 배치 task), 실패는 DELETE_FAIL */
    void deleteByUserResources(List<PinnedFolderDeleteUserResourcesQuery> queries);
    int deleteByUserSeqList(List<Integer> userSeqList);
    int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList);
    int deleteAllSharedByTenant(Integer tenantSeq);
    int deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);
    int deleteOtherTenantSharedByTenant(Integer tenantSeq);
    int deleteOtherTenantByResources(PinnedFolderDeleteResourcesQuery query);
}

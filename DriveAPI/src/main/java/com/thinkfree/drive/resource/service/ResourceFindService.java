package com.thinkfree.drive.resource.service;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.data.*;
import com.thinkfree.drive.resource.query.*;

import java.util.List;


public interface ResourceFindService {
    
    List<ResourceData> findAll(ResourceListQuery query);
    void existsResourceName(ResourceExistsQuery query);
    ResourceExistsData findResourceByExists(ResourceExistsQuery query);
    /** 소유자 기준 단건 DTO(항상 DB 현재 값, 10-12 단계 4). 없으면 RESOURCE NOT_FOUND */
    ResourceDetailData findDetail(ResourceFindQuery query);
    /** 없으면 예외 대신 null을 반환한다. 조회 실패가 트랜잭션을 되돌려선 안 되는 경우(감사 로그 경로 조회 등)에 사용한다. */
    ResourceDetailData findDetailOrNull(ResourceFindQuery query);
    boolean existsByResource(ResourceFindQuery query);
    boolean existsByShareToken(String shareToken);
    ResourceTokenAccessData findResourceByShareToken(String shareToken, boolean isDelete);
    ResourceData findByIdAndIsDelete(ResourceFindQuery query);
    List<ResourceDeleteParentData> findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery query);
    List<ResourceHierarchyData> findResourceHierarchy(ResourceHierarchyQuery query);
    List<ResourceHierarchyData> findResourceHierarchyByPartitionKeys(List<PartitionKey> keys, Boolean isDelete);
    List<ResourceVersionHierarchyData> findResourceVersionsByFolderHierarchy(long resourceSeq, int userSeq);
    List<ResourceTreeData> findResourceTree(ResourceFindQuery query);
    List<ResourcePathData> findFolderAndDescendants(ResourcePathQuery query);
    List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList);
    ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq);
    
}

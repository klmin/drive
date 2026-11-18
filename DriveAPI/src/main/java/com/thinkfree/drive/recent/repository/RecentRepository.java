package com.thinkfree.drive.recent.repository;

import com.thinkfree.drive.recent.entity.Recent;
import com.thinkfree.drive.recent.query.*;

import java.util.List;

/**
 * 쓰기 전용(Service 가 주입) — INSERT … ON CONFLICT upsert·DELETE 12종(고정 조건 삭제 5종은 JPQL, 나머지 JdbcClient). 읽기(목록)는 {@link RecentQueryRepository}.
 */
public interface RecentRepository {

    /** upsert — (user_seq, resource_seq, shared_by_user_seq) 충돌 시 update_date 만 갱신. 등록 감사 값은 엔티티에도 반영 */
    int save(Recent entity);
    int deleteByResource(RecentDeleteResourceQuery query);
    int deleteByResources(RecentDeleteResourcesQuery query);
    int deleteByResourceExcludingOwner(RecentDeleteResourceQuery query);
    int deleteByResourcesExcludingOwner(RecentDeleteResourcesQuery query);
    int deleteOtherTenantByResources(RecentDeleteResourcesQuery query);
    /** (user_seq, resource_seq) 파티션 키 튜플 IN 삭제 */
    int deleteByShareTargets(RecentDeleteShareTargetsQuery query);
    /** 항목별 호출(구 MyBatis 배치 task), 실패는 DELETE_FAIL */
    void deleteByUserResources(List<RecentDeleteUserResourcesQuery> queries);
    int deleteByUserSeqList(List<Integer> userSeqList);
    int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList);
    int deleteAllSharedByTenant(Integer tenantSeq);
    int deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);
    int deleteOtherTenantSharedByTenant(Integer tenantSeq);
}

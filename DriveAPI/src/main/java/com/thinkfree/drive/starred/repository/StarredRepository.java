package com.thinkfree.drive.starred.repository;

import com.thinkfree.drive.starred.entity.Starred;
import com.thinkfree.drive.starred.query.*;

import java.util.List;

/**
 * 쓰기 전용(Service 가 주입) — INSERT 는 JPA 위임(identity PK·감사 컬럼이 엔티티에 채워짐, uniq_starred 위반은 DataIntegrityViolationException), DELETE 13종은 JdbcClient.
 * 읽기는 {@link StarredQueryRepository}.
 */
public interface StarredRepository {

    void save(Starred entity);
    int delete(StarredDeleteQuery query);
    int deleteByResource(StarredDeleteResourceQuery query);
    int deleteByResources(StarredDeleteResourcesQuery query);
    int deleteByResourceExcludingOwner(StarredDeleteResourceQuery query);
    int deleteByResourcesExcludingOwner(StarredDeleteResourcesQuery query);
    int deleteOtherTenantByResources(StarredDeleteResourcesQuery query);
    /** (user_seq, resource_seq) 파티션 키 튜플 IN 삭제 */
    int deleteByShareTargets(StarredDeleteShareTargetsQuery query);
    /** 항목별 호출(구 MyBatis 배치 task), 실패는 DELETE_FAIL */
    void deleteByUserResources(List<StarredDeleteUserResourcesQuery> queries);
    int deleteByUserSeqList(List<Integer> userSeqList);
    int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList);
    int deleteAllSharedByTenant(Integer tenantSeq);
    int deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);
    int deleteOtherTenantSharedByTenant(Integer tenantSeq);
}

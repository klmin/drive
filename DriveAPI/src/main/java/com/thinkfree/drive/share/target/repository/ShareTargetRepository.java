package com.thinkfree.drive.share.target.repository;

import com.thinkfree.drive.share.target.entity.ShareTarget;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteKeysQuery;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteResourceQuery;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteResourcesQuery;

import java.util.List;

/**
 * 쓰기 전용(Service 가 주입) — 배치 INSERT / ON CONFLICT upsert(처리 건수 = 항목 수)·DELETE 8종(고정 조건 삭제 4종은 JPQL, 나머지 JdbcClient). 읽기는 {@link ShareTargetQueryRepository}.
 */
public interface ShareTargetRepository {

    /** 배치 INSERT — 등록 감사 값은 엔티티에도 반영. 빈 목록은 0 */
    int insertAll(List<ShareTarget> entities);
    /** 배치 INSERT … ON CONFLICT (user_seq, resource_seq, shared_by_user_seq) DO UPDATE(권한 6컬럼 + update_*) */
    int upsertAll(List<ShareTarget> entities);
    int deleteByResource(ShareTargetDeleteResourceQuery query);
    int deleteByResources(ShareTargetDeleteResourcesQuery query);
    /** (user_seq, share_target_seq) 파티션 키 튜플 IN 삭제 */
    int deleteByShareTargetDeleteKeys(ShareTargetDeleteKeysQuery query);
    int deleteByUserSeqList(List<Integer> userSeqList);
    int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList);
    /** 테넌트 소유자가 공유한 대상 중 자기 자신이 아닌 것 */
    int deleteByTenant(Integer tenantSeq);
    int deleteByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);
    /** 테넌트 소유자가 공유한 대상 중 테넌트 외부 사용자 */
    int deleteOtherTenantByTenant(Integer tenantSeq);
}

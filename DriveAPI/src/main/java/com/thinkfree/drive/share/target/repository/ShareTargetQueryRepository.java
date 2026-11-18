package com.thinkfree.drive.share.target.repository;

import com.thinkfree.drive.share.query.ShareTargetNearestFindQuery;
import com.thinkfree.drive.share.target.data.*;
import com.thinkfree.drive.share.target.query.*;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). 공유받은 목록(커서·정렬)·파티션 키·가장 가까운 상위 공유 대상(재귀 CTE)·해제 범위·대상 사용자 목록 — JdbcClient 구현.
 * 쓰기(배치 INSERT/UPSERT·DELETE)는 {@link ShareTargetRepository}.
 */
public interface ShareTargetQueryRepository {

    /** 공유받은 목록 — 수신자(userSeq) 기준, 폴더 먼저·정렬(NAME 자연정렬/SIZE/UPDATE/OPEN 열람일 fallback/SHARE 기본 DESC)·커서 게이트·필터 */
    List<ShareTargetData> findAll(ShareTargetListQuery query);
    List<ShareTargetPartitionKeyData> findShareTargetPartitionKeys(ShareTargetFindQuery query);
    /** 공유(shareSeq)의 대상 각각에 대해 조상 공유 중 같은 사용자를 대상으로 둔 가장 가까운 공유 대상(없으면 ancestorShareTargetSeq null) */
    List<ShareTargetNearestData> findNearestAncestorShareTargets(ShareTargetNearestFindQuery query);
    /** 위와 같되 대상 사용자 목록으로 한정. 목록이 비면 빈 결과 */
    List<ShareTargetNearestData> findNearestAncestorShareTargetsByUsers(ShareTargetNearestFindQuery query);
    /** 대상 사용자별로 이 공유가 끊기면 접근 경로가 사라지는 리소스(다른 공유의 대상인 서브트리 제외). 목록이 비면 빈 결과 */
    List<UnsharedTargetsData> findUnsharedDescendantsByTargets(UnsharedTargetsFindQuery query);
    List<ShareTargetUserData> findShareTargetUsersByShares(ShareTargetUsersQuery query);
    List<ShareTargetUserData> findShareTargetUsersByResource(ShareTargetUsersQuery query);
    List<ShareTargetUserData> findShareTargetUsersByDeletedResource(ShareTargetUsersQuery query);
    /** 리소스별 대상 사용자 목록(resultMap collection → 리포에서 그룹핑). 목록이 비면 빈 결과 */
    List<ShareTargetResourceUsersData> findShareTargetResourceUsersByResources(ShareTargetResourceUsersQuery query);
    List<ShareTargetResourceUsersData> findShareTargetResourceUsersByDeletedResources(ShareTargetResourceUsersQuery query);
}

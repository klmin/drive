package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.share.data.*;
import com.thinkfree.drive.share.query.*;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). 공유 단건·대상 포함·사용자 목록 DTO, 재귀 CTE 로 가장 가까운 상위/하위 공유를 찾는 nearest 계열, 공유 해제 범위 산정 — JdbcClient 구현.
 * 쓰기(INSERT 는 JPA 위임·UPDATE/DELETE 는 JdbcClient)는 {@link ShareRepository}.
 */
public interface ShareQueryRepository {

    SharedResourceData findSharedResourceByShare(SharedResourceFindQuery query);
    /** 링크 토큰 접근 시 해당 리소스 자체에 걸린 공유(상속/상위 공유는 보지 않음). 없으면 null */
    SharedResourceData findSharedResourceByResource(SharedResourceFindQuery query);
    /** 공유 + 대상 목록(share_target_seq DESC). 대상이 없으면 빈 목록, 공유가 없으면 null */
    ShareWithTargetsData findShareWithTargets(ShareWithTargetsFindQuery query);
    List<ShareUserData> findUsersByShare(ShareUsersFindQuery query);
    /** 리소스의 조상(자기 제외) 중 가장 가까운 공유 + 최상위 조상 노드. 리소스가 없으면 null */
    SharedResourceNearestData findNearestAncestorShareByResource(SharedResourceNearestFindQuery query);
    /** 리소스 자신 포함 조상 중 가장 가까운 공유 + 최상위 조상 노드 */
    SharedResourceNearestData findNearestSelfOrAncestorShareByResource(SharedResourceNearestFindQuery query);
    /** rootResourceSeq 하위(자기 제외 = resourceSeq) 중 가장 가까운 공유. 없으면 null */
    SharedResourceNearestData findNearestDescendantShareByResource(SharedResourceNearestFindQuery query);
    /** 공유 리소스의 조상 중 다른 공유(shareSeq 제외) 가장 가까운 것 — shareSeq 만 채움. 없으면 null */
    SharedResourceNearestData findNearestAncestorShareByShare(SharedResourceNearestFindQuery query);
    SharedResourceNearestData findNearestAncestorShareAndRootByShare(SharedResourceNearestFindQuery query);
    /** rootResourceSeq 하위 중 shareSeq 가 아닌 가장 가까운 공유 */
    SharedResourceNearestData findNearestDescendantShareByShare(SharedResourceNearestFindQuery query);
    /** 이 공유가 끊기면 접근 경로가 사라지는 리소스(공유 루트 + 하위에서 다른 공유 서브트리 제외). 루트가 없으면 null */
    UnsharedResourceData findUnsharedDescendantsByShare(UnsharedResourcesFindQuery query);
    /** 리소스 자신 포함 조상에 걸린 공유 전부(4컬럼: resourceSeq·shareSeq·resourceType·shareType) */
    List<SharedResourceData> findAncestorSharesByResource(SharedResourceFindQuery query);
    boolean existsByTenant(Integer tenantSeq);
}

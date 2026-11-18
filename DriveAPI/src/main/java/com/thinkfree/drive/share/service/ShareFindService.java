package com.thinkfree.drive.share.service;

import com.thinkfree.drive.share.data.*;
import com.thinkfree.drive.share.query.SharedResourceNearestFindQuery;

import java.util.List;


public interface ShareFindService {
    
    SharedResourceData findSharedResourceByShare(Long shareSeq, int userSeq, boolean isDelete);

    /**
     * 해당 리소스 자체에 걸린 공유 조회. 없으면 null.
     * 링크 토큰 접근 시 "이 리소스가 어떤 공유로 노출되는가"를 가볍게 판별하는 용도.
     * (상위/상속 공유는 보지 않음 — 링크 토큰은 공유가 걸린 그 리소스에 발급되는 것을 전제로 한다)
     */
    SharedResourceData findSharedResourceByResource(long resourceSeq, int userSeq, boolean isDelete);
    ShareWithTargetsData findShareWithTargets(long shareSeq, int userSeq, boolean isDelete);
    List<ShareUserData> findUsersByShare(long shareSeq, int userSeq, boolean isDelete);
    SharedResourceNearestData findNearestAncestorShareByShare(long shareSeq, int userSeq, boolean isDelete);
    SharedResourceNearestData findNearestAncestorShareAndRootByShare(long shareSeq, int userSeq, boolean isDelete);
    SharedResourceNearestData findNearestShareByResource(long resourceSeq, int userSeq, boolean isDelete);
    SharedResourceNearestData findNearestSelfOrAncestorShareByResource(SharedResourceNearestFindQuery query);
    SharedResourceNearestData findNearestSelfOrShareByResource(long resourceSeq, int userSeq, boolean isDelete);
    SharedResourceNearestData findNearestShareByShare(long shareSeq, int userSeq, boolean isDelete);

    /**
     * 이 공유(폴더)가 끊기면 접근 경로가 사라지는 리소스 목록.
     * 공유 루트 + 하위 전체에서 "다른 공유가 걸린 노드와 그 하위 서브트리 전체"를 제외한 나머지
     * = 이 공유 하나로만 접근 가능했던 범위. (다른 공유 서브트리는 별도 접근원이 있어 정리 대상 아님)
     * 링크 해제/소멸/축소 시 소유주 + 이 리소스 목록만으로 메타(최근/중요/핀)를 범위 삭제하는 데 쓴다.
     */
    UnsharedResourceData findUnsharedDescendantsByShare(long shareSeq, int userSeq, boolean isDelete);
    List<ShareUserData> findSharedUsersByResource(long resourceSeq, int userSeq, boolean isDelete);
    boolean existsByTenant(Integer tenantSeq);
}

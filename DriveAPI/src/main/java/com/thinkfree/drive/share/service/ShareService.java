package com.thinkfree.drive.share.service;

import com.thinkfree.drive.share.data.SharedResourceData;
import com.thinkfree.drive.share.dto.ShareMoveImpactDto;
import com.thinkfree.drive.share.dto.ShareSaveDto;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.result.ShareMoveImpactResult;
import com.thinkfree.drive.share.result.ShareSaveResult;
import com.thinkfree.drive.share.result.ShareUnshareResult;
import com.thinkfree.drive.share.result.ShareUpdateResult;

import java.util.Collection;
import java.util.List;


public interface ShareService {
    
    int deleteByResource(long resourceSeq, int userSeq);
    int deleteByResources(Collection<Long> resourceSeqList, int userSeq);
    ShareSaveResult saveShareWithTargets(ShareSaveDto dto);
    ShareUpdateResult updateShareWithTargets(ShareUpdateDto dto);
    /** 판단용 DTO(호출부가 같은 트랜잭션에서 조회)를 받아 대상·공유 행 삭제 + 정리 컨텍스트 계산 */
    ShareUnshareResult deleteShare(SharedResourceData sharedResource);
    ShareMoveImpactResult evaluateMoveShareImpact(ShareMoveImpactDto dto);
    void deleteByUsers(List<Integer> userSeqList);
    void deleteByTenant(Integer tenantSeq);

    /**
     * 테넌트의 특정 공유 유형(scope) 공유를 통째로 삭제한다 — 공유 대상(target)도 함께 cascade.
     * 공유 설정 reconcile 에서 불허된 scope 정리에 사용. shareTypes 가 비면 호출하지 않는다.
     */
    void deleteByTenantAndScopes(Integer tenantSeq, List<String> shareTypes);
}

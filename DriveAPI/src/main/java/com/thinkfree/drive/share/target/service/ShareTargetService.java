package com.thinkfree.drive.share.target.service;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.dto.ShareTargetsReplaceDto;
import com.thinkfree.drive.share.target.dto.ShareTargetsSaveDto;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.result.ShareTargetDeletionResult;
import com.thinkfree.drive.share.target.result.ShareTargetReplaceResult;
import com.thinkfree.drive.share.target.result.ShareTargetSaveResult;

import java.util.Collection;
import java.util.List;


public interface ShareTargetService  {
    
    ShareTargetSaveResult insertShareTargets(ShareTargetsSaveDto dto);
    ShareTargetReplaceResult replaceShareTargets(ShareTargetsReplaceDto dto);
    void deleteByResource(Long resourceSeq, int sharedByUserSeq);
    void deleteByResources(Collection<Long> resourceSeqList, int userSeq);
    int deleteByShareTargetDeleteKeys(Collection<ShareTargetDeleteKey> deleteKeys, int sharedByUserSeq);
    ShareTargetDeletionResult calculateTargetsDeleteResult(long shareSeq, int userSeq, ShareType shareType, ResourceType resourceType, long resourceSeq);
    void deleteByUsers(List<Integer> userSeqList);
    void deleteByTenant(Integer tenantSeq);

    /** 테넌트의 특정 공유 유형(scope) 공유에 속한 공유대상을 삭제한다(살아있는 share 와 조인). */
    void deleteByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);

    /** 외부공유 OFF: 테넌트가 공유한 대상 중 다른 테넌트(테넌트 외부) 접근자만 삭제한다. */
    void deleteOtherTenantByTenant(Integer tenantSeq);
}

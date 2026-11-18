package com.thinkfree.drive.recent.service;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.recent.dto.RecentSaveDto;

import java.util.Collection;
import java.util.List;


public interface RecentService {
    
    void save(RecentSaveDto dto);
    void deleteByResource(Long resourceSeq, int sharedByUserSeq);
    void deleteByResources(Collection<Long> resourceSeqList, int sharedByUserSeq);
    void deleteByResourceExcludingOwner(Long resourceSeq, int userSeq, int sharedByUserSeq);
    void deleteByResourcesExcludingOwner(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq);
    void deleteByResourceExcludingOwner(Long resourceSeq, int userSeq, int sharedByUserSeq, Collection<Integer> excludeUserSeqList);
    void deleteByResourcesExcludingOwner(Collection<Long> resourceSeqList, int userSeq, int sharedByUserSeq, Collection<Integer> excludeUserSeqList);

    /** 전체공개 -> 조직링크 축소: 해당 리소스들에서 외부 테넌트(tenantSeq 기준) 접근자의 최근문서 정리. excludeUserSeqList 는 보존. */
    void deleteOtherTenantByResources(Collection<Long> resourceSeqList, int sharedByUserSeq, int tenantSeq, Collection<Integer> excludeUserSeqList);
    void deleteByShareTargets(int sharedByUserSeq, Collection<PartitionKey> keys);
    void deleteByUserResources(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq);
    void deleteByUsers(List<Integer> userSeqList);
    void deleteAllSharedByTenant(Integer tenantSeq);

    /** 불허된 scope 공유의 stale 한 최근문서 접근자 메타데이터 정리(살아있는 share 와 조인). */
    void deleteSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);

    /** 외부공유 OFF: 다른 테넌트(테넌트 외부) 접근자의 최근문서 메타데이터만 정리. */
    void deleteOtherTenantSharedByTenant(Integer tenantSeq);

}

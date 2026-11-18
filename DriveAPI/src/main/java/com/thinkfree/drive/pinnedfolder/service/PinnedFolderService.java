package com.thinkfree.drive.pinnedfolder.service;

import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.pinnedfolder.dto.PinnedFolderSaveDto;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourceQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourcesQuery;

import java.util.Collection;
import java.util.List;

public interface PinnedFolderService {
    
    void pinFolder(PinnedFolderSaveDto dto);
    void unpinFolder(PinnedFolderDeleteQuery query);
    void unpinFolderByResource(PinnedFolderDeleteResourceQuery query);
    void unpinFolderByResources(PinnedFolderDeleteResourcesQuery query);
    void unpinFolderByResourcesExcludingOwner(PinnedFolderDeleteResourcesQuery query);
    void unpinFolderByUserResources(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq);
    void unpinFoldersByUsers(List<Integer> userSeqList);
    void unpinAllSharedByTenant(Integer tenantSeq);

    /** 불허된 scope 공유의 stale 한 고정폴더 접근자 메타데이터 정리(살아있는 share 와 조인). */
    void unpinSharedByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);

    /** 외부공유 OFF: 다른 테넌트(테넌트 외부) 접근자의 고정폴더 메타데이터만 정리. */
    void unpinOtherTenantSharedByTenant(Integer tenantSeq);

    /** 전체공개 -> 조직링크 축소: 해당 리소스들에서 외부 테넌트(query.tenantSeq 기준) 접근자의 핀 정리. excludeUserSeqList 는 보존. */
    void unpinOtherTenantByResources(PinnedFolderDeleteResourcesQuery query);
}

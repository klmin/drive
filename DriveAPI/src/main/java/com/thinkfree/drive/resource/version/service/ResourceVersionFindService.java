package com.thinkfree.drive.resource.version.service;

import com.thinkfree.drive.resource.data.ResourceStorageKeyData;
import com.thinkfree.drive.resource.data.ResourceUsageSummaryData;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData;
import com.thinkfree.drive.resource.version.data.ResourceVersionKeyData;
import com.thinkfree.drive.resource.version.query.ResourceVersionFindQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionListQuery;

import java.util.List;


public interface ResourceVersionFindService {
    
    List<ResourceVersionData> findAll(ResourceVersionListQuery query);
    Long countAll(ResourceVersionListQuery query);
    List<ResourceVersionKeyData> findAllByResource(ResourceVersionListQuery query);
    List<ResourceVersionKeyData> findAllByResourceList(ResourceVersionListQuery query);
    ResourceVersionWithResourceData findResourceVersionWithResource(ResourceVersionFindQuery query);
    ResourceVersionKeyData findResourceVersion(ResourceVersionFindQuery query);
    List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList);
    ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq);
    
}

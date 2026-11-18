package com.thinkfree.drive.resource.share.service;

import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.share.data.ResourceSharedListData;

import java.util.List;


public interface ResourceShareFindService {
    
    List<ResourceSharedListData> findAll(int requestUserSeq, ResourceListQuery query);
    
    
}

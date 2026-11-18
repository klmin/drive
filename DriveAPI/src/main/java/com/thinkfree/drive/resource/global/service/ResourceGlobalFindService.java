package com.thinkfree.drive.resource.global.service;

import com.thinkfree.drive.resource.global.data.ResourceGlobalListData;
import com.thinkfree.drive.resource.query.ResourceListQuery;

import java.util.List;

public interface ResourceGlobalFindService {
    
    List<ResourceGlobalListData> findAll(int requestUserSeq, ResourceListQuery query);
}

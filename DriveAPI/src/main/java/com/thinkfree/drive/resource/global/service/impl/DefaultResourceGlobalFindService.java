package com.thinkfree.drive.resource.global.service.impl;

import com.thinkfree.drive.permission.service.PermissionAggregationService;
import com.thinkfree.drive.resource.global.converter.ResourceGlobalConverter;
import com.thinkfree.drive.resource.global.repository.ResourceGlobalQueryRepository;
import com.thinkfree.drive.resource.global.data.ResourceGlobalListData;
import com.thinkfree.drive.resource.global.service.ResourceGlobalFindService;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultResourceGlobalFindService implements ResourceGlobalFindService {
    
    private final ResourceGlobalConverter resourceGlobalConverter;
    private final ResourceGlobalQueryRepository resourceGlobalQueryRepository;
    private final PermissionAggregationService permissionAggregationService;
    
    @Override
    public List<ResourceGlobalListData> findAll(int requestUserSeq, ResourceListQuery query) {
        var list = resourceGlobalQueryRepository.findAll(query);
        return permissionAggregationService.mergeResourcesPermissionWithFarthest(requestUserSeq, list, resourceGlobalConverter::toResourceGlobalListData);
    }
    
    
}

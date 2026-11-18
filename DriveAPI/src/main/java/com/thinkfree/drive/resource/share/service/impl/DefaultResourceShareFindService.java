package com.thinkfree.drive.resource.share.service.impl;

import com.thinkfree.drive.permission.service.PermissionAggregationService;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.share.converter.ResourceShareConverter;
import com.thinkfree.drive.resource.share.repository.ResourceShareQueryRepository;
import com.thinkfree.drive.resource.share.data.ResourceSharedListData;
import com.thinkfree.drive.resource.share.service.ResourceShareFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultResourceShareFindService implements ResourceShareFindService {
    
    private final ResourceShareConverter resourceShareConverter;
    private final ResourceShareQueryRepository resourceShareQueryRepository;
    private final PermissionAggregationService permissionAggregationService;
    
    @Override
    public List<ResourceSharedListData> findAll(int requestUserSeq, ResourceListQuery query) {
        query.setRequestUserSeq(requestUserSeq);
        var resources = resourceShareQueryRepository.findAllByShared(query);
        return permissionAggregationService.mergeResourcesPermissionWithFarthest(requestUserSeq, resources, resourceShareConverter::toResourceSharedListData);
    }
    
}

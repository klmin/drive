package com.thinkfree.drive.recent.service.impl;

import com.thinkfree.drive.permission.service.PermissionAggregationService;
import com.thinkfree.drive.recent.converter.RecentConverter;
import com.thinkfree.drive.recent.data.RecentResourceShareData;
import com.thinkfree.drive.recent.query.RecentListQuery;
import com.thinkfree.drive.recent.repository.RecentQueryRepository;
import com.thinkfree.drive.recent.service.RecentFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class DefaultRecentFindService implements RecentFindService {
    
    private final RecentQueryRepository recentQueryRepository;
    private final RecentConverter recentConverter;
    private final PermissionAggregationService permissionAggregationService;
    
    @Override
    public List<RecentResourceShareData> findAll(RecentListQuery query) {
        var recentList = recentQueryRepository.findAll(query);
        var userSeq = query.getUserSeq();
        return permissionAggregationService.mergeResourcesPermissionWithFarthest(userSeq, recentList, recentConverter::toRecentResourceShareData);
    }
}

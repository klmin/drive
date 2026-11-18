package com.thinkfree.drive.starred.service.impl;

import com.thinkfree.drive.permission.service.PermissionAggregationService;
import com.thinkfree.drive.starred.converter.StarredConverter;
import com.thinkfree.drive.starred.data.StarredResourceShareData;
import com.thinkfree.drive.starred.query.StarredListQuery;
import com.thinkfree.drive.starred.repository.StarredQueryRepository;
import com.thinkfree.drive.starred.service.StarredFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DefaultStarredFindService implements StarredFindService {

    private final StarredQueryRepository starredQueryRepository;
    private final StarredConverter starredConverter;
    private final PermissionAggregationService permissionAggregationService;
    
    @Override
    public List<StarredResourceShareData> findAll(StarredListQuery query) {
        var starredList = starredQueryRepository.findAll(query);
        var userSeq = query.getUserSeq();
        return permissionAggregationService.mergeResourcesPermissionWithFarthest(userSeq, starredList, starredConverter::toStarredResourceShareData);
        
    }
}

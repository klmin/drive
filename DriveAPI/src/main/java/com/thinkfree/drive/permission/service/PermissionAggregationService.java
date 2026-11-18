package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

@Service
@RequiredArgsConstructor
public class PermissionAggregationService {
    
    private final ResourcePermissionFindService resourcePermissionFindService;
    
    @Transactional(readOnly = true)
    public <T extends PartitionKeyProvider, R> List<R> mergeResourcesPermissionWithFarthest(int requestUserSeq, Collection<T> list, BiFunction<T, ResourcePermissionWithFarthest, R> converter) {
        
        if(list.isEmpty()){
            return Collections.emptyList();
        }
        
        var keys = list.stream().map(d -> new PartitionKey(d.getSharedByUserSeq(), d.getResourceSeq())).toList();
        var resourcesPermissionWithFarthest = resourcePermissionFindService.findResourcesPermissionWithFarthestMap(requestUserSeq, keys, false);
        
        return list.stream()
                    .map(d -> {
                        var sharedByUserSeq = d.getSharedByUserSeq();
                        var resourceSeq = d.getResourceSeq();
                        PartitionKey partitionKey = new PartitionKey(sharedByUserSeq, resourceSeq);
                        ResourcePermissionWithFarthest resourcePermissionWithFarthest = resourcesPermissionWithFarthest.get(partitionKey);
                        return converter.apply(d, resourcePermissionWithFarthest);
                    }).toList();
    }
    
    @Transactional(readOnly = true)
    public <T extends PartitionKeyProvider, R> R mergeResourcePermissionWithFarthest(int requestUserSeq, T context, Boolean isDelete, BiFunction<T, ResourcePermissionWithFarthest, R> converter) {
        var partitionKey = new PartitionKey(context.getSharedByUserSeq(), context.getResourceSeq());
        var resourcePermissionWithFarthest = resourcePermissionFindService.findResourcePermissionWithFarthest(requestUserSeq, partitionKey, isDelete);
        return converter.apply(context, resourcePermissionWithFarthest);
    }
    
}

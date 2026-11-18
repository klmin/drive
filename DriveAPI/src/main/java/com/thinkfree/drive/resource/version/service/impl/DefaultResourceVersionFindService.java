package com.thinkfree.drive.resource.version.service.impl;

import com.thinkfree.drive.resource.data.ResourceStorageKeyData;
import com.thinkfree.drive.resource.data.ResourceUsageSummaryData;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData;
import com.thinkfree.drive.resource.version.data.ResourceVersionKeyData;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.resource.version.query.ResourceVersionFindQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionListQuery;
import com.thinkfree.drive.resource.version.repository.ResourceVersionQueryRepository;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultResourceVersionFindService implements ResourceVersionFindService {
    
    private final ResourceVersionQueryRepository resourceVersionQueryRepository;
    
    @Override
    public List<ResourceVersionData> findAll(ResourceVersionListQuery query){
        return resourceVersionQueryRepository.findAll(query);
    }
    @Override
    public Long countAll(ResourceVersionListQuery query){
        return resourceVersionQueryRepository.countAll(query);
    }
    @Override
    public List<ResourceVersionKeyData> findAllByResource(ResourceVersionListQuery query){
        return resourceVersionQueryRepository.findAllByResource(query);
    }
    @Override
    public List<ResourceVersionKeyData> findAllByResourceList(ResourceVersionListQuery query){
        return resourceVersionQueryRepository.findAllByResourceList(query);
    }
    @Override
    public ResourceVersionWithResourceData findResourceVersionWithResource(ResourceVersionFindQuery query){
        var data = resourceVersionQueryRepository.findResourceVersionWithResource(query);
        DriveAssert.throwIfNull(data, ResourceVersionErrorCode.NOT_FOUND);
        return data;
    }
    @Override
    public ResourceVersionKeyData findResourceVersion(ResourceVersionFindQuery query){
        var data = resourceVersionQueryRepository.findResourceVersion(query);
        DriveAssert.throwIfNull(data, ResourceVersionErrorCode.NOT_FOUND);
        return data;
    }
    
    @Override
    public List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList) {
        return resourceVersionQueryRepository.findStorageKeysByUserSeqList(userSeqList);
    }
    
    @Override
    public ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq) {
        return resourceVersionQueryRepository.findUsageSummaryByUserSeq(userSeq);
    }
    
}

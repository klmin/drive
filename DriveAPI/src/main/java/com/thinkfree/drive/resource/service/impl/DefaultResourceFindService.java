package com.thinkfree.drive.resource.service.impl;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.data.*;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.query.*;
import com.thinkfree.drive.resource.repository.ResourceQueryRepository;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultResourceFindService implements ResourceFindService {
    
    private final ResourceConverter resourceConverter;
    private final ResourceQueryRepository resourceQueryRepository;
    private final ResourceVersionFindService resourceVersionFindService;
    
    @Override
    public List<ResourceData> findAll(ResourceListQuery query){
        return resourceQueryRepository.findAll(query);
    }
    @Override
    public void existsResourceName(ResourceExistsQuery query){
        var result = resourceQueryRepository.existsResourceName(query);
        DriveAssert.throwIfTrue(result, ResourceErrorCode.CONFLICT);
    }

    @Override
    public ResourceExistsData findResourceByExists(ResourceExistsQuery query){
        return resourceQueryRepository.findResourceByExists(query);
    }

    @Override
    public ResourceDetailData findDetail(ResourceFindQuery query){
        var resource = resourceQueryRepository.findDetail(query);
        DriveAssert.throwIfNull(resource, ResourceErrorCode.NOT_FOUND, Map.of("resourceSeq", query.getResourceSeq()));
        return resource;
    }

    @Override
    public ResourceDetailData findDetailOrNull(ResourceFindQuery query){
        return resourceQueryRepository.findDetail(query);
    }

    /** PK 한 행 조회 + null 판정(같은 조건의 count 쿼리와 비용 같음 — 메서드를 늘리지 않음, 단계 3 User 와 같은 방침) */
    @Override
    public boolean existsByResource(ResourceFindQuery query){
        return resourceQueryRepository.findDetail(query) != null;
    }

    @Override
    public boolean existsByShareToken(String shareToken){
        return resourceQueryRepository.existsByShareToken(shareToken);
    }

    @Override
    public ResourceTokenAccessData findResourceByShareToken(String shareToken, boolean isDelete){
        return resourceQueryRepository.findResourceByShareToken(shareToken, isDelete);
    }
    @Override
    public ResourceData findByIdAndIsDelete(ResourceFindQuery query){
        var entity = resourceQueryRepository.findByIdAndIsDelete(query);
        DriveAssert.throwIfNull(entity, ResourceErrorCode.NOT_FOUND);
        return entity;
    }
    @Override
    public List<ResourceDeleteParentData> findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery query){
        return resourceQueryRepository.findAllByDeleteParentFolderSeq(query);
    }
    @Override
    public List<ResourceHierarchyData> findResourceHierarchy(ResourceHierarchyQuery query){
        var resources = resourceQueryRepository.findResourceHierarchy(query);
        DriveAssert.throwIfEmpty(resources, ResourceErrorCode.NOT_FOUND);
        return resources;
    }
    @Override
    public List<ResourceHierarchyData> findResourceHierarchyByPartitionKeys(List<PartitionKey> keys, Boolean isDelete) {
        var query = resourceConverter.toResourceHierarchyPartitionKeysQuery(keys, isDelete);
        var hierarchies = resourceQueryRepository.findResourceHierarchyByPartitionKeys(query);
        DriveAssert.throwIfEmpty(hierarchies, ResourceErrorCode.NOT_FOUND);
        return hierarchies;
    }
    
    @Override
    public List<ResourceVersionHierarchyData> findResourceVersionsByFolderHierarchy(long resourceSeq, int userSeq){
        var query = resourceConverter.toResourceVersionHierarchyQuery(resourceSeq, userSeq);
        return resourceQueryRepository.findResourceVersionsByFolderHierarchy(query);
    }
    
    @Override
    public List<ResourceTreeData> findResourceTree(ResourceFindQuery query) {
        return resourceQueryRepository.findResourceTree(query);
    }
    
    @Override
    public List<ResourcePathData> findFolderAndDescendants(ResourcePathQuery query) {
        var list = resourceQueryRepository.findFolderAndDescendants(query);
        DriveAssert.throwIfEmpty(list, ResourceErrorCode.NOT_FOUND);
        return list;
    }
    
    @Override
    public List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList){
        var resourceVersionStorageKeys = resourceVersionFindService.findStorageKeysByUserSeqList(userSeqList);
        var resourceStorageKeys = resourceQueryRepository.findStorageKeysByUserSeqList(userSeqList);
        List<ResourceStorageKeyData> result = new ArrayList<>(resourceVersionStorageKeys.size() + resourceStorageKeys.size());
        result.addAll(resourceVersionStorageKeys);
        result.addAll(resourceStorageKeys);
        return result;
    }
    
    @Override
    public ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq) {
        return resourceQueryRepository.findUsageSummaryByUserSeq(userSeq);
    }
    
}

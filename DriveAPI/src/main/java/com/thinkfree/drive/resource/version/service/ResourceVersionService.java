package com.thinkfree.drive.resource.version.service;

import com.thinkfree.drive.resource.query.ResourceVersionRollbackQuery;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.dto.ResourceVersionSaveDto;
import com.thinkfree.drive.resource.version.result.ResourceVersionDeleteAllResult;
import com.thinkfree.drive.resource.version.result.ResourceVersionDeleteResult;
import com.thinkfree.drive.resource.version.result.ResourceVersionSaveResult;
import com.thinkfree.drive.resource.version.result.ResourceVersionStatsResult;

import java.util.Collection;
import java.util.List;

public interface ResourceVersionService {
    ResourceVersionSaveResult saveAndSetResourceName(ResourceVersionSaveDto dto);
    void rollbackResourceVersionMetadata(ResourceVersionRollbackQuery query, ResourceVersionRowData rollbackData);
    void finalizeAfterStorageUpload(Long resourceVersionSeq, int userSeq, ResourceVersionRowData deleteResourceVersion);
    ResourceVersionRowData cleanVersionIfExists(int userSeq, Long resourceSeq, Long currentSizeByte, String resourceSaveName);
    void deleteByResource(Long resourceSeq, int userSeq);
    void deleteByResourceSeqList(List<Long> resourceSeqList, int userSeq);
    ResourceVersionDeleteResult deleteVersion(long resourceSeq, long resourceVersionSeq, int userSeq);
    ResourceVersionDeleteAllResult deleteAllVersions(long resourceSeq, int userSeq);
    ResourceVersionStatsResult calculateTotalVersionStats(long resourceSeq, int userSeq);
    void updateUserSeqForResource(long resourceSeq, int userSeq, int changeUserSeq);
    void updateUserSeqForResources(Collection<Long> resourceSeqList, int userSeq, int changeUserSeq);
    void deleteByUserSeqList(List<Integer> userSeqList);
}

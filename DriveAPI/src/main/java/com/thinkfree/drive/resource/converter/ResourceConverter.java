package com.thinkfree.drive.resource.converter;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.dto.ResourceRenameDto;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.*;
import com.thinkfree.drive.resource.result.ResourceDeleteVersionResult;
import com.thinkfree.drive.resource.result.ResourceFolderCreateResult;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring", imports = {ResourceType.class, Instant.class, DriveConstants.class})
public interface ResourceConverter {
    
    @Mapping(target = "resourceType", expression = "java(ResourceType.FILE)")
    @Mapping(target = "sizeByte", source = "dto.fileSize")
    @Mapping(target = "resourceName", source = "dto.baseFileName")
    @Mapping(target = "latestOwnerSeq", source = "ownerSeq")
    @Mapping(target = "isDelete", constant = "false")
    @Mapping(target = "versionRegisterDate", expression = "java(Instant.now())")
    Resource toUploadResource(FileUploadDto dto);
    
    @Mapping(target = "resourceType", expression = "java(ResourceType.FOLDER)")
    @Mapping(target = "resourceName", source = "dto.baseFolderName")
    @Mapping(target = "originalFileName", source = "dto.originalFolderName")
    @Mapping(target = "isDelete", constant = "false")
    Resource toFolderResource(FolderUploadDto dto);

    ResourceLockQuery toResourceLockQuery(Long resourceSeq, Integer userSeq, ResourceType resourceType, Long parentFolderSeq, String originalFileName, Boolean isDelete);
    
    ResourceLockUnlockQuery toResourceLockUnlockQuery(Long resourceSeq, Integer userSeq, Instant lockedDate, Integer loginUserSeq, Instant updateDate, Boolean isLocked);

    @Mapping(target = "isDelete", constant = "false")
    ResourcePathUpdateQuery toPathUpdateQuery(Long resourceSeq, Integer userSeq, String oldFullPath, String newFullPath, String oldFullPathSeq, String newFullPathSeq, Integer changeUserSeq);

    ResourceRestoreChildQuery toRestoreChildQuery(Long resourceSeq, Integer userSeq, String oldFullPath, String newFullPath, String oldFullPathSeq, String newFullPathSeq);
    
    ResourceFindQuery toFindQuery(Long resourceSeq, Integer userSeq, ResourceType resourceType, Boolean isDelete);

    ResourceDeleteQuery toDeleteQuery(Long resourceSeq, Integer userSeq);
    
    ResourceDeleteSeqListQuery toDeleteSeqListQuery(List<Long> resourceSeqList, Integer userSeq);
    
    @Mapping(target = "resourceSeq", source = "resource.resourceSeq")
    @Mapping(target = "userSeq", source = "resource.userSeq")
    @Mapping(target = "resourceVersionSeq", source = "resourceVersionSeq")
    @Mapping(target = "resourceName", source = "resource.resourceName")
    @Mapping(target = "resourceSaveName", source = "resource.resourceSaveName")
    @Mapping(target = "versionResourceSaveName", source = "versionResourceSaveName")
    @Mapping(target = "currentVersionNumber", source = "currentVersionNumber")
    @Mapping(target = "versionRegisterDate", source = "resource.versionRegisterDate")
    @Mapping(target = "sizeByte", source = "resource.sizeByte")
    @Mapping(target = "rollbackData", source = "rollbackData")
    @Mapping(target = "updateUserSeq", source = "resource.updateUserSeq")
    @Mapping(target = "updateDate", source = "resource.updateDate")
    ResourceSaveResult toSaveResult(Resource resource, Long resourceVersionSeq, Integer currentVersionNumber, String versionResourceSaveName,
                                    Long rollbackSizeByte, ResourceVersionRowData rollbackData, String parentFolderName, Instant occurredDatetime,
                                    Long restoreSizeByte, Integer restoreUpdateUserSeq, Instant restoreUpdateDate);
    
    /**
     * 덮어쓰기 결과(W10) — 덮어쓰기 전 행(DTO)과 쓴 새 크기·감사 값(stamp). sizeByte·updateUserSeq·updateDate = 덮어쓴 값(되돌리기의 "현재 값" 조건),
     * restore* = 덮어쓰기 전 값(되돌릴 값). 버전 관련 칸은 비운다(구 toSaveResult 호출과 같음).
     */
    @Mapping(target = "resourceSeq", source = "before.resourceSeq")
    @Mapping(target = "userSeq", source = "before.userSeq")
    @Mapping(target = "latestOwnerSeq", source = "before.latestOwnerSeq")
    @Mapping(target = "resourceName", source = "before.resourceName")
    @Mapping(target = "resourceSaveName", source = "before.resourceSaveName")
    @Mapping(target = "versionRegisterDate", source = "before.versionRegisterDate")
    @Mapping(target = "sizeByte", source = "sizeByte")
    @Mapping(target = "updateUserSeq", source = "stamp.userSeq")
    @Mapping(target = "updateDate", source = "stamp.date")
    @Mapping(target = "rollbackSizeByte", source = "rollbackSizeByte")
    @Mapping(target = "occurredDatetime", source = "stamp.date")
    @Mapping(target = "restoreSizeByte", source = "before.sizeByte")
    @Mapping(target = "restoreUpdateUserSeq", source = "before.updateUserSeq")
    @Mapping(target = "restoreUpdateDate", source = "before.updateDate")
    @Mapping(target = "resourceVersionSeq", ignore = true)
    @Mapping(target = "parentFolderName", ignore = true)
    @Mapping(target = "versionResourceSaveName", ignore = true)
    @Mapping(target = "currentVersionNumber", ignore = true)
    @Mapping(target = "rollbackData", ignore = true)
    ResourceSaveResult toOverwriteResult(ResourceDetailData before, Long sizeByte, UpdateStamp stamp, Long rollbackSizeByte);

    /**
     * 관리 엔티티의 지금 값(공유 토큰 제외) — 감사 인자(W10c, 엔티티가 감사 인터페이스를 구현하지 않음).
     * 감사가 변경 후 값을 읽도록 Service 의 flush 뒤에 변환한다.
     */
    ResourceDetailData toDetailData(Resource resource);

    /** 폴더 생성 결과(W10c) — 저장·경로 지정까지 끝난 엔티티의 식별 값(감사 FOLDER_CREATE 인자) */
    ResourceFolderCreateResult toFolderCreateResult(Resource resource);

    ResourceDeleteVersionResult toDeleteVersionResult(String deleteResourceSaveName, Instant occurredDatetime);
    
    ResourceDeleteParentListQuery toDeleteParentListQuery(Long resourceSeq, int userSeq);
    
    ResourceHierarchyPartitionKeysQuery toResourceHierarchyPartitionKeysQuery(List<PartitionKey> keys, Boolean isDelete);
    
    @Mapping(target = "resourceType", expression = "java(ResourceType.FILE)")
    @Mapping(target = "isDelete", constant = "false")
    ResourceVersionHierarchyQuery toResourceVersionHierarchyQuery(Long resourceSeq, int userSeq);
    
    ResourcesUserUpdateQuery toResourcesUserUpdateQuery(Integer userSeq, Integer changeUserSeq, Collection<Long> resourceSeqList);
    
    /** 이름 중복 검사 쿼리 — 위치는 잠근 행의 실제 부모, 대상 자신은 제외 */
    @Mapping(target = "originalFileName", source = "dto.resourceName")
    @Mapping(target = "parentFolderSeq", source = "parentFolderSeq")
    @Mapping(target = "excludeResourceSeq", source = "dto.resourceSeq")
    ResourceExistsQuery toExistsQuery(ResourceRenameDto dto, Long parentFolderSeq);
    
}

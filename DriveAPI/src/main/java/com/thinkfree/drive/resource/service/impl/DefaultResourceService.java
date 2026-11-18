package com.thinkfree.drive.resource.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.contract.ResourceBrief;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.data.ResourceCopyNumbers;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.dto.NumberedNameRequestDto;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.dto.ResourceRenameDto;
import com.thinkfree.drive.resource.dto.ResourceRestoreDto;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.model.NumberedResourceName;
import com.thinkfree.drive.resource.model.ResourceMoveContext;
import com.thinkfree.drive.resource.model.ResourceVersionHierarchyStats;
import com.thinkfree.drive.resource.query.*;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.result.*;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.resource.version.converter.ResourceVersionConverter;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.resource.share.service.ResourceShareTokenService;
import com.thinkfree.drive.resource.version.service.ResourceVersionService;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.StringUtil;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultResourceService implements ResourceService {

    private final ResourceRepository resourcerepository;
    private final ResourceConverter resourceConverter;
    private final ResourceVersionConverter resourceVersionConverter;
    private final ResourceFindService resourceFindService;
    private final ResourceVersionService resourceVersionService;
    private final ResourceVersionFindService resourceVersionFindService;
    private final ResourceLockUnlockService resourceLockUnlockService;
    private final ResourceShareTokenService resourceShareTokenService;
    private final ResourceAuditService resourceAuditService;

    @Override
    public ResourceDetailData lockResourceForVersioning(long resourceSeq, int userSeq){
        var resource = resourcerepository.lockResourceForVersioning(resourceSeq, userSeq);
        DriveAssert.throwIfNull(resource, ResourceErrorCode.NOT_FOUND);
        return resource;
    }

    /**
     * 휴지통 보내기·영구 삭제용 — 대상 잠금(값) → 폴더면 하위 잠금 → 잠김 확인. 확인을 잠금 뒤에 하므로 확인~변경 사이에
     * 다른 요청이 대상·하위를 잠그거나(편집 시작) 같은 대상을 동시에 바꿀 수 없다. 이동·이름 변경은 {@link #lockForChangeUnlocked}(엔티티).
     */
    @Override
    public ResourceDetailData lockActiveUnlocked(long resourceSeq, int ownerSeq, ResourceType type){
        var resource = resourcerepository.lockVisibleActiveDetail(ownerSeq, resourceSeq);
        var matched = resource != null && (type == null || type == resource.getResourceType()) ? resource : null;
        DriveAssert.throwIfNull(matched, ResourceErrorCode.NOT_FOUND, Map.of("resourceSeq", resourceSeq));
        if (ResourceType.FOLDER == matched.getResourceType()) {
            resourcerepository.lockSubtree(ownerSeq, matched.getFullPathSeq());
        }
        resourceLockUnlockService.checkLockedResource(resourceSeq, ownerSeq);
        return matched;
    }

    @Override
    public void delete(long resourceSeq, int userSeq){
        var deleteQuery = resourceConverter.toDeleteQuery(resourceSeq, userSeq);
        var result = resourcerepository.delete(deleteQuery);
        DriveAssert.throwIfZero(result, ResourceErrorCode.DELETE_FAIL);
    }

    @Override
    public ResourceDeleteResult deleteAllVersions(long resourceSeq, int userSeq) {
        // 다른 버전 작업과 같이 리소스 행을 먼저 잠근다 — 현재 버전 갱신이 변경 감지(값이 같으면 UPDATE 생략)라 동시 버전 저장과 엇갈리지 않게
        lockResourceForVersioning(resourceSeq, userSeq);
        var result = resourceVersionService.deleteAllVersions(resourceSeq, userSeq);
        // 현재 버전 = 기본값 — 관리 엔티티 변경 감지(W10, 구 메모리 엔티티 + 리포 재로드·flush·detach). 잠금은 위 DTO 잠금이 이미 쥠
        var resource = resourcerepository.findByUserSeqAndResourceSeq(userSeq, resourceSeq);
        DriveAssert.throwIfNull(resource, ResourceErrorCode.UPDATE_FAIL);
        resource.applyCurrentVersion(DriveConstants.DEFAULT_VERSION_NUMBER);
        // 이벤트 시각 = Service 가 정한 현재 시각(W10 H7·결정 X — 구 감사 write-back)
        var occurredDatetime = Instant.now();
        return new ResourceDeleteResult(null, result.deleteKeys(), result.totalSizeByte(), occurredDatetime, Collections.emptyList());
    }
    
    @Override
    public ResourceDeleteResult deleteResourceAndVersions(long resourceSeq, int userSeq) {
        var resourceFindQuery = resourceConverter.toFindQuery(resourceSeq, userSeq, ResourceType.FILE, true);
        var resource = resourceFindService.findDetail(resourceFindQuery);
        var result = resourceVersionService.deleteAllVersions(resourceSeq, userSeq);

        var totalSize = result.totalSizeByte() + resource.getSizeByte();
        var keys = result.deleteKeys();
        keys.add(resource.getResourceSaveName());

        var deleteQuery = resourceConverter.toDeleteQuery(resourceSeq, userSeq);
        resourcerepository.delete(deleteQuery);
        var occurredDatetime = Instant.now();

        return new ResourceDeleteResult(null, keys, totalSize, occurredDatetime, List.of(new ResourceBrief(resourceSeq, resource.getResourceName())));
    }
    
    @Override
    public ResourceDeleteResult deleteResourcesAndVersions(long resourceSeq, int userSeq) {
            
        var deleteParentListQuery = resourceConverter.toDeleteParentListQuery(resourceSeq, userSeq);
        var deleteParentList = resourceFindService.findAllByDeleteParentFolderSeq(deleteParentListQuery);
        
        if(deleteParentList.isEmpty()){
            return null;
        }
        
        List<Long> deleteResourceSeqList = new ArrayList<>();
        List<String> deleteKeys = new ArrayList<>();
        List<Long> fileResourceSeqList = new ArrayList<>();
        long totalSizeByte = 0L;
        List<ResourceBrief> resourceBriefs = new ArrayList<>();
        
        for (var data : deleteParentList) {
            
            deleteResourceSeqList.add(data.getResourceSeq());
            resourceBriefs.add(new ResourceBrief(data.getResourceSeq(), data.getResourceName()));
            
            if (data.isFile()) {
                fileResourceSeqList.add(data.getResourceSeq());
                deleteKeys.add(data.getResourceSaveName());
                totalSizeByte += data.getSizeByte();
            }
            
        }
        
        if(!fileResourceSeqList.isEmpty()) {
            var resourceVersionListQuery = resourceVersionConverter.toResourceVersionListQuery(null, userSeq, fileResourceSeqList);
            var versions = resourceVersionFindService.findAllByResourceList(resourceVersionListQuery);
            
            if (!versions.isEmpty()) {
                for (var version : versions) {
                    deleteKeys.add(version.getResourceSaveName());
                    totalSizeByte += version.getSizeByte();
                }
                
                resourceVersionService.deleteByResourceSeqList(fileResourceSeqList, userSeq);
            }
        }
        
        var deleteQueryList = resourceConverter.toDeleteSeqListQuery(deleteResourceSeqList, userSeq);
        deleteByResourceSeqList(deleteQueryList);
        var occurredDatetime = Instant.now();

        // 폴더 완전삭제는 하위 파일/폴더까지 cascade되므로, 삭제된 항목마다 실제 타입에 맞춰 fan-out한다.
        for (var data : deleteParentList) {
            EventName eventName = data.isFile() ? EventName.FILE_PERMANENT_DELETE : EventName.FOLDER_PERMANENT_DELETE;
            // TODO(TFDRIVE-1612): 감사 테스트 미작성 — 폴더 영구삭제(하위 cascade)는 무거운 도메인 로직이라 H2 통합+픽스처 필요 (단건 FILE_PERMANENT_DELETE는 TrashUseCaseAuditTest로 검증됨)
            resourceAuditService.recordPermanentDelete(eventName, data.getResourceSeq(), data.getResourceName());
        }

        return new ResourceDeleteResult(deleteResourceSeqList, deleteKeys, totalSizeByte, occurredDatetime, resourceBriefs);
    }
    
    /** 파일 휴지통 이동 — 관리 엔티티 변경 감지(W10, 구 리포 안 로드·flush·detach). 뒤에 같은 행을 JdbcClient 로 읽는 곳 없음(tb_trash INSERT 뿐) → flush 없음 */
    @Override
    public void updateIsDeleteByFile(long resourceSeq, int userSeq) {
        var resource = resourcerepository.findByUserSeqAndResourceSeq(userSeq, resourceSeq);
        DriveAssert.throwIfNull(resource, ResourceErrorCode.DELETE_FAIL);
        resource.applyDeleteState(Boolean.TRUE);
    }
    
    @Override
    public void updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery query) {
        var result = resourcerepository.updateIsDeleteByFullPathSeq(query);
        DriveAssert.throwIfZero(result, ResourceErrorCode.DELETE_FAIL);
    }
    
    /**
     * 고칠 행 로드(관리 엔티티) — 구 {@code ResourceFindService.findForMutation} 과 같은 조건: 소유 계정 노출 가능 + 선택 조건 type·isDelete(null 이면 무시) Java 판정. 없거나 다르면 NOT_FOUND.
     */
    private Resource loadForChange(long resourceSeq, int userSeq, ResourceType resourceType, Boolean isDelete) {
        return requireMatches(resourcerepository.findVisibleByUserSeqAndResourceSeq(userSeq, resourceSeq), resourceSeq, resourceType, isDelete);
    }

    /** {@link #loadForChange} 의 잠금판 — 휴지통 복원(결정 R4: 구 {@code is_delete = Y} 조건 JPQL 의 동시 복원 보호를 첫 로드 잠금으로) */
    private Resource lockForRestore(long resourceSeq, int userSeq, ResourceType resourceType) {
        return lockForChange(resourceSeq, userSeq, resourceType, Boolean.TRUE);
    }

    /**
     * {@link #loadForChange} 의 잠금판(FOR UPDATE) — 그 행의 첫 로드에서만(같은 tx 에서 앞서 관리 엔티티로 올렸으면 캐시 값이라 잠금 뒤 값이 아니다).
     * 이동(10-14 점검 6-c): 같은 폴더의 동시 이동이 둘 다 잠금 없이 옛 경로를 읽으면, 나중 쪽의 하위 경로 치환({@code LIKE 옛경로%})이 앞선 커밋 뒤 0행이 되고
     * 자기 행만 옮겨져 하위 경로가 어긋났다. 첫 로드에서 잠그면 늦은 쪽은 앞선 커밋까지 기다린 뒤 새 값을 읽는다.
     */
    private Resource lockForChange(long resourceSeq, int userSeq, ResourceType resourceType, Boolean isDelete) {
        return requireMatches(resourcerepository.lockVisibleByUserSeqAndResourceSeq(userSeq, resourceSeq), resourceSeq, resourceType, isDelete);
    }

    /**
     * {@link #lockForChange} + 잠김 확인 — 이동·이름 변경용(관리 엔티티, 변경 감지로 고친다). 대상 잠금 → 폴더면 하위 잠금 → 잠김 확인(폴더면 하위까지).
     * 확인을 잠금 뒤 같은 트랜잭션에서 하므로, 확인~변경 사이에 다른 요청이 대상·하위를 잠글(편집 시작) 수 없다(구 컨트롤러 @CheckResourceLock 은 트랜잭션 밖이라 못 막았다).
     * 휴지통 보내기·영구 삭제는 {@link #lockActiveUnlocked}(값).
     */
    private Resource lockForChangeUnlocked(long resourceSeq, int userSeq, ResourceType resourceType, Boolean isDelete) {
        var entity = lockForChange(resourceSeq, userSeq, resourceType, isDelete);
        if (entity.getResourceType() == ResourceType.FOLDER) {
            resourcerepository.lockSubtree(userSeq, entity.getFullPathSeq());
        }
        resourceLockUnlockService.checkLockedResource(resourceSeq, userSeq);
        return entity;
    }

    private static Resource requireMatches(Resource resource, long resourceSeq, ResourceType resourceType, Boolean isDelete) {
        boolean matches = resource != null
                && (resourceType == null || resourceType == resource.getResourceType())
                && (isDelete == null || isDelete.equals(resource.getIsDelete()));
        var matched = matches ? resource : null;
        DriveAssert.throwIfNull(matched, ResourceErrorCode.NOT_FOUND, Map.of("resourceSeq", resourceSeq));
        return matched;
    }

    private void rollbackResourceVersionData(ResourceVersionRollbackQuery query){
        var result = resourcerepository.rollbackResourceVersionData(query);
        DriveAssert.throwIfZero(result, ResourceErrorCode.UPDATE_FAIL);
    }
    @Override
    public void rollbackVersion(ResourceSaveResult result){
        var query = resourceConverter.toVersionRollbackQuery(result);
        rollbackResourceVersionData(query);
        resourceVersionService.rollbackResourceVersionMetadata(query, result.rollbackData());
    }
    
    private void updateFullPathForChildFolders(ResourcePathUpdateQuery query){
        var result = resourcerepository.updateFullPathForChildFolders(query);
        log.info("""
                 [하위 폴더 {} 개 수정 성공] resourceSeq : {}
                 oldFullPath : {}, newFullPath : {}
                 oldFullPathSeq : {}, newFullPathSeq : {}
                 """,
                result, query.getResourceSeq(), query.getOldFullPath(), query.getNewFullPath(), query.getOldFullPathSeq(), query.getNewFullPathSeq());
    }
    
    private void restoreChildResources(ResourceRestoreChildQuery query){
        var result = resourcerepository.restoreChildResources(query);
        log.info("""
                 [하위 리소스 {} 개 복원 성공] resourceSeq : {}
                 oldFullPath : {}, newFullPath : {}
                 oldFullPathSeq : {}, newFullPathSeq : {}
                 """,
                result, query.getResourceSeq(), query.getOldFullPath(), query.getNewFullPath(), query.getOldFullPathSeq(), query.getNewFullPathSeq());
    }
    
    @Override
    public void deleteByResourceSeqList(ResourceDeleteSeqListQuery query){
        var result = resourcerepository.deleteByResourceSeqList(query);
        DriveAssert.throwIfZero(result, ResourceErrorCode.DELETE_FAIL);
    }

    /** 저장(IDENTITY 즉시 INSERT·관리 상태) 뒤 저장 이름(원본 키 + PK) — 커밋 때 변경 감지 UPDATE(W10, 구 재로드 → flush → detach) */
    private void saveAndSetResourceName(Resource entity){
        var saved = resourcerepository.save(entity);
        saved.initResourceSaveName();
    }

    /** 폴더 저장 뒤 경로(부모 경로 + PK·이름) — 커밋 때 변경 감지 UPDATE(W10). 같은 tx 의 하위 폴더 생성은 부모를 JPQL DTO 로 읽어 자동 flush 됨 */
    private Resource saveFolderAndSetFullPathSeq(Resource resource){
        var saved = resourcerepository.save(resource);
        saved.initFolderPath();
        return saved;
    }
    
    private void updateUserSeqForResources(int userSeq, int targetUserSeq, Set<Long> resourceSeqList){
        if(resourceSeqList.isEmpty()){
            return;
        }
        var resourcesUserUpdateQuery = resourceConverter.toResourcesUserUpdateQuery(userSeq, targetUserSeq, resourceSeqList);
        var updateResult = resourcerepository.updateUserSeqForResources(resourcesUserUpdateQuery);
        DriveAssert.throwIfZero(updateResult, ResourceErrorCode.UPDATE_FAIL);
    }
    
    private void applyCopyNameToResource(Resource resource, ResourceCopyNumbers resourceCopyNumbers, NumberedNameRequestDto numberedNameRequestDto){
        var copyResource = this.generateNumberedName(resourceCopyNumbers, numberedNameRequestDto);
        resource.changeCopyResource(copyResource.getResourceName(), copyResource.getOriginalFileName(), copyResource.getCopyNumber());
    }

    @Override
    public ResourceSaveResult saveWithCopyCheck(FileUploadDto dto){

        var resource = resourceConverter.toUploadResource(dto);
        
        var parentFolderResource = validateParentFolderExists(dto.getParentFolderSeq(), dto.getUserSeq());
        String parentFolderName = null;
        if(parentFolderResource != null){
            parentFolderName = parentFolderResource.getResourceName();
        }
        
        var resourceLockQuery = resourceConverter.toResourceLockQuery(null, resource.getUserSeq(), resource.getResourceType(), resource.getParentFolderSeq(), resource.getOriginalFileName(), resource.getIsDelete());
        var resourceCopyNumbers = resourcerepository.lockMaxCopyNumbers(resourceLockQuery);
        
        var numberedNameRequestDto = new NumberedNameRequestDto(dto.isCopyFile(), dto.getOriginalFileName(), dto.getBaseFileName(), dto.getCopyNumber(), dto.getExt(), dto.getOriginalFileNameWithoutExt());
        applyCopyNameToResource(resource, resourceCopyNumbers, numberedNameRequestDto);
        var token = resourceShareTokenService.createNewToken();
        resource.applyShareToken(token);
        
        var saved = resourcerepository.save(resource);
        saved.initResourceSaveName();
        
        var occurredDatetime = resource.getRegisterDate();
        
        return resourceConverter.toSaveResult(resource, null, null, null, resource.getSizeByte(), null, parentFolderName, occurredDatetime, null, null, null);

    }
    
    private ResourceDetailData validateParentFolderExists(Long parentFolderSeq, int userSeq){
        if(parentFolderSeq != null) {
            var parentFineQuery = resourceConverter.toFindQuery(parentFolderSeq, userSeq, ResourceType.FOLDER, false);
            return resourceFindService.findDetail(parentFineQuery);
        }
        return null;
    }
    
    @Override
    public ResourceSaveResult updateWithVersioning(FileUploadDto dto) {
        
        var lockResource = lockResourceForVersioning(dto.getResourceSeq(), dto.getUserSeq());
        var resourceSeq = lockResource.getResourceSeq();

        // 버전 복원만 — 행 잠금 뒤 같은 트랜잭션에서 잠김 확인(구 컨트롤러 @CheckResourceLock 은 트랜잭션 밖이라 확인~복원 사이에 잠기면 못 막았다)
        if (dto.isUnlockedRequired()) {
            resourceLockUnlockService.checkLockedResource(resourceSeq, dto.getUserSeq());
        }

        /* lock 정책에 버전은 아직 없음. 버전 lock 들어갈 경우 lock 소유주 필요해보임
        if (dto.isLockedResourceCheck()) {
            resourceLockUnlockService.checkLockedResource(resourceSeq, dto.getUserSeq());
        }
        */

        cleanVersionIfExists(lockResource);

        // 고칠 행 = 관리 엔티티 — 잠금(DTO)·정리(JdbcClient 되돌리기) 뒤의 첫 JPA 로드라 DB 현재 값(W10 R2, H1)
        var resource = loadForChange(resourceSeq, lockResource.getUserSeq(), ResourceType.FILE, false);

        var currentVersionNumber = resource.getCurrentVersionNumber();
        var userSeq = resource.getUserSeq();
        var versionRegisterUserSeq = dto.getVersionRegisterUserSeq();
        var newSizeByte = dto.getFileSize();
        var newLatestOwnerSeq = dto.getOwnerSeq();

        var resourceVersionSaveDto = resourceVersionConverter.toResourceVersionSaveDto(userSeq, resourceSeq, resource.getLatestOwnerSeq(), resource.getResourceSaveName(), currentVersionNumber, resource.getSizeByte(), resource.getVersionRegisterDate(), versionRegisterUserSeq);
        var resourceVersionSaveResult = resourceVersionService.saveAndSetResourceName(resourceVersionSaveDto);
        var versionRegisterDate = Instant.now();

        // 결과(롤백 기준 = 저장 전 크기·수정자·수정일·최종 소유자·버전 등록일)는 바꾸기 전에 만든다 — 구 revertToPreviousVersionMetadata(메모리 되돌리기) 제거(W10 H2).
        // 이벤트 시각 = Service 가 정한 버전 등록 시각(W10 H7·결정 X — 구 감사 write-back)
        var result = resourceConverter.toSaveResult(resource, resourceVersionSaveResult.resourceVersionSeq(), currentVersionNumber, resourceVersionSaveResult.resourceSaveName(), newSizeByte,
                resourceVersionSaveResult.rollbackData(), null, versionRegisterDate, null, null, null);

        // 커밋 때 변경 감지 UPDATE(W10 — 구 리포 재로드·flush·detach). 같은 tx 에서 이 행을 JdbcClient 로 읽는 곳 없음(prepareMeta 끝) → flush 없음
        resource.applyVersionUpdate(resourceVersionSaveResult.isLimitExceeded(), versionRegisterUserSeq, newSizeByte, newLatestOwnerSeq, versionRegisterDate);

        return result;
    }

    private void cleanVersionIfExists(ResourceDetailData resource){
        var userSeq = resource.getUserSeq();
        var resourceSeq = resource.getResourceSeq();
        var cleanResourceVersion = resourceVersionService.cleanVersionIfExists(userSeq, resourceSeq, resource.getSizeByte(), resource.getResourceSaveName());
        if(cleanResourceVersion != null){
            var rollbackQuery = resourceConverter.toVersionRollbackQuery(cleanResourceVersion);
            rollbackResourceVersionData(rollbackQuery);
        }
    }
    
    @Override
    public ResourceSaveResult updateWithOverwrite(FileUploadDto dto) {
        var lockResource = lockResourceForVersioning(dto.getResourceSeq(), dto.getUserSeq());
        var resourceSeq = lockResource.getResourceSeq();

        cleanVersionIfExists(lockResource);

        // 엔티티 없이(W10, 계획 Q7·C-7 ③): 덮어쓰기는 @Modifying 이라 관리 엔티티를 쥘 이유가 없다 — 정리(JdbcClient) 뒤 DB 현재 값을 DTO 로 읽는다
        var findQuery = resourceConverter.toFindQuery(resourceSeq, lockResource.getUserSeq(), ResourceType.FILE, false);
        var before = resourceFindService.findDetail(findQuery);
        var newFileSize = dto.getFileSize();
        var restoreQuotaSizeByte = newFileSize - before.getSizeByte();

        var stamp = resourcerepository.overwrite(before.getUserSeq(), resourceSeq, newFileSize);
        DriveAssert.throwIfNull(stamp, ResourceErrorCode.OVERWRITE_FAIL);

        return resourceConverter.toOverwriteResult(before, newFileSize, stamp, restoreQuotaSizeByte);
    }
    
    @Override
    public ResourceFolderCreateResult saveFolderWithCopyCheck(FolderUploadDto dto){

        var resource = resourceConverter.toFolderResource(dto);
        
        var parentFolderResource = validateParentFolderExists(resource.getParentFolderSeq(), resource.getUserSeq());
        
        if(parentFolderResource != null){
            resource.changeFolderPath(parentFolderResource.getFullPath(), parentFolderResource.getFullPathSeq());
        }
        
        var resourceLockQuery = resourceConverter.toResourceLockQuery(null, resource.getUserSeq(), resource.getResourceType(), resource.getParentFolderSeq(), resource.getOriginalFileName(), resource.getIsDelete());
        var resourceCopyNumbers = resourcerepository.lockMaxCopyNumbers(resourceLockQuery);
        
        var numberedNameRequestDto = new NumberedNameRequestDto(dto.isCopyFolder(), dto.getOriginalFolderName(), dto.getBaseFolderName(), dto.getCopyNumber(), null, dto.getOriginalFolderName());
        applyCopyNameToResource(resource, resourceCopyNumbers, numberedNameRequestDto);
        var token = resourceShareTokenService.createNewToken();
        resource.applyShareToken(token);

        var saved = saveFolderAndSetFullPathSeq(resource);
        // 경로 UPDATE 를 여기서 내보낸다 — 커밋 때 나가면 DB 오류(경로 길이 초과 등)가 ResourceRetryService 의 INSERT_FAIL 변환·재시도 밖에서 터져 500(W10d D1, 재현 = FolderCreateRetryIntegrationTest)
        resourcerepository.flush();

        // 반환 = 저장·경로 지정까지 끝난 식별 값(감사 FOLDER_CREATE 인자, W10c)
        return resourceConverter.toFolderCreateResult(saved);

    }
    
    @Override
    public ResourceRenameResult resolveName(ResourceRenameDto dto) {

        var nameConflictPolicy = dto.getNameConflictPolicy() == null ? NameConflictPolicy.NONE : dto.getNameConflictPolicy();
        var resourceSeq = dto.getResourceSeq();
        var userSeq = dto.getUserSeq();

        // 고칠 행 = 관리 엔티티 한 번 로드(W10, 7-8) — 감사 "변경 전" 이름·부모도 여기서 읽는다(구 감사용 findResource DTO 재조회 + 메모리 엔티티 + 리포 재로드).
        // 조건은 구 findResource 와 같음(미삭제·소유 계정 노출, 타입 무관) — 없으면 NOT_FOUND. 첫 로드에서 잠금(10-14 — 같은 행 동시 변경 직렬화, 고칠 행 잠금 규칙 통일)
        // ncp=AUTO 는 잠김 확인을 건너뛴다(구 @CheckResourceLock 의 nameConflictPolicy 분기 그대로 — 잠금을 쥔 편집기 자신의 이름 변경)
        var entity = nameConflictPolicy == NameConflictPolicy.AUTO
                ? lockForChange(resourceSeq, userSeq, null, false)
                : lockForChangeUnlocked(resourceSeq, userSeq, null, false);
        var beforeName = entity.getResourceName();
        var parentFolderSeq = entity.getParentFolderSeq();

        switch (nameConflictPolicy){
            case NONE -> {
                // 중복 검사 위치 = 잠근 행의 실제 부모, 대상 자신 제외(기존 이름 그대로 제출해도 성공)
                var existsQuery = resourceConverter.toExistsQuery(dto, parentFolderSeq);
                resourceFindService.existsResourceName(existsQuery);
                entity.rename(dto.getResourceName(), dto.getOriginalFileName(), dto.getCopyNumber() == null ? 0 : dto.getCopyNumber());
            }
            case AUTO -> {

                var resourceName = dto.getResourceName();

                if(beforeName.equals(resourceName)){
                    return ResourceRenameResult.of(resourceName, entity.getExt());
                }

                var resourceLockQuery = resourceConverter.toResourceLockQuery(resourceSeq, userSeq, ResourceType.FILE, parentFolderSeq, dto.getOriginalFileName(), false);
                var resourceCopyNumbers = resourcerepository.lockMaxCopyNumbers(resourceLockQuery);
                var numberedNameRequestDto = new NumberedNameRequestDto(dto.isCopyFile(), dto.getOriginalFileName(), dto.getBaseFileName(), dto.getCopyNumber(), dto.getExt(), dto.getOriginalFileNameWithoutExt());
                applyCopyNameToResource(entity, resourceCopyNumbers, numberedNameRequestDto);
            }
        }

        if (!beforeName.equals(entity.getResourceName())) {
            // 감사 경로 조회가 공유자면 이 행부터 JdbcClient 재귀 CTE 로 읽는다 — 변경을 먼저 내보낸다(W10 R3·결정 Y, 구 리포 안 flush 와 같은 시점)
            resourcerepository.flush();
            // TODO(TFDRIVE-1612): 감사 테스트 미작성 — 재조회 기반 before/after(중복 시 최종 이름 변동)·무거운 도메인 로직이라 H2 통합+픽스처 필요
            resourceAuditService.recordRename(EventName.FILE_RENAME, resourceSeq, parentFolderSeq,
                    entity.getResourceName(), userSeq, beforeName);
        }

        return ResourceRenameResult.of(entity.getResourceName(), entity.getExt());
    }
    
    @Override
    public void folderRenameWithChildFolders(ResourceRenameDto dto) {

        // 고칠 행 = 관리 엔티티(W10 — 구 detach 엔티티 수정 + 리포 재로드·flush·detach), 첫 로드에서 잠금(10-14 — 이동과 동시에 오면 옛 경로로
        // 자기 경로를 덮어쓰고 하위 치환(LIKE 옛경로%)이 0행이 되던 것: 늦은 쪽은 앞선 커밋까지 기다린 뒤 새 경로를 읽는다)
        var entity = lockForChange(dto.getResourceSeq(), dto.getUserSeq(), dto.getResourceType(), dto.getIsDelete());

        // 중복 검사 위치 = 잠근 행의 실제 부모, 대상 자신 제외(기존 이름 그대로 제출해도 성공)
        resourceFindService.existsResourceName(resourceConverter.toExistsQuery(dto, entity.getParentFolderSeq()));

        var beforeName = entity.getResourceName();
        // 이름 그대로면 변경 없음 — 하위 경로 치환이 하위 전체의 update_user_seq·update_date 를 덮어쓰지 않게 여기서 끝낸다
        if (beforeName.equals(dto.getResourceName())) {
            return;
        }

        String oldFullPath = StringUtil.toEmptyIfBlank(entity.getFullPath());
        entity.changeCopyResource(dto.getResourceName(), dto.getOriginalFileName(), dto.getCopyNumber());
        entity.replaceFullPath(oldFullPath);

        // 하위 경로 치환(JdbcClient)은 자식 행만(자기 제외) — 자기 행의 미반영 변경과 겹치지 않는다
        var pathUpdateQuery = resourceConverter.toPathUpdateQuery(dto.getResourceSeq(), dto.getUserSeq(), oldFullPath, entity.getFullPath(), entity.getFullPathSeq(), null, null);
        updateFullPathForChildFolders(pathUpdateQuery);

        // 감사 경로 조회가 공유자면 이 행부터 JdbcClient 재귀 CTE 로 읽는다 — 변경을 먼저 내보낸다(W10 R3·결정 Y)
        resourcerepository.flush();

        if (!beforeName.equals(entity.getResourceName())) {
            // TODO(TFDRIVE-1612): 감사 테스트 미작성 — 재조회 기반 before/after(중복 시 최종 이름 변동)·무거운 도메인 로직이라 H2 통합+픽스처 필요
            resourceAuditService.recordRename(EventName.FOLDER_RENAME, resourceConverter.toDetailData(entity), dto.getUserSeq(), beforeName);
        }
    }
    
    private ResourceMoveContext prepareMoveWithLock(Long parentFolderSeq, Integer targetUserSeq, Resource resource, String resourceName, String originalFileNameWithoutExt, boolean isDelete) {
        
        var isCopied = ResourceUtil.isCopiedResourceByName(resourceName);
        var moveContext = calculateResourceMoveResult(resource.getUserSeq(), targetUserSeq);
        boolean userChanged = moveContext.isUserChanged();
        
        // 이동 대상 소유자 — 지역 값(W10 H2: 구 엔티티 user_seq 를 대상으로 바꿨다 되돌리던 메모리 왕복 제거. 관리 엔티티라 바꾸면 그대로 UPDATE 로 나감)
        int userSeq = userChanged ? targetUserSeq : resource.getUserSeq();
        Long resourceSeq = userChanged ? null : resource.getResourceSeq();

        validateParentFolderExists(parentFolderSeq, userSeq);

        ResourceLockQuery resourceLockQuery = resourceConverter.toResourceLockQuery(resourceSeq, userSeq, resource.getResourceType(), parentFolderSeq, resourceName, isDelete);
        var resourceCopyNumbers = resourcerepository.lockMaxCopyNumbers(resourceLockQuery);
        
        var numberedNameRequestDto = new NumberedNameRequestDto(isCopied, resource.getResourceName(), resource.getOriginalFileName(), resource.getCopyNumber(), resource.getExt(), originalFileNameWithoutExt);
        applyCopyNameToResource(resource, resourceCopyNumbers, numberedNameRequestDto);
        
        resource.moveToParentFolder(parentFolderSeq);

        return moveContext;
     
    }
    
    private ResourceMoveContext calculateResourceMoveResult(Integer userSeq, Integer targetUserSeq){
        
        boolean sameOwner = targetUserSeq == null || userSeq.equals(targetUserSeq);
        
        if(sameOwner){
            return ResourceMoveContext.sameOwner();
        }
        
        return ResourceMoveContext.create(true);
    }

    @Override
    public void restoreFile(ResourceRestoreDto dto){

        // 고칠 행 = 관리 엔티티, 첫 로드에서 잠금(W10 R4 — 구 is_delete = Y 조건 JPQL 의 동시 복원 보호)
        var resource = lockForRestore(dto.getResourceSeq(), dto.getUserSeq(), dto.getResourceType());

        var resourceName = resource.getResourceName();
        var parentFolderSeq = dto.isParentExists() ? resource.getParentFolderSeq() : null;
        String originalFileNameWithoutExt = ResourceUtil.nameWithoutExtension(resourceName, resource.getExt());

        prepareMoveWithLock(parentFolderSeq, null, resource, resourceName, originalFileNameWithoutExt, false);

        resource.restore();

        // 감사 경로 조회가 공유자면 이 행부터 JdbcClient 재귀 CTE 로 읽는다(삭제 여부 포함) — 변경을 먼저 내보낸다(W10 R3·결정 Y, 구 JPQL 즉시 반영과 같은 시점)
        resourcerepository.flush();

        // TODO(TFDRIVE-1612): 감사 테스트 미작성 — 복원 도메인 로직(prepareMoveWithLock/락/조회)이 무거워 H2 통합+픽스처 필요
        resourceAuditService.record(EventName.FILE_RESTORE, resourceConverter.toDetailData(resource), dto.getUserSeq());
    }

    @Override
    public void restoreFolder(ResourceRestoreDto dto) {

        // 고칠 행 = 관리 엔티티, 첫 로드에서 잠금(W10 R4)
        var resource = lockForRestore(dto.getResourceSeq(), dto.getUserSeq(), dto.getResourceType());

        var oldFullPath = resource.getFullPath();
        var oldFullPathSeq = resource.getFullPathSeq();
        var parentFolderSeq = dto.isParentExists() ? resource.getParentFolderSeq() : null;

        updateFolderPath(resource, parentFolderSeq, null, false);

        resource.restore();

        // 하위 복원(JdbcClient)은 자식 행만(delete_parent = 이 폴더 — 자기 행은 NULL 이라 안 걸림, C-7 ④) — 부모 값은 파라미터
        var restoreChildQuery = resourceConverter.toRestoreChildQuery(resource.getResourceSeq(), resource.getUserSeq(), oldFullPath, resource.getFullPath(), oldFullPathSeq, resource.getFullPathSeq());
        restoreChildResources(restoreChildQuery);

        // 감사 경로 조회가 공유자면 이 행부터 JdbcClient 재귀 CTE 로 읽는다 — 변경을 먼저 내보낸다(W10 R3·결정 Y)
        resourcerepository.flush();

        // TODO(TFDRIVE-1612): 감사 테스트 미작성 — 복원 도메인 로직(prepareMoveWithLock/락/조회)이 무거워 H2 통합+픽스처 필요
        resourceAuditService.record(EventName.FOLDER_RESTORE, resourceConverter.toDetailData(resource), dto.getUserSeq());
    }
    
    @Override
    public void revertOverwrite(ResourceRevertOverWriteQuery query) {
        var result = resourcerepository.revertOverwrite(query);
        DriveAssert.throwIfZero(result, ResourceErrorCode.OVERWRITE_REVERT_FAIL);
    }
    
    private ResourceMoveContext updateFolderPath(Resource resource, Long parentFolderSeq, Integer targetUserSeq, boolean isDelete) {

        var resourceName = resource.getResourceName();

        var moveContext = prepareMoveWithLock(parentFolderSeq, targetUserSeq, resource, resourceName, resourceName, isDelete);

        String parentFullPath = "";
        String parentFullPathSeq = "";

        if (parentFolderSeq != null) {
            var userSeq = targetUserSeq == null ? resource.getUserSeq() : targetUserSeq;
            var parentFindQuery = resourceConverter.toFindQuery(parentFolderSeq, userSeq, resource.getResourceType(), isDelete);
            var parentResource = resourceFindService.findDetail(parentFindQuery);
            parentFullPath = parentResource.getFullPath();
            parentFullPathSeq = parentResource.getFullPathSeq();
        }

        var fullPath = parentFullPath + resource.getResourceName() + DriveConstants.PATH_SEPARATOR;
        var fullPathSeq = parentFullPathSeq + resource.getResourceSeq() + DriveConstants.PATH_SEPARATOR;

        resource.changeFolderPath(fullPath, fullPathSeq);
        
        return moveContext;
    }

    @Override
    public ResourceMoveResult fileMove(ResourceMoveDto dto) {

        var userSeq = dto.getUserSeq();
        var targetUserSeq = dto.getTargetUserSeq();
        // 고칠 행 = 관리 엔티티(W10 — 구 detach 엔티티 수정 + 리포 재로드·flush·detach / 소유자 변경은 JdbcClient), 첫 로드에서 잠금(6-c — 동시 이동)
        var resource = lockForChangeUnlocked(dto.getResourceSeq(), userSeq, dto.getResourceType(), dto.getIsDelete());

        var oldParentFolderSeq = resource.getParentFolderSeq();
        var newParentFolderSeq = dto.getParentFolderSeq();
        boolean parentChanged = !Objects.equals(oldParentFolderSeq, newParentFolderSeq);

        // 이동 전 경로는 prepareMoveWithLock이 parentFolderSeq를 새 값으로 바꾸기 전에 캡처해야 한다
        var oldParentPath = parentChanged
                ? resourceAuditService.findResourcePath(resource.getResourceSeq(), oldParentFolderSeq, userSeq)
                : null;

        var resourceName = resource.getResourceName();
        String originalFileNameWithoutExt = ResourceUtil.nameWithoutExtension(resourceName, resource.getExt());
        var moveContext = prepareMoveWithLock(newParentFolderSeq, targetUserSeq, resource, resourceName, originalFileNameWithoutExt, dto.getIsDelete());

        boolean isUserChanged  = moveContext.isUserChanged();
        Long totalSizeByte = resource.getSizeByte();

        if(isUserChanged){

            var resourceVersionSizeResult = resourceVersionService.calculateTotalVersionStats(resource.getResourceSeq(), userSeq);

            totalSizeByte += resourceVersionSizeResult.totalSizeByte();

            // 본 행 소유자(파티션 키) 변경도 변경 감지(§7-5, 부록 D-5 — 구 moveToOtherUser JdbcClient), 연관 버전 행은 벌크 JPQL
            resource.changeUserSeq(targetUserSeq);

            if(!resourceVersionSizeResult.resourceVersionSeqList().isEmpty()) {
                resourceVersionService.updateUserSeqForResource(resource.getResourceSeq(), userSeq, targetUserSeq);
            }

        }

        // 같은 tx 의 감사 경로 조회(공유자 — JdbcClient 재귀 CTE)·이동 후처리(공유 정리)가 이 행을 JdbcClient 로 읽는다 — 변경을 먼저 내보낸다(W10 R3·결정 Y, 구 리포 안 flush·JdbcClient 즉시 반영과 같은 시점)
        resourcerepository.flush();
        // 이벤트 시각 = Service 가 정한 현재 시각(결정 X)
        var occurredDatetime = Instant.now();

        if (parentChanged) {
            // TODO(TFDRIVE-1612): fileMove 흐름 미검증 — 이동 전 경로를 이동 UPDATE 전에 조회하는지는
            // 공유 체인 쿼리가 리소스의 현재 부모를 조인하므로 실DB 통합 테스트가 필요하다.
            // (recordMove의 경로 조립·before/after는 DefaultResourceAuditServiceTest로 검증됨)
            resourceAuditService.recordMove(resourceConverter.toDetailData(resource), oldParentPath, resourceName, newParentFolderSeq, userSeq, targetUserSeq);
        }

        return new ResourceMoveResult(isUserChanged, resource.getResourceName(), totalSizeByte, null, occurredDatetime);
    }

    @Override
    public ResourceMoveResult folderMove(ResourceMoveDto dto) {
        
        var userSeq = dto.getUserSeq();
        var targetUserSeq = dto.getTargetUserSeq();
        var parentFolderSeq = dto.getParentFolderSeq();

        // 목적지도 잠근 뒤 검사 — 교차 이동(A→B·B→A)이 둘 다 하위 아님을 읽고 통과하지 않게. 교착 방지로 resource_seq 작은 쪽 먼저
        boolean lockDestinationFirst = parentFolderSeq != null && parentFolderSeq < dto.getResourceSeq();
        if (lockDestinationFirst) {
            lockMoveDestination(parentFolderSeq, userSeq, targetUserSeq);
        }
        // 고칠 행 = 관리 엔티티(W10), 첫 로드에서 잠금(6-c — 동시 이동이 옛 경로로 하위 경로를 치환하지 않게)
        var resource = lockForChangeUnlocked(dto.getResourceSeq(), userSeq, dto.getResourceType(), dto.getIsDelete());
        var resourceSeq = resource.getResourceSeq();
        if (parentFolderSeq != null && !lockDestinationFirst) {
            lockMoveDestination(parentFolderSeq, userSeq, targetUserSeq);
        }

        var oldFullPath = resource.getFullPath();
        var oldFullPathSeq = resource.getFullPathSeq();

        validateNotSameLocation(resource.getParentFolderSeq(), parentFolderSeq, userSeq, targetUserSeq);

        var hierarchyStats = analyzeFolderHierarchy(resourceSeq, userSeq, parentFolderSeq);
        var moveContext = updateFolderPath(resource, parentFolderSeq, targetUserSeq, dto.getIsDelete());
        
        boolean isUserChanged = moveContext.isUserChanged();
        Integer changeUserSeq = null;

        if(isUserChanged){

            changeUserSeq = targetUserSeq;

            // 본 행 소유자(파티션 키) 변경도 변경 감지(§7-5 — 구 moveToOtherUser JdbcClient), 하위 파일·버전 행은 벌크 JPQL(조건부 다건)
            resource.changeUserSeq(targetUserSeq);
            updateUserSeqForResources(userSeq, targetUserSeq, hierarchyStats.getFileResourceSeqList());

            if(hierarchyStats.getHasVersion()){
                resourceVersionService.updateUserSeqForResources(hierarchyStats.getFileResourceSeqList(), userSeq, targetUserSeq);
            }

        }

        // 하위 폴더 경로·소유자 치환(JdbcClient)은 자기 행 제외 — 자기 행의 미반영 변경과 겹치지 않는다
        var pathUpdateQuery = resourceConverter.toPathUpdateQuery(resourceSeq, userSeq, oldFullPath, resource.getFullPath(), oldFullPathSeq, resource.getFullPathSeq(), changeUserSeq);
        updateFullPathForChildFolders(pathUpdateQuery);

        // 같은 tx 의 이동 후처리(공유 정리)가 이 행을 JdbcClient 로 읽는다 — 변경을 먼저 내보낸다(W10 R3·결정 Y)
        resourcerepository.flush();
        // 이벤트 시각 = Service 가 정한 현재 시각(결정 X)
        var occurredDatetime = Instant.now();

        return new ResourceMoveResult(isUserChanged, resource.getResourceName(), hierarchyStats.getTotalSizeByte(), hierarchyStats.getResourceSeqList(), occurredDatetime);
    }
    
    
    private ResourceVersionHierarchyStats analyzeFolderHierarchy(long resourceSeq, int userSeq, Long parentFolderSeq){
        
        var resourceVersionHierarchyDataList = resourceFindService.findResourceVersionsByFolderHierarchy(resourceSeq, userSeq);
        
        Long totalSizeByte = 0L;
        Set<Long> resourceSeqList = new HashSet<>();
        Set<Long> fileResourceSeqList = new HashSet<>();
        boolean hasVersion = false;
        
        for(var data : resourceVersionHierarchyDataList) {

            resourceSeqList.add(data.getResourceSeq());
            
            if (data.isFile()){
                
                fileResourceSeqList.add(data.getResourceSeq());
                totalSizeByte += data.getSizeByte();
                
                if(!hasVersion && data.hasVersion()){
                    hasVersion = true;
                }
            }
        }

        validateNotMovingIntoOwnSubfolder(parentFolderSeq, resourceSeqList);

        return ResourceVersionHierarchyStats.create(totalSizeByte, resourceSeqList, fileResourceSeqList, hasVersion);
    }

    /** 이동 목적지 폴더 행 잠금 — 소유자는 대상 사용자(없으면 요청자) */
    private void lockMoveDestination(long parentFolderSeq, int userSeq, Integer targetUserSeq) {
        resourcerepository.lockRow(targetUserSeq == null ? userSeq : targetUserSeq, parentFolderSeq);
    }

    /** 목적지가 자기 자신 또는 하위 폴더면 거부 — 하위 목록은 자기 행 포함 */
    private void validateNotMovingIntoOwnSubfolder(Long parentFolderSeq, Set<Long> hierarchyResourceSeqs) {
        if (parentFolderSeq != null && hierarchyResourceSeqs.contains(parentFolderSeq)) {
            throw ApiRuntimeException.type(ResourceErrorCode.UPDATE_FAIL);
        }
    }

    /** 같은 소유자의 현재 부모로 이동이면 거부 — 위치는 그대로인데 공유 정리 후처리가 돌기 때문 */
    private void validateNotSameLocation(Long currentParentFolderSeq, Long parentFolderSeq, int userSeq, Integer targetUserSeq) {
        boolean sameOwner = targetUserSeq == null || targetUserSeq == userSeq;
        if (sameOwner && Objects.equals(currentParentFolderSeq, parentFolderSeq)) {
            throw ApiRuntimeException.type(ResourceErrorCode.UPDATE_FAIL);
        }
    }
    
    @Override
    public ResourceDeleteVersionResult deleteVersion(long resourceSeq, long resourceVersionSeq, int userSeq){
        
        var lockResource = lockResourceForVersioning(resourceSeq, userSeq);

        cleanVersionIfExists(lockResource);

        // 고칠 행 = 관리 엔티티 — 잠금(DTO)·정리(JdbcClient) 뒤의 첫 JPA 로드(W10 R2, H1)
        var resource = loadForChange(resourceSeq, userSeq, ResourceType.FILE, false);

        var result = resourceVersionService.deleteVersion(resourceSeq, resourceVersionSeq, userSeq);

        // 커밋 때 변경 감지 UPDATE — 뒤에 같은 행을 JdbcClient 로 읽는 곳 없음(사용량 후처리는 tb_user) → flush 없음
        resource.decreaseVersionNumber();

        // 이벤트 시각 = Service 가 정한 현재 시각(W10 H7·결정 X — 구 감사 write-back)
        var occurredDatetime = Instant.now();
        
        return resourceConverter.toDeleteVersionResult(result.resourceSaveName(), occurredDatetime);
    }
    
    private void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = resourcerepository.deleteByUserSeqList(userSeqList);
        log.info("[리소스 삭제 : {}건]", result);
    }
    
    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        resourceVersionService.deleteByUserSeqList(userSeqList);
        deleteByUserSeqList(userSeqList);
    }
    
    /**
     * 파일명 생성 로직
     * - 최초 생성 시: 원본이면 그대로, 복사본이면 복사본 규칙에 맞게 파일명 생성
     * - 기존 파일이 존재하는 경우: 복사본 번호 증가 로직 적용4
     *
     * @param resourceCopyNumbers 기존 파일 정보 (null이면 최초 입력)
     * @param dto 업로드 요청 정보
     * @return 생성된 파일명 정보 (NumberedResourceName 객체)
     */
    private NumberedResourceName generateNumberedName(ResourceCopyNumbers resourceCopyNumbers, NumberedNameRequestDto dto){
        // 1. 최초 입력
        if (resourceCopyNumbers == null) {
            // 복사본 파일일 경우 "(number)" 형식으로 파일명 생성
            // 원본 파일일 경우 파일명 그대로 저장
            return dto.copyFile() ?
                    NumberedResourceName.initialCopyWithNumber(dto.originalFileName(), dto.baseFileName(), dto.copyNumber()) :
                    NumberedResourceName.initial(dto.baseFileName(), dto.originalFileName());
        }

        // 2. resourceName이 이미 존재할경우
        if (resourceCopyNumbers.resourceNameMax() != null){
            
            // 기존 originalName 에 파일이 없을 경우 (최초 복사본 생성)
            // 기존 originalName 에 파일이 있는 경우 (복사본 번호 증가)
            return resourceCopyNumbers.originalFileNameMax() == null ?
                    NumberedResourceName.firstCopy(dto.ext(), dto.originalFileNameWithOutExt(), dto.originalFileName()) :
                    NumberedResourceName.incrementedCopy(dto.ext(), dto.originalFileNameWithOutExt(), dto.originalFileName(), resourceCopyNumbers.originalFileNameMax());
        }
        
        // 3. resourceName이 존재하지 않을 경우
        return NumberedResourceName.initialCopyWithNumber(dto.originalFileName(), dto.baseFileName(), dto.copyNumber());
    }


}

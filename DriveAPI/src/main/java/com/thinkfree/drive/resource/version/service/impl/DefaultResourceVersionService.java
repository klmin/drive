package com.thinkfree.drive.resource.version.service.impl;

import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.query.ResourceVersionRollbackQuery;
import com.thinkfree.drive.resource.version.converter.ResourceVersionConverter;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.dto.ResourceVersionSaveDto;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.resource.version.properties.ResourceVersionProperty;
import com.thinkfree.drive.resource.version.query.ResourceVersionDeleteQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionNumberQuery;
import com.thinkfree.drive.resource.version.repository.ResourceVersionRepository;
import com.thinkfree.drive.resource.version.result.*;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.resource.version.service.ResourceVersionService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultResourceVersionService implements ResourceVersionService {
    
    private final ResourceVersionConverter resourceVersionConverter;
    private final ResourceVersionProperty resourceVersionProperty;
    private final ResourceVersionFindService resourceVersionFindService;
    private final UserService userService;
    private final ResourceVersionRepository resourceVersionRepository;
    private final ApplicationEventPublisher publisher;
    private final StorageService storageService;
    
    private void delete(ResourceVersionDeleteQuery query){
        var result = resourceVersionRepository.delete(query);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.UPDATE_FAIL);
    }
    
    @Override
    public ResourceVersionDeleteResult deleteVersion(long resourceSeq, long resourceVersionSeq, int userSeq){

        // 잠근 행 목록에서 대상(SUCCESS)을 고른다 — 같은 행을 FindService 로 다시 읽지 않는다(이중 로드 통합, W8). 없으면 구 findResourceVersion 과 같은 NOT_FOUND
        var entity = lockAllVersionsByResource(userSeq, resourceSeq).stream()
                .filter(v -> v.getResourceVersionSeq() == resourceVersionSeq && ResourceVersionStatus.SUCCESS == v.getStatus())
                .findFirst()
                .orElseThrow(() -> ApiRuntimeException.type(ResourceVersionErrorCode.NOT_FOUND));
        var deleteVersionNumber = entity.getVersionNumber();
        
        userService.decreaseUseQuotaByte(entity.getSizeByte(), entity.getUserSeq());

        var query = resourceVersionConverter.toDeleteResourceVersionQuery(resourceVersionSeq, null, userSeq);
        delete(query);

        var numberQuery = resourceVersionConverter.toResourceVersionDecreaseNumberQuery(entity.getResourceSeq(), entity.getUserSeq(), ResourceVersionStatus.SUCCESS, deleteVersionNumber);
        decreaseAllVersionNumbers(numberQuery);
        
        return new ResourceVersionDeleteResult(resourceVersionSeq, entity.getResourceSaveName(), entity.getSizeByte(), deleteVersionNumber);
    }

    @Override
    public ResourceVersionDeleteAllResult deleteAllVersions(long resourceSeq, int userSeq) {
        long totalSize = 0;
        List<String> keys = new ArrayList<>();

        var resourceVersionListQuery = resourceVersionConverter.toResourceVersionListQuery(resourceSeq, userSeq, null);
        var versions = resourceVersionFindService.findAllByResource(resourceVersionListQuery);

        if (!versions.isEmpty()) {
            deleteByResource(resourceSeq, userSeq);
            for (var v : versions) {
                totalSize += v.getSizeByte();
                keys.add(v.getResourceSaveName());
            }
        }

        return new ResourceVersionDeleteAllResult(keys, totalSize);
    }

    @Override
    public void deleteByResource(Long resourceSeq, int userSeq) {
        var query = resourceVersionConverter.toDeleteResourceVersionQuery(null, resourceSeq, userSeq);
        var result = resourceVersionRepository.deleteByResource(query);
        log.info("[deleteByResource] Delete Versions : {}", result);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.DELETE_FAIL);
    }
    
    @Override
    public void deleteByResourceSeqList(List<Long> resourceSeqList, int userSeq) {
        var query = resourceVersionConverter.toResourceVersionDeleteSeqListQuery(resourceSeqList, userSeq);
        var result = resourceVersionRepository.deleteByResourceSeqList(query);
        log.info("[deleteByResourceSeqList] Delete Versions : {}", result);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.DELETE_FAIL);
    }
    
    /** 리소스의 모든 버전 행 잠금 — 값만 받는다(엔티티를 컨텍스트에 안 올림, W8) */
    private List<ResourceVersionRowData> lockAllVersionsByResource(int userSeq, Long resourceSeq){
        return resourceVersionRepository.lockAllVersionsByResource(userSeq, resourceSeq);
    }
    private boolean existsAliveVersion(Long resourceSeq, int userSeq){
        var pendingQuery = resourceVersionConverter.toResourceVersionFindQuery(resourceSeq, userSeq, ResourceVersionStatus.SUCCESS);
        return resourceVersionRepository.existsAliveVersion(pendingQuery);
    }
    private ResourceVersionRowData findExpiredVersion(Long resourceSeq, int userSeq, ResourceVersionStatus status){
        var pendingQuery = resourceVersionConverter.toResourceVersionFindQuery(resourceSeq, userSeq, status);
        return resourceVersionRepository.findExpiredVersion(pendingQuery);
    }

    /**
     * 상태 단건 변경 — 관리 엔티티 + 변경 감지(W8, 구 JPQL @Modifying). 없으면 UPDATE_FAIL(구 0행과 같음).
     * UPDATE 는 커밋 또는 뒤이은 번호 이동 JPQL 직전 자동 flush 때 나가므로 기존 순서(상태 → 번호 이동)와 같다. 수정자·수정일은 Auditing(시큐리티가 없는 경로는 수정자 이전 값 유지).
     */
    public void updateStatus(Long resourceVersionSeq, int userSeq, ResourceVersionStatus status){
        var version = resourceVersionSeq == null ? null : resourceVersionRepository.findByUserSeqAndResourceVersionSeq(userSeq, resourceVersionSeq);
        DriveAssert.throwIfNull(version, ResourceVersionErrorCode.UPDATE_FAIL);
        version.changeStatus(status);
    }
    
    private int decreaseAllVersionNumbers(ResourceVersionNumberQuery query){
        var tempResult = resourceVersionRepository.temporaryVersionNumber(query);
        var result = resourceVersionRepository.decreaseAllVersionNumbers(query);
        return tempResult + result;
    }
    private void decreaseAllVersionNumbersWithThrow(ResourceVersionNumberQuery query){
        var result = decreaseAllVersionNumbers(query);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.UPDATE_FAIL);
    }
    private void increaseAllVersionNumbers(ResourceVersionNumberQuery query){
        var tempResult = resourceVersionRepository.temporaryVersionNumber(query);
        DriveAssert.throwIfZero(tempResult, ResourceVersionErrorCode.UPDATE_FAIL);
        var result = resourceVersionRepository.increaseAllVersionNumbers(query);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.UPDATE_FAIL);
    }
    
    /**
     * 업로드 세션 커밋(VERSION) — 리소스의 현재 내용(key·크기·등록일)을 그대로 새 버전 행(SUCCESS)으로 넘긴다. S3 복사 없음(불변 key).
     * 버전 한도에 걸리면 가장 오래된 SUCCESS 버전 행을 지우고 번호를 당긴다 — 그 크기·key 는 돌려주고 사용량 차감·삭제 등록은 호출자(같은 tx)가 한다.
     * 호출자는 사용자 행 → 리소스 행을 먼저 잠근다(규칙 8). 여기서 버전 행을 잠근다.
     */
    @Override
    public ResourceVersionPushResult pushCurrentAsVersion(ResourceVersionSaveDto current) {
        DriveAssert.throwIfNoText("current resourceSaveName is null", current.getResourceSaveName());

        List<ResourceVersionRowData> successVersions = lockAllVersionsByResource(current.getUserSeq(), current.getResourceSeq()).stream()
                .filter(v -> ResourceVersionStatus.SUCCESS == v.getStatus())
                .toList();

        // 저장 이름은 비운 채 INSERT(insertable=false), 저장 뒤 넣어야 변경 감지 UPDATE 가 나간다
        var withoutKey = resourceVersionConverter.toResourceVersionSaveDto(current.getUserSeq(), current.getResourceSeq(), current.getOwnerSeq(), null,
                current.getVersionNumber(), current.getSizeByte(), current.getVersionRegisterDate(), current.getRegisterUserSeq());
        var entity = resourceVersionConverter.toSaveResourceVersion(withoutKey, ResourceVersionStatus.PENDING);

        boolean isLimitExceeded = isLimitExceeded(successVersions.size());
        ResourceVersionRowData pruned = null;
        if (isLimitExceeded) {
            pruned = successVersions.get(0);
            delete(resourceVersionConverter.toDeleteResourceVersionQuery(pruned.getResourceVersionSeq(), null, pruned.getUserSeq()));
            entity.decreaseVersionNumber();
            var numberQuery = resourceVersionConverter.toResourceVersionDecreaseNumberQuery(pruned.getResourceSeq(), pruned.getUserSeq(), ResourceVersionStatus.SUCCESS, null);
            decreaseAllVersionNumbers(numberQuery);
        }

        resourceVersionRepository.save(entity);          // IDENTITY — 즉시 INSERT(status = DB 기본 PENDING)
        entity.applyResourceSaveName(current.getResourceSaveName());
        entity.changeStatus(ResourceVersionStatus.SUCCESS); // 같은 tx 의 변경 감지 — 다른 tx 에는 PENDING 이 보이지 않는다

        return new ResourceVersionPushResult(entity.getResourceVersionSeq(), current.getResourceSaveName(), isLimitExceeded,
                pruned == null ? 0L : pruned.getSizeByte(), pruned == null ? null : pruned.getResourceSaveName());
    }

    @Override
    public void finalizeAfterStorageUpload(Long resourceVersionSeq, int userSeq, ResourceVersionRowData deleteResourceVersion) {
        
        updateStatus(resourceVersionSeq, userSeq, ResourceVersionStatus.SUCCESS);
        
        if(deleteResourceVersion != null) {
            var deleteQuery = resourceVersionConverter.toDeleteResourceVersionQuery(deleteResourceVersion.getResourceVersionSeq(), null, deleteResourceVersion.getUserSeq());
            delete(deleteQuery);
            publisher.publishEvent(new ResourceDeletedEvent(List.of(deleteResourceVersion.getResourceSaveName())));
        }
    }
    
    /**
     * 만료된 PENDING 버전 정리 — 되돌리기 전에 스토리지로 그 저장의 업로드가 끝났는지 본다.
     * <ul>
     *   <li>끝났으면(스토리지 성공 뒤 finalize 만 실패) finalize 를 대신 마친다: PENDING → SUCCESS, 한도에서 밀려난 버전 행·객체 삭제.
     *       메타는 그대로(이미 새 내용 기준), 사용량도 그대로. 반환 null(되돌릴 것 없음).
     *       구 동작은 무조건 되돌려 원본 키(새 내용)와 메타(이전 크기·버전)가 어긋나고, 이전 내용 사본(버전 키)이 고아가 됐다.</li>
     *   <li>아니면(업로드 전·도중 중단, 스토리지 실패 뒤 롤백도 실패) 기존처럼 되돌린다. 버전 키에 사본이 남았으면 지운다(원본 키는 이전 내용 그대로).
     *       사용량은 그 저장이 예약한 새 내용 크기(= 잠근 리소스 행의 현재 size_byte)를 돌려준다 —
     *       PENDING 행의 size_byte 는 버전 키로 옮긴 이전 내용 크기라 그것을 빼면 (새 크기 − 이전 크기)만큼 사용량이 남았다.</li>
     * </ul>
     */
    public ResourceVersionRowData cleanVersionIfExists(int userSeq, Long resourceSeq, Long currentSizeByte, String resourceSaveName){
        
        lockAllVersionsByResource(userSeq, resourceSeq);
        
        Long sizeByte = 0L;
        
        var isAlive = existsAliveVersion(resourceSeq, userSeq);
        
        if(isAlive){
            throw ApiRuntimeException.type(ResourceVersionErrorCode.VERSION_PROCESSING);
        }
        
        var expiredPendingData = findExpiredVersion(resourceSeq, userSeq, ResourceVersionStatus.PENDING);
        
        if(expiredPendingData != null && isUploadCompleted(resourceSaveName, expiredPendingData.getResourceVersionSeq())){
            completeExpiredPending(expiredPendingData, resourceSeq, userSeq);
            return null;
        }
        
        if(expiredPendingData != null){
            
            if (storageService.findLastModified(expiredPendingData.getResourceSaveName()).isPresent()) {
                publisher.publishEvent(new ResourceDeletedEvent(List.of(expiredPendingData.getResourceSaveName())));
            }
            
            sizeByte -= currentSizeByte;
            
            var deleteQuery = resourceVersionConverter.toDeleteResourceVersionQuery(expiredPendingData.getResourceVersionSeq(), null, expiredPendingData.getUserSeq());
            delete(deleteQuery);
            
            var expiredDeleteData = findExpiredVersion(resourceSeq, userSeq, ResourceVersionStatus.DELETE);
            
            if(expiredDeleteData != null){
                
                var increaseQuery = resourceVersionConverter.toResourceVersionIncreaseNumberQuery(expiredPendingData.getResourceSeq(), expiredPendingData.getUserSeq(), ResourceVersionStatus.SUCCESS, null);
                increaseAllVersionNumbers(increaseQuery);
                
                sizeByte += expiredDeleteData.getSizeByte();
                updateStatus(expiredDeleteData.getResourceVersionSeq(), expiredDeleteData.getUserSeq(), ResourceVersionStatus.SUCCESS);
                
                boolean isLimitExceeded = isLimitExceeded(expiredPendingData.getVersionNumber());

                if(isLimitExceeded){
                    // 결과 조립은 값으로 — 구 엔티티 메모리 변경(increaseVersionNumber)은 행을 고치지 않는 반환용이었다
                    expiredPendingData = expiredPendingData.withVersionNumber(expiredPendingData.getVersionNumber() + 1);
                }
            }
            
            if(sizeByte != 0){
                userService.increaseUseQuotaByte(sizeByte, userSeq);
            }
            
            return expiredPendingData;
            
        }
        
        return null;
    }
    
    /**
     * 업로드 완료 판정 — 저장 흐름은 원본 키를 버전 키로 복사한 뒤 원본 키에 새 내용을 올리면서 그 저장의 버전 seq 를 저장 마커로 남긴다
     * (copyAndUpload·backUpAndRestore). 원본 키의 마커가 이 PENDING 의 seq 이면 업로드까지 끝난 것, 다르거나 없으면(직전 저장의 마커·마커 도입 전 객체) 안 끝난 것.
     * 구 판정은 수정 시각 비교였는데 S3 Last-Modified 가 초 단위라, 직전 저장의 업로드와 이번 복사가 같은 초면 업로드 전에 끊겨도 완료로 오판했다.
     */
    private boolean isUploadCompleted(String resourceSaveName, Long resourceVersionSeq) {
        return storageService.findSaveMarker(resourceSaveName)
                             .map(String.valueOf(resourceVersionSeq)::equals)
                             .orElse(false);
    }
    
    /** finalizeAfterStorageUpload 와 같은 마무리 — 밀려난 버전은 만료된 DELETE 행(그 저장이 밀어낸 것). */
    private void completeExpiredPending(ResourceVersionRowData expiredPendingData, Long resourceSeq, int userSeq) {
        var pushedOut = findExpiredVersion(resourceSeq, userSeq, ResourceVersionStatus.DELETE);
        finalizeAfterStorageUpload(expiredPendingData.getResourceVersionSeq(), userSeq, pushedOut);
    }
    
    public void rollbackResourceVersionMetadata(ResourceVersionRollbackQuery query, ResourceVersionRowData rollbackData){
        
        var deleteQuery = resourceVersionConverter.toDeleteResourceVersionQuery(query.getResourceVersionSeq(), null, query.getUserSeq());
        delete(deleteQuery);
        
        if(rollbackData != null) {
            
            var increaseQuery = resourceVersionConverter.toResourceVersionIncreaseNumberQuery(rollbackData.getResourceSeq(), rollbackData.getUserSeq(), ResourceVersionStatus.SUCCESS, null);
            increaseAllVersionNumbers(increaseQuery);
            
            updateStatus(rollbackData.getResourceVersionSeq(), rollbackData.getUserSeq(), ResourceVersionStatus.SUCCESS);
            
            userService.increaseUseQuotaByte(rollbackData.getSizeByte(), rollbackData.getUserSeq());
        }
        
    }
    
    @Transactional(readOnly = true)
    @Override
    public ResourceVersionStatsResult calculateTotalVersionStats(long resourceSeq, int userSeq) {
        
        var resourceVersionListQuery = resourceVersionConverter.toResourceVersionListQuery(resourceSeq, userSeq, null);
        var versions = resourceVersionFindService.findAllByResource(resourceVersionListQuery);
        
        long totalSizeByte = 0L;
        List<Long> resourceVersionSeqList = new ArrayList<>(versions.size());
        
        for(var stat : versions) {
            resourceVersionSeqList.add(stat.getResourceVersionSeq());
            totalSizeByte += stat.getSizeByte();
        }
        
        return new ResourceVersionStatsResult(resourceVersionSeqList, totalSizeByte);
    }
    
    @Override
    public void updateUserSeqForResource(long resourceSeq, int userSeq, int changeUserSeq) {
        var query = resourceVersionConverter.toResourceVersionUserUpdateQuery(resourceSeq, userSeq, changeUserSeq);
        var result = resourceVersionRepository.updateUserSeqForResource(query);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.UPDATE_FAIL);
    }
    
    @Override
    public void updateUserSeqForResources(Collection<Long> resourceSeqList, int userSeq, int changeUserSeq) {
        var query = resourceVersionConverter.toResourcesVersionUserUpdateQuery(resourceSeqList, userSeq, changeUserSeq);
        int result = resourceVersionRepository.updateUserSeqForResources(query);
        DriveAssert.throwIfZero(result, ResourceVersionErrorCode.UPDATE_FAIL);
    }
    
    @Override
    public void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = resourceVersionRepository.deleteByUserSeqList(userSeqList);
        log.info("[리소스 버전 삭제 : {}건]", result);
    }
    
    private boolean isLimitExceeded(int size){
        return size >= resourceVersionProperty.limit();
    }
    

}

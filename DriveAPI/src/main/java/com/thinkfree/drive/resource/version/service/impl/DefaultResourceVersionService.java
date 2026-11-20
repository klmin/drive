package com.thinkfree.drive.resource.version.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
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
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    private int decreaseAllVersionNumbers(ResourceVersionNumberQuery query){
        var tempResult = resourceVersionRepository.temporaryVersionNumber(query);
        var result = resourceVersionRepository.decreaseAllVersionNumbers(query);
        return tempResult + result;
    }
    private void decreaseAllVersionNumbersWithThrow(ResourceVersionNumberQuery query){
        var result = decreaseAllVersionNumbers(query);
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

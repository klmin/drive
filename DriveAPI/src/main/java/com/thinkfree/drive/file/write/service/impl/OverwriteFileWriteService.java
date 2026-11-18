package com.thinkfree.drive.file.write.service.impl;

import com.thinkfree.drive.common.annotation.ApiRetryable;
import com.thinkfree.drive.file.write.service.FileWriteService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.query.ResourceRevertOverWriteQuery;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OverwriteFileWriteService implements FileWriteService {
    
    private final ResourceService resourceService;
    private final QuotaService quotaService;
    private final UserService userService;
    private final StorageService storageService;
    
    @Override
    public ResourceSaveType supports() {
        return ResourceSaveType.OVERWRITE;
    }
    
    @ApiRetryable
    @Transactional
    @Override
    public ResourceSaveResult prepareMeta(FileUploadDto dto, int requestUserSeq) {
        
        try{
            var userSeq = dto.getUserSeq();
            // 잠금 순서 = 사용자 → 리소스(Duplicate·Version 과 같게) — 같은 파일의 동시 덮어쓰기·버전 저장 교착 방지. 아래 reserveQuotaInTx 의 재잠금은 같은 tx 라 대기 없음
            userService.lockUserBySeq(userSeq);
            var resource = resourceService.lockResourceForVersioning(dto.getResourceSeq(), userSeq);
            var additionalBytes = dto.getFileSize() - resource.getSizeByte();
            quotaService.reserveQuotaInTx(additionalBytes, userSeq, requestUserSeq);
            return resourceService.updateWithOverwrite(dto);
        }catch(DataAccessException | IllegalStateException e){
            log.error("[Failed to prepareMeta fail] retryCount : {}, userSeq: {}, resourceSeq : {}, requestUserSeq : {}",
                   RetryUtil.getRetryCount(), dto.getUserSeq(), dto.getResourceSeq(), requestUserSeq, e);
            throw ApiRuntimeException.type(ResourceErrorCode.OVERWRITE_FAIL);
        }
    }
   
    /** 스토리지 실패 보상 — 메타 되돌리기 + 사용량 감소를 한 트랜잭션, Duplicate·Version 과 같이 재시도 */
    @ApiRetryable
    @Transactional
    @Override
    public void rollback(ResourceSaveResult result) {
        try {
            // 되돌릴 크기 = 덮어쓰기 전 크기(restoreSizeByte), 현재 값 조건 = 덮어쓴 새 크기(sizeByte)·수정자·수정일
            var resourceRevertOverwriteQuery = new ResourceRevertOverWriteQuery(result.userSeq(), result.resourceSeq(), result.restoreSizeByte(), result.restoreUpdateUserSeq(),
                    result.restoreUpdateDate(), result.sizeByte(), result.updateUserSeq(), result.updateDate() == null ? null : result.updateDate().truncatedTo(ChronoUnit.SECONDS));
            resourceService.revertOverwrite(resourceRevertOverwriteQuery);
            userService.decreaseUseQuotaByte(result.rollbackSizeByte(), result.userSeq());
        } catch (DataAccessException | IllegalStateException e) {
            log.error("[OverwriteFileWriteService rollback fail] retryCount : {}, userSeq : {}, resourceSeq : {}",
                    RetryUtil.getRetryCount(), result.userSeq(), result.resourceSeq(), e);
            throw ApiRuntimeException.type(ResourceErrorCode.OVERWRITE_FAIL);
        }
    }
    
    /** 원본 키를 새 내용으로 — 출처별(현재 호출자는 업로드 파일뿐) */
    @Override
    public void doStorage(FileUploadDto dto, ResourceSaveResult result) {
        String key = result.resourceSaveName();
        FileWriteService.uploadTo(storageService, dto.resolveContentSource(), key);
    }
    
    @Recover
    public ResourceSaveResult saveFolderWithCopyCheckRecover(ApiRuntimeException e, FileUploadDto dto, int requestUserSeq){
        log.error("[Failed to prepareMeta retry failed] message : {}, useSeq : {}, resourceSeq : {}, requestUserSeq : {}, retryCount : {}",
                e.getMessage(), dto.getUserSeq(), dto.getResourceSeq(), requestUserSeq, RetryUtil.getRetryCount());
        throw e;
    }

    @Recover
    public void rollbackRecover(ApiRuntimeException e, ResourceSaveResult result) {
        // 보상 최종 실패 = 메타는 새 크기·사용량 과계상인데 스토리지는 이전 내용 — 운영 추적을 위해 명시 로그 후 전파
        log.error("[OverwriteFileWriteService rollback retry failed] message : {}, userSeq : {}, resourceSeq : {}, retryCount : {}",
                e.getMessage(), result.userSeq(), result.resourceSeq(), RetryUtil.getRetryCount());
        throw e;
    }
}

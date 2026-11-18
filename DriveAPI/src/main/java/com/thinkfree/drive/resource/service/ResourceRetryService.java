package com.thinkfree.drive.resource.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceDeleteVersionResult;
import com.thinkfree.drive.resource.result.ResourceFolderCreateResult;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.retry.RetryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceRetryService {

    private final ResourceService resourceService;
    
    @Retryable(
           retryFor = { ApiRuntimeException.class },
           backoff = @Backoff(delay = 500)
    )
    @Transactional
    public ResourceFolderCreateResult saveFolderWithCopyCheck(FolderUploadDto dto) {
        try{
           return resourceService.saveFolderWithCopyCheck(dto);
        }catch(DataAccessException | IllegalStateException | ApiRuntimeException e){
            log.error("[Failed to saveFolderWithCopyCheck fail] retryCount : {}, userSeq: {}, originalFolderName : {}",
                   RetryUtil.getRetryCount(), dto.getUserSeq(), dto.getOriginalFolderName(), e);
            throw ApiRuntimeException.type(ResourceErrorCode.INSERT_FAIL);
        }
   }

    /**
     * 버전 삭제 + DELETE_FAIL 변환. 호출부(VersionUseCase.deleteVersion)가 트랜잭션을 열고 그 전체를 재시도한다 — 합류 중에는 여기서 재시도하지 않는다
     * (합류한 재시도는 rollback-only 공유 트랜잭션이라 무의미하거나 커밋 때 UnexpectedRollbackException, 10-14 점검 2-a). 트랜잭션 밖에서 부르면 기존처럼 재시도.
     */
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    @Transactional
    public ResourceDeleteVersionResult deleteVersion(long resourceSeq, long resourceVersionSeq, int userSeq) {
        try {
            return resourceService.deleteVersion(resourceSeq, resourceVersionSeq, userSeq);
        } catch (DataAccessException | IllegalStateException | ApiRuntimeException e) {
            log.error("[Failed to deleteVersion] retryCount: {}, resourceSeq: {}, resourceVersionSeq: {}, userSeq: {}",
                    RetryUtil.getRetryCount(), resourceSeq, resourceVersionSeq,userSeq, e);
            throw ApiRuntimeException.type(ResourceVersionErrorCode.DELETE_FAIL);
        }
    }

    @Recover
    public ResourceFolderCreateResult saveFolderWithCopyCheckRecover(ApiRuntimeException e, FolderUploadDto dto){
        log.error("[Resource saveFolderWithCopyCheckRecover retry failed] message : {}, useSeq : {}, originalFolderName : {}, retryCount : {}",
                e.getMessage(), dto.getUserSeq(), dto.getOriginalFolderName(), RetryUtil.getRetryCount());
        throw e;
    }

    @Recover
    public ResourceDeleteVersionResult deleteVersionRecover(RuntimeException e, long resourceSeq, long resourceVersionSeq, int userSeq){
        log.error("[Resource rollbackVersion retry failed] message : {}, resourceSeq : {}, resourceVersionSeq : {}, userSeq : {}, retryCount : {}",
                e.getMessage(), resourceSeq, resourceVersionSeq, userSeq, RetryUtil.getRetryCount());
        throw e;
    }
    
}

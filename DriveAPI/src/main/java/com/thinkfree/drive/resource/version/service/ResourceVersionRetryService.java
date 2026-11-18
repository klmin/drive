package com.thinkfree.drive.resource.version.service;

import com.thinkfree.drive.global.exception.ApiException;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceVersionRetryService {
    
    private final ResourceVersionService resourceVersionService;
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            backoff = @Backoff(delay = 500)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeAfterStorageUpload(Long resourceVersionSeq, int userSeq, ResourceVersionRowData deleteResourceVersion) {
        try {
            resourceVersionService.finalizeAfterStorageUpload(resourceVersionSeq, userSeq, deleteResourceVersion);
        } catch (DataAccessException | IllegalStateException | ApiRuntimeException e) {
            log.error("[Failed to finalizeAfterStorageUpload] retryCount: {}, resourceVersionSeq: {}, userSeq: {}, type: {}",
                    RetryUtil.getRetryCount(), resourceVersionSeq, userSeq, e);
            throw ApiRuntimeException.type(ResourceVersionErrorCode.VERSION_PROCESSING_ERROR);
        }
    }
    
    @Recover
    public void finalizeAfterStorageUploadRecover(ApiRuntimeException e, Long resourceVersionSeq, int userSeq, ResourceVersionRowData deleteResourceVersion){
        log.error("[ResourceVersion finalizeAfterStorageUpload retry failed] message : {}, resourceVersionSeq : {}, userSeq : {}, retryCount : {}",
                e.getMessage(), resourceVersionSeq, userSeq, RetryUtil.getRetryCount());
        throw e;
    }
}

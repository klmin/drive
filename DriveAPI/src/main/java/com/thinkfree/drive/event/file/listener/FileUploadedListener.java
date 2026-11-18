package com.thinkfree.drive.event.file.listener;

import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.event.file.converter.FileEventConverter;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessQuotaChangeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileUploadedListener {
    
    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    private final FileEventConverter fileEventConverter;
    
    @TransactionalEventListener(fallbackExecution = true)
    @Async
    public void onFileUploaded(FileUploadedEvent event) {
        PostProcessQuotaChangeDto dto = fileEventConverter.toPostProcessQuotaChangeDto(event);
        try {
            postProcessingOrchestratorRetry.processQuotaIncreaseAndNotify(dto);
        }catch (ApiRuntimeException e){
            log.error("[onFileUploaded Exception] : ", e);
        }
    }
}

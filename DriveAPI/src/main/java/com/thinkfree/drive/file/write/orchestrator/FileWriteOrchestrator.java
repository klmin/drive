package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.file.write.service.FileWriteService;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FileWriteOrchestrator {

    private final Map<ResourceSaveType, FileWriteService> handlers;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SystemConfigFindService systemConfigFindService;
    private final UserFindService userFindService;

    public FileWriteOrchestrator(List<FileWriteService> fileWriteServices, ApplicationEventPublisher applicationEventPublisher, SystemConfigFindService systemConfigFindService, UserFindService userFindService){

        EnumMap<ResourceSaveType, FileWriteService> map = new EnumMap<>(ResourceSaveType.class);

        for (FileWriteService service : fileWriteServices) {
            ResourceSaveType type = service.supports();
            FileWriteService old = map.put(type, service);
            if (old != null) {
                throw new IllegalStateException(
                    "Duplicate FileWriteService for type: " + type
                    + " (" + old.getClass().getName() + ", " + service.getClass().getName() + ")"
                );
            }
        }

        this.handlers = Collections.unmodifiableMap(map);
        this.applicationEventPublisher = applicationEventPublisher;
        this.systemConfigFindService = systemConfigFindService;
        this.userFindService = userFindService;
    }
    
    public ResourceSaveResult write(FileUploadDto dto, int requestUserSeq) {
        
        ResourceSaveType saveType = dto.getResourceSaveType();
        
        if (saveType == null) {
            throw new IllegalArgumentException("ResourceSaveType must not be null");
        }
        
        FileWriteService handler = handlers.get(saveType);

        if (handler == null) {
            throw new IllegalStateException("No FileWriteService found for type: " + saveType);
        }

        Integer ownerTenantSeq = userFindService.findTenantSeqByUserSeq(dto.getUserSeq());
        systemConfigFindService.validateFileSizeLimit(ownerTenantSeq, dto.getFileSize());

        var result = handler.prepareMeta(dto, requestUserSeq);
        
        // 데이터 롤백은 스토리지 실패에만 — 스토리지가 성공한 뒤의 단계(마무리·후처리) 실패로 메타를 되돌리면 스토리지 내용과 어긋난다
        try {
            handler.doStorage(dto, result);
        } catch (StorageException e) {
            // 보상이 끝내 실패해도 응답은 원인(스토리지 오류) — 보상 실패는 suppressed 로 붙인다(상세 로그는 핸들러 rollbackRecover)
            try {
                handler.rollback(result);
            } catch (RuntimeException rollbackException) {
                e.addSuppressed(rollbackException);
            }
            throw e;
        }

        try {
            handler.afterStorage(result);
        } catch (RuntimeException e) {
            log.error("[FileWriteOrchestrator afterStorage fail - 롤백 안 함] saveType : {}, userSeq : {}, resourceSeq : {}",
                    saveType, result.userSeq(), result.resourceSeq(), e);
        }

        // 사용량 알림 등 후처리는 비동기 리스너(FileUploadedListener) — 실패해도 업로드 결과에 영향 없음.
        // 발행 자체가 실패해도(실행기 종료 중, 큐 포화가 30초 넘게 지속 — AsyncConfig) 저장은 끝났으므로 로그만
        try {
            applicationEventPublisher.publishEvent(new FileUploadedEvent(result.userSeq(), requestUserSeq, result.occurredDatetime()));
        } catch (RuntimeException e) {
            log.error("[FileWriteOrchestrator publish FileUploadedEvent fail - 저장은 완료] saveType : {}, userSeq : {}, resourceSeq : {}",
                    saveType, result.userSeq(), result.resourceSeq(), e);
        }

        return result;
    }
    
}

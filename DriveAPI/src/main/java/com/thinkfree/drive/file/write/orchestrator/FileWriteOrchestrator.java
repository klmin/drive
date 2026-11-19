package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.file.write.service.FileWriteService;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.upload.session.service.UploadSessionWriter;
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
    private final UploadSessionWriter uploadSessionWriter;

    public FileWriteOrchestrator(List<FileWriteService> fileWriteServices, ApplicationEventPublisher applicationEventPublisher, SystemConfigFindService systemConfigFindService, UserFindService userFindService,
                                 UploadSessionWriter uploadSessionWriter){

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
        this.uploadSessionWriter = uploadSessionWriter;
    }
    
    public ResourceSaveResult write(FileUploadDto dto, int requestUserSeq) {
        
        ResourceSaveType saveType = dto.getResourceSaveType();
        
        if (saveType == null) {
            throw new IllegalArgumentException("ResourceSaveType must not be null");
        }
        
        Integer ownerTenantSeq = userFindService.findTenantSeqByUserSeq(dto.getUserSeq());
        systemConfigFindService.validateFileSizeLimit(ownerTenantSeq, dto.getFileSize());

        // 업로드 세션 재설계 3단계 — 신규(DUPLICATE)는 세션 → 저장 → 커밋 경로. 사용량 알림 이벤트는 커밋 tx 가 발행한다.
        // VERSION·OVERWRITE 는 다음 단계에서 옮긴 뒤 이 클래스를 지운다(docs/design/upload-session-redesign.md §12)
        if (saveType.isDuplicate()) {
            return uploadSessionWriter.writeDuplicate(dto, requestUserSeq);
        }

        FileWriteService handler = handlers.get(saveType);

        if (handler == null) {
            throw new IllegalStateException("No FileWriteService found for type: " + saveType);
        }

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

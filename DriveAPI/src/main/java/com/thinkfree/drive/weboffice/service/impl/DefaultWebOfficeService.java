package com.thinkfree.drive.weboffice.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.global.exception.ApiException;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.converter.WebOfficeToResourceConverter;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.weboffice.converter.WebOfficeConverter;
import com.thinkfree.drive.weboffice.enums.WebOfficeInfoType;
import com.thinkfree.drive.weboffice.enums.WebOfficeMode;
import com.thinkfree.drive.weboffice.enums.WebOfficeType;
import com.thinkfree.drive.weboffice.support.CursorColors;
import com.thinkfree.drive.weboffice.dto.*;
import com.thinkfree.drive.weboffice.service.WebOfficeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultWebOfficeService implements WebOfficeService {

    private final WebOfficeToResourceConverter webOfficeToResourceConverter;
    private final ResourceConverter resourceConverter;
    private final WebOfficeConverter webOfficeConverter;
    private final ResourceFindService resourceFindService;
    private final FileWriteOrchestrator fileWriteOrchestrator;
    private final StorageService storageService;
    private final ResourceAuditService resourceAuditService;

    @Override
    public WebOfficeInfoResult fetchInfo(WebOfficeInfoDto dto){
        ResourceDetailData resource = findByResource("info", dto.getResourceSeq(), dto.getUserSeq(), dto.getLoginUserSeq(), e -> null);
        if (resource == null) return null;

        EventName eventName = resolveInfoEventName(dto);
        if (eventName != null) {
            resourceAuditService.record(eventName, resource, dto.getUserSeq(), dto.getActor());
        }

        String cursorColor = dto.getCursorColor() != null ? dto.getCursorColor() : CursorColors.random();
        return webOfficeConverter.toInfoResult(resource.getResourceType(), resource.getResourceName(), resource.getUpdateDate(), true, true, false, dto.getLoginUserName(), resource.getSizeByte(), dto.getProfileImage(), cursorColor, resource.getCurrentVersionNumber());
    }
    
    @Override
    public WebOfficeGetResult loadFile(WebOfficeGetDto dto){
        
        ResourceDetailData resource = findByResource("get", dto.getResourceSeq(), dto.getUserSeq(), dto.getLoginUserSeq(), e -> {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR);
        });
        
        try{
            var fileData = storageService.getFileData(resource.getResourceSaveName());
            resourceAuditService.record(resolveOpenEventName(dto.getWebOfficeType()), resource, dto.getUserSeq(), dto.getActor());
            return webOfficeConverter.toGetResult(fileData.content());
        }catch(StorageException e){
            log.error("[WebOfficeService get getFileData Exception] resourceSeq : {}, loginUserSeq : {}", dto.getResourceSeq(), dto.getLoginUserSeq(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
    }
    
    @Override
    public WebOfficeSaveResult save(WebOfficeSaveDto dto) {
        
        Long resourceSeq = dto.getResourceSeq();
        Integer userSeq = dto.getUserSeq();
        Integer loginUserSeq = dto.getLoginUserSeq();
        long updatedSize = dto.getSize();
        String methodName = "save";
        
        ResourceDetailData resource = findByResource(methodName, resourceSeq, userSeq, loginUserSeq, e -> {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR);
        });
        
        if(dto.getWebOfficeSaveType() == null || dto.getWebOfficeSaveType().shouldSaveWithVersioning()){
            
            var fileData = new FileData(resource.getContentType(), dto.getInputStream(), updatedSize);
            var fileUploadDto = webOfficeToResourceConverter.toFileUploadDto(resourceSeq, userSeq, updatedSize, loginUserSeq)
                    .toBuilder().contentSource(new FileContentSource.Stream(fileData)).build();

            // VERSION 핸들러(FileWriteOrchestrator) — 쿼터 예약·메타 저장 한 트랜잭션, 스토리지 실패만 롤백, 사용량 알림은 비동기 이벤트(5차 패스 D1)
            executeOperation(methodName, "fileWrite", resourceSeq, userSeq, loginUserSeq,
                    () -> fileWriteOrchestrator.write(fileUploadDto, loginUserSeq), () -> {});

            resourceAuditService.record(EventName.FILE_EDIT, resource, userSeq, dto.getActor());

        }

        return webOfficeConverter.toSaveResult(dto.getDocId());
    }
    
    /**
     * info 요청 유형별로 기록할 이벤트를 정한다. 문서 열기·미리보기는 get 요청에서 기록하므로 다운로드만 남는다.
     */
    private EventName resolveInfoEventName(WebOfficeInfoDto dto) {
        if (dto.getType() == null || dto.getType().isBlank()) {
            return null;
        }

        WebOfficeInfoType infoType = WebOfficeInfoType.findByAdapterParam(dto.getType());
        if (infoType == null) {
            log.info("[WebOffice info] 미지원 요청 유형 - 감사 로그 미기록. type={}", dto.getType());
            return null;
        }

        return switch (infoType) {
            case DOWNLOAD, PDF_DOWNLOAD -> EventName.FILE_DOWNLOAD;
            case OPEN, PRINT -> null;
        };
    }

    /** 뷰어 모드는 미리보기, 편집기·관전 모드는 문서 열기로 기록한다. */
    private EventName resolveOpenEventName(WebOfficeType webOfficeType) {
        return webOfficeType.getMode() == WebOfficeMode.VIEWER ? EventName.FILE_PREVIEW : EventName.FILE_OPEN;
    }

    private <T> T executeOperation(String methodName, String serviceName, Long resourceSeq, Integer userSeq, Integer loginUserSeq, Supplier<T> supplier, Runnable rollbackRunnable) {
        try {
            return supplier.get();
        } catch (ApiRuntimeException | StorageException e) {
            webOfficeServiceErrorLog(methodName, serviceName, resourceSeq, userSeq, loginUserSeq, e);
            rollbackRunnable.run();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private void executeOperation(String methodName, String serviceName, Long resourceSeq, Integer userSeq, Integer loginUserSeq, Runnable runnable, Runnable rollbackRunnable) {
        executeOperation(methodName, serviceName, resourceSeq, userSeq, loginUserSeq,
                () -> {
                    runnable.run();
                    return null;
                }, rollbackRunnable);
    }
    
    private ResourceDetailData findByResource(String methodName, Long resourceSeq, Integer userSeq, Integer loginUserSeq, Function<Exception, ResourceDetailData> exceptionHandler){
        
        var query = resourceConverter.toFindQuery(resourceSeq, userSeq, ResourceType.FILE, null);
        
        try {
            return resourceFindService.findDetail(query);
        }catch(ApiRuntimeException | StorageException e){
            webOfficeServiceErrorLog(methodName, "findByResource", resourceSeq, userSeq, loginUserSeq, e);
            return exceptionHandler.apply(e);
        }
    }
    
    private void webOfficeServiceErrorLog(String methodName, String serviceName, Long resourceSeq, Integer userSeq, Integer loginUserSeq, Exception e){
        log.error("[WebOfficeService {} - {} Exception] resourceSeq : {}, userSeq : {}, loginUserSeq : {}",
                methodName, serviceName, resourceSeq, userSeq, loginUserSeq, e);
    }
    
    
}

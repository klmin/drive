package com.thinkfree.drive.document.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.domain.FileKey;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.provider.FileProvider;
import com.thinkfree.drive.document.converter.DocumentConverter;
import com.thinkfree.drive.document.converter.DocumentToResourceConverter;
import com.thinkfree.drive.document.dto.DocumentCreateDto;
import com.thinkfree.drive.document.dto.DocumentCreateResult;
import com.thinkfree.drive.document.enums.DocumentTemplateType;
import com.thinkfree.drive.document.service.DocumentService;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDocumentService implements DocumentService {
    
    private final MessageSourceService messageSourceService;
    private final DocumentToResourceConverter documentToResourceConverter;
    private final DocumentConverter documentConverter;
    private final FileWriteOrchestrator fileWriteOrchestrator;
    private final FileProvider staticResourceProvider;
    private final ResourceAuditService resourceAuditService;

    public DocumentCreateResult create(DocumentCreateDto dto) {
        
        DocumentTemplateType templateType = dto.getTemplateType();
        var staticResourceKey = new FileKey(templateType.getClasspath(LocaleContextHolder.getLocale()));
        FileData fileMetadata = staticResourceProvider.getFileData(staticResourceKey);
        Integer userSeq = dto.getUserSeq();
        var ownerSeq = dto.getOwnerSeq();
        String fileName = messageSourceService.getMessage(templateType.getMessageKey());
        String contentType = fileMetadata.contentType();
        long contentLength = fileMetadata.contentLength();

        // DUPLICATE 쓰기(FileWriteOrchestrator), 새 내용 = 템플릿 스트림(5차 패스) — 사용량 예약·메타 저장 한 트랜잭션, 스토리지 실패만 롤백,
        // 사용량 알림은 비동기 이벤트. 행위자(사용량 예약·알림) = ownerSeq(구 흐름과 같음)
        var fileUploadDto = documentToResourceConverter.toFileUploadDto(dto, fileName, contentType, contentLength)
                .toBuilder().contentSource(new FileContentSource.Stream(fileMetadata)).build();

        Long resourceSeq = fileWriteOrchestrator.write(fileUploadDto, ownerSeq).resourceSeq();

        resourceAuditService.record(EventName.FILE_CREATE, resourceSeq, userSeq);

        return documentConverter.toCreateResult(resourceSeq, userSeq);
    }
    
    
}

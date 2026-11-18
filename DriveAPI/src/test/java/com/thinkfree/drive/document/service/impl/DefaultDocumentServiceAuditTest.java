package com.thinkfree.drive.document.service.impl;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.provider.FileProvider;
import com.thinkfree.drive.document.converter.DocumentConverter;
import com.thinkfree.drive.document.converter.DocumentToResourceConverter;
import com.thinkfree.drive.document.dto.DocumentCreateDto;
import com.thinkfree.drive.document.enums.DocumentTemplateType;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FILE_FOLDER 카테고리 중 DefaultDocumentService.create(새 문서 생성)의 FILE_CREATE 감사 로그 기록 호출 테스트.
 */
@DisplayName("DefaultDocumentService(문서 생성) 감사 로그 기록 호출 테스트")
class DefaultDocumentServiceAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private MessageSourceService messageSourceService;
    @Mock private DocumentToResourceConverter documentToResourceConverter;
    @Mock private DocumentConverter documentConverter;
    @Mock private FileWriteOrchestrator fileWriteOrchestrator;
    @Mock private FileProvider staticResourceProvider;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private DefaultDocumentService documentService;

    private ResourceSaveResult saveResult(Long resourceSeq) {
        return new ResourceSaveResult(resourceSeq, USER, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    // EventName: FILE_CREATE
    @Test
    @DisplayName("create - FILE_CREATE 기록(새 문서 생성)")
    void create_recordsFileCreate() {
        DocumentCreateDto dto = Mockito.mock(DocumentCreateDto.class);
        given(dto.getTemplateType()).willReturn(DocumentTemplateType.WORD);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getOwnerSeq()).willReturn(USER);

        given(staticResourceProvider.getFileData(any())).willReturn(new FileData("application/vnd", new ByteArrayInputStream(new byte[0]), 10L));
        given(messageSourceService.getMessage(anyString())).willReturn("새 문서.docx");
        // 5차 패스 — 문서 생성도 FileWriteOrchestrator(DUPLICATE, 출처 = 템플릿 스트림)
        given(documentToResourceConverter.toFileUploadDto(any(DocumentCreateDto.class), anyString(), anyString(), anyLong())).willReturn(FileUploadDto.builder().build());
        given(fileWriteOrchestrator.write(any(), anyInt())).willReturn(saveResult(700L));

        documentService.create(dto);

        verify(resourceAuditService).record(EventName.FILE_CREATE, 700L, USER);
    }
}

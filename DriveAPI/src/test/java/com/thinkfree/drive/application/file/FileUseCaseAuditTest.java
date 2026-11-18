package com.thinkfree.drive.application.file;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.file.command.FileCopyCommand;
import com.thinkfree.drive.application.file.command.FileUploadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.share.service.ShareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FILE_FOLDER 카테고리 중 FileUseCase가 기록하는 FILE 이벤트의 감사 로그 기록 호출 테스트.
 * (구 api/v1 이사분 — 다운로드·미리보기 이벤트는 FileDownloadUseCaseAuditTest로 분리)
 * objectSeq/objectName/resourcePath 조립은 ResourceAuditService의 책임이라
 * "어떤 이벤트를 어떤 리소스/소유자로 기록 요청하는가"만 검증한다.
 */
@DisplayName("FileUseCase(FILE 이벤트) 감사 로그 기록 호출 테스트")
class FileUseCaseAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private FileCommandConverter fileCommandConverter;
    @Mock private ResourceFindService resourceFindService;
    @Mock private ResourceService resourceService;
    @Mock private MessageSourceService messageSourceService;
    @Mock private ShareService shareService;
    @Mock private ResourcePermissionService resourcePermissionService;
    @Mock private FileWriteOrchestrator fileWriteOrchestrator;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private FileUseCase fileUseCase;

    private ResourceDetailData resourceMock(long seq, String name) {
        ResourceDetailData r = Mockito.mock(ResourceDetailData.class);
        given(r.getResourceSeq()).willReturn(seq);
        given(r.getResourceName()).willReturn(name);
        given(r.getParentFolderSeq()).willReturn(10L);
        given(r.getResourceSaveName()).willReturn("savekey");
        return r;
    }

    private ResourceSaveResult saveResult(Long resourceSeq, Integer userSeq) {
        return new ResourceSaveResult(resourceSeq, userSeq, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    // EventName: FILE_EDIT
    @Test
    @DisplayName("upload(overwrite) - FILE_EDIT 기록(objectSeq/objectName=덮어쓴 리소스)")
    void upload_overwrite_recordsFileEdit() {
        FileUploadDto dto = Mockito.mock(FileUploadDto.class);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getResourceSaveType()).willReturn(ResourceSaveType.OVERWRITE);
        given(dto.getResourceSeq()).willReturn(100L);
        given(fileCommandConverter.toUploadDto(any(FileUploadCommand.class), anyInt())).willReturn(dto);

        given(fileWriteOrchestrator.write(any(), anyInt())).willReturn(saveResult(100L, USER));

        fileUseCase.upload(new FileUploadCommand(null, null, 100L, null, ResourceSaveType.OVERWRITE), 1, false, true);

        verify(resourceAuditService).record(EventName.FILE_EDIT, 100L, USER);
    }

    // EventName: FILE_UPLOAD
    @Test
    @DisplayName("upload(duplicate) - FILE_UPLOAD 기록(objectSeq/objectName=업로드 리소스, resourcePath=부모 폴더)")
    void upload_duplicate_recordsFileUpload() {
        FileUploadDto dto = Mockito.mock(FileUploadDto.class);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getResourceSaveType()).willReturn(ResourceSaveType.DUPLICATE);
        given(fileCommandConverter.toUploadDto(any(FileUploadCommand.class), anyInt())).willReturn(dto);

        given(fileWriteOrchestrator.write(any(), anyInt())).willReturn(saveResult(101L, USER));

        fileUseCase.upload(new FileUploadCommand(null, null, null, null, ResourceSaveType.DUPLICATE), 1, false, false);

        verify(resourceAuditService).record(EventName.FILE_UPLOAD, 101L, USER);
    }

    @Test
    @DisplayName("upload(version, 인텔릭트) - 닫기 저장은 FILE_EDIT 기록")
    void upload_versionFromIntellect_recordsFileEdit() {
        givenVersionUpload(102L);

        fileUseCase.upload(new FileUploadCommand(null, null, 102L, null, ResourceSaveType.VERSION), 1, false, true);

        verify(resourceAuditService).record(EventName.FILE_EDIT, 102L, USER);
    }

    @Test
    @DisplayName("upload(version, 업로드 창) - 새 버전 업로드는 FILE_UPLOAD 기록")
    void upload_version_recordsFileUpload() {
        givenVersionUpload(103L);

        fileUseCase.upload(new FileUploadCommand(null, null, 103L, null, ResourceSaveType.VERSION), 1, false, false);

        verify(resourceAuditService).record(EventName.FILE_UPLOAD, 103L, USER);
    }

    private void givenVersionUpload(Long resourceSeq) {
        FileUploadDto dto = Mockito.mock(FileUploadDto.class);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getResourceSaveType()).willReturn(ResourceSaveType.VERSION);
        given(dto.getResourceSeq()).willReturn(resourceSeq);
        given(fileCommandConverter.toUploadDto(any(FileUploadCommand.class), anyInt())).willReturn(dto);
        // 5차 패스 D1 — VERSION 도 FileWriteOrchestrator(VersionFileWriteService) 경로
        given(fileWriteOrchestrator.write(dto, 1)).willReturn(saveResult(resourceSeq, USER));
    }

    // EventName: FILE_COPY
    @Test
    @DisplayName("copy - FILE_COPY 기록(objectSeq/objectName=복사본 리소스)")
    void copy_recordsFileCopy() {
        ResourceDetailData origin = resourceMock(104L, "원본.txt");
        given(origin.getContentType()).willReturn("text/plain");
        given(origin.getSizeByte()).willReturn(10L);
        given(resourceFindService.findDetail(any())).willReturn(origin);

        given(messageSourceService.getMessage(anyString(), any(Object[].class))).willReturn("원본 (사본).txt");
        // 5차 패스 — 복사도 FileWriteOrchestrator(DUPLICATE, 출처 = 원본 스토리지 객체)
        given(fileCommandConverter.toCopyUploadDto(any(), any(), anyInt(), anyString(), any(), anyString(), anyLong())).willReturn(FileUploadDto.builder().build());
        given(fileWriteOrchestrator.write(any(), anyInt())).willReturn(saveResult(200L, USER));

        fileUseCase.copy(104L, new FileCopyCommand(null, null, 10L), USER);

        verify(resourceAuditService).record(EventName.FILE_COPY, 200L, USER);
    }
}

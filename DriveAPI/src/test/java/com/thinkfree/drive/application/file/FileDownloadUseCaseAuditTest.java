package com.thinkfree.drive.application.file;

import com.thinkfree.drive.application.file.command.FileDownloadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * FileDownloadUseCase가 기록하는 FILE_PREVIEW·FILE_DOWNLOAD 감사 로그 기록 호출 테스트.
 * (구 api/v1 FileUseCaseAuditTest에서 다운로드 분리 이사분)
 */
@DisplayName("FileDownloadUseCase(FILE_PREVIEW·FILE_DOWNLOAD) 감사 로그 기록 호출 테스트")
class FileDownloadUseCaseAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private FileCommandConverter fileCommandConverter;
    @Mock private ResourceFindService resourceFindService;
    @Mock private StorageService storageService;
    @Mock private DownloadTokenService downloadTokenService;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private FileDownloadUseCase fileDownloadUseCase;

    private ResourceDetailData resourceMock(long seq, String name) {
        ResourceDetailData r = Mockito.mock(ResourceDetailData.class);
        given(r.getResourceSeq()).willReturn(seq);
        given(r.getResourceName()).willReturn(name);
        given(r.getParentFolderSeq()).willReturn(10L);
        given(r.getResourceSaveName()).willReturn("savekey");
        return r;
    }

    // EventName: FILE_PREVIEW
    @Test
    @DisplayName("view - FILE_PREVIEW 기록")
    void view_recordsFilePreview() {
        ResourceDetailData resource = resourceMock(101L, "미리보기.pdf");
        given(resourceFindService.findDetail(any())).willReturn(resource);
        given(storageService.getFileData(any())).willReturn(new FileData("application/pdf", new ByteArrayInputStream(new byte[0]), 0L));

        fileDownloadUseCase.view(new FileDownloadCommand(101L, null), USER);

        verify(resourceAuditService).record(EventName.FILE_PREVIEW, resource, USER);
    }

    // EventName: FILE_OPEN (인텔릭트 본문 로드)
    @Test
    @DisplayName("open - FILE_OPEN 기록")
    void open_recordsFileOpen() {
        ResourceDetailData resource = resourceMock(102L, "노트.tfi");
        given(resourceFindService.findDetail(any())).willReturn(resource);
        given(storageService.getFileData(any())).willReturn(new FileData("application/json", new ByteArrayInputStream(new byte[0]), 0L));

        fileDownloadUseCase.open(new FileDownloadCommand(102L, null), USER);

        verify(resourceAuditService).record(EventName.FILE_OPEN, resource, USER);
    }

    @Test
    @DisplayName("download - 감사 로그 미기록")
    void download_recordsNothing() {
        ResourceDetailData resource = resourceMock(103L, "파일.txt");
        given(resourceFindService.findDetail(any())).willReturn(resource);
        given(storageService.getFileData(any())).willReturn(new FileData("text/plain", new ByteArrayInputStream(new byte[0]), 0L));

        fileDownloadUseCase.download(new FileDownloadCommand(103L, null), USER);

        verifyNoInteractions(resourceAuditService);
    }

    // EventName: FILE_DOWNLOAD (다운로드 signed URL 발급)
    @Test
    @DisplayName("createSignedUrl - FILE_DOWNLOAD 기록")
    void createSignedUrl_recordsFileDownload() {
        ResourceDetailData resource = resourceMock(103L, "다운로드.zip");
        given(resourceFindService.findDetail(any())).willReturn(resource);
        given(downloadTokenService.createSignedUrl(any(), anyInt(), any(), anyInt())).willReturn("token");

        fileDownloadUseCase.createSignedUrl(new FileDownloadCommand(103L, null), USER);

        verify(resourceAuditService).record(EventName.FILE_DOWNLOAD, resource, USER);
    }
}

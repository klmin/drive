package com.thinkfree.drive.application.folder;

import com.thinkfree.drive.application.folder.command.FolderDownloadCommand;
import com.thinkfree.drive.application.folder.converter.FolderCommandConverter;
import com.thinkfree.drive.application.resource.converter.ResourceToZipEntryConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FolderDownloadUseCase가 기록하는 FOLDER_DOWNLOAD 감사 로그 기록 호출 테스트.
 * (구 api/v1 FolderUseCaseAuditTest에서 다운로드 분리 이사분)
 */
@DisplayName("FolderDownloadUseCase(FOLDER_DOWNLOAD) 감사 로그 기록 호출 테스트")
class FolderDownloadUseCaseAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private FolderCommandConverter folderCommandConverter;
    @Mock private ResourceToZipEntryConverter resourceToZipEntryConverter;
    @Mock private ResourceFindService resourceFindService;
    @Mock private StorageService storageService;
    @Mock private DownloadTokenService downloadTokenService;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private FolderDownloadUseCase folderDownloadUseCase;

    // EventName: FOLDER_DOWNLOAD (다운로드 signed URL 발급)
    @Test
    @DisplayName("createSignedUrl - FOLDER_DOWNLOAD 기록")
    void createSignedUrl_recordsFolderDownload() {
        ResourceHierarchyData folder = Mockito.mock(ResourceHierarchyData.class);
        given(folder.getResourceSeq()).willReturn(302L);
        given(folder.getResourceName()).willReturn("다운로드폴더");
        given(folder.getParentFolderSeq()).willReturn(10L);
        given(resourceFindService.findResourceHierarchy(any())).willReturn(List.of(folder));
        given(downloadTokenService.createSignedUrl(any(), anyInt(), any(), anyInt())).willReturn("token");

        folderDownloadUseCase.createSignedUrl(new FolderDownloadCommand(302L, null), USER);

        verify(resourceAuditService).record(EventName.FOLDER_DOWNLOAD, folder, USER);
    }
}

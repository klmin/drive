package com.thinkfree.drive.application.folder;

import com.thinkfree.drive.application.folder.command.FolderCreateCommand;
import com.thinkfree.drive.application.folder.converter.FolderCommandConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.result.ResourceFolderCreateResult;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.share.service.ShareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FILE_FOLDER 카테고리 중 FolderUseCase가 기록하는 FOLDER 이벤트의 감사 로그 기록 호출 테스트.
 * objectSeq/objectName/resourcePath 조립은 ResourceAuditService의 책임이라 여기서는
 * "어떤 이벤트를 어떤 리소스/소유자로 기록 요청하는가"만 검증한다.
 * (구 api/v1 FolderUseCaseAuditTest — application 이사분. FOLDER_DOWNLOAD는 FolderDownloadUseCaseAuditTest로 분리)
 */
@DisplayName("FolderUseCase(FOLDER 이벤트) 감사 로그 기록 호출 테스트")
class FolderUseCaseAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private FolderCommandConverter folderCommandConverter;
    @Mock private ResourceRetryService resourceRetryService;
    @Mock private ResourceFindService resourceFindService;
    @Mock private ResourceService resourceService;
    @Mock private PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    @Mock private ShareService shareService;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private FolderUseCase folderUseCase;

    private ResourceFolderCreateResult folderResult(long seq, String name) {
        return new ResourceFolderCreateResult(seq, name, 10L, "부모/" + name + "/");
    }

    // EventName: FOLDER_CREATE
    @Test
    @DisplayName("create - FOLDER_CREATE 기록(폴더 생성)")
    void create_recordsFolderCreate() {
        FolderUploadDto dto = Mockito.mock(FolderUploadDto.class);
        given(dto.getUserSeq()).willReturn(USER);
        given(folderCommandConverter.toFolderUploadDto(any(FolderCreateCommand.class), anyInt())).willReturn(dto);

        ResourceFolderCreateResult folder = folderResult(300L, "신규폴더");
        given(resourceRetryService.saveFolderWithCopyCheck(any())).willReturn(folder);

        folderUseCase.create(FolderCreateCommand.builder().folderName("신규폴더").build(), USER);

        verify(resourceAuditService).record(EventName.FOLDER_CREATE, folder, USER);
    }

}

package com.thinkfree.drive.application.trash;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.trash.converter.TrashCommandConverter;
import com.thinkfree.drive.application.trash.converter.TrashInfoConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.cleanup.resource.service.ResourceCleanUpService;
import com.thinkfree.drive.cleanup.trash.service.TrashCleanUpService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.contract.ResourceBrief;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.converter.TrashToResourceConverter;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.result.ResourceDeleteResult;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.trash.converter.ResourceToTrashConverter;
import com.thinkfree.drive.trash.service.TrashFindService;
import com.thinkfree.drive.trash.service.TrashService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FILE_FOLDER 카테고리 중 TrashUseCase가 기록하는 삭제 이벤트의 감사 로그 기록 테스트.
 * 휴지통 이동(FILE_DELETE/FOLDER_DELETE)은 경로 조회와 기록이 ResourceAuditService로 통합돼
 * 기록 호출만 검증하고, 영구 삭제는 경로가 없어 여전히 기록 내용을 직접 검증한다.
 * deleteFile(휴지통 영구 삭제)의 FILE_PERMANENT_DELETE는 RESOURCE_DELETE shape(objectSeq/objectName만,
 * resourcePath 없음)이고 삭제되는 리소스(하위 포함 briefs)마다 row 1건씩 fan-out한다.
 * (deleteFolder의 FOLDER_PERMANENT_DELETE는 record를 DefaultResourceService에 위임하므로 그쪽 테스트에서 검증)
 * moveFile/FolderToTrash(휴지통 이동)의 FILE_DELETE/FOLDER_DELETE는 RESOURCE shape
 * (objectSeq/objectName/resourcePath) — 구 File/FolderUseCase delete 이사분.
 */
@DisplayName("TrashUseCase(휴지통 이동·영구 삭제) 감사 로그 기록 내용 테스트")
class TrashUseCaseAuditTest extends AuditMockTest {

    private static final int USER = 5;
    private static final int TENANT = 1;

    @Mock private TrashCommandConverter trashCommandConverter;
    @Mock private TrashInfoConverter trashInfoConverter;
    @Mock private TrashToResourceConverter trashToResourceConverter;
    @Mock private ResourceToTrashConverter resourceToTrashConverter;
    @Mock private ResourceAuditService resourceAuditService;
    @Mock private TrashFindService trashFindService;
    @Mock private TrashService trashService;
    @Mock private ResourceService resourceService;
    @Mock private ResourceFindService resourceFindService;
    @Mock private ResourceConverter resourceConverter;
    @Mock private UserService userService;
    @Mock private ResourceCleanUpService resourceCleanupService;
    @Mock private TrashCleanUpService trashCleanUpService;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;

    @InjectMocks
    private TrashUseCase trashUseCase;

    private ResourceDetailData resourceMock(long seq, String name) {
        ResourceDetailData r = Mockito.mock(ResourceDetailData.class);
        given(r.getResourceSeq()).willReturn(seq);
        given(r.getResourceName()).willReturn(name);
        given(r.getParentFolderSeq()).willReturn(10L);
        return r;
    }

    // EventName: FILE_DELETE (휴지통 이동 — 구 FileUseCaseAuditTest 이사분)
    @Test
    @DisplayName("moveFileToTrash - FILE_DELETE 기록(soft delete/휴지통 이동 시점)")
    void moveFileToTrash_recordsFileDelete() {
        ResourceDetailData resource = resourceMock(102L, "삭제대상.txt");
        given(resourceService.lockActiveUnlocked(anyLong(), anyInt(), any())).willReturn(resource);

        trashUseCase.moveFileToTrash(102L, USER);

        verify(resourceAuditService).record(EventName.FILE_DELETE, resource, USER);
    }

    // EventName: FOLDER_DELETE (휴지통 이동 — 구 FolderUseCaseAuditTest 이사분)
    @Test
    @DisplayName("moveFolderToTrash - FOLDER_DELETE 기록(soft delete/휴지통 이동 시점)")
    void moveFolderToTrash_recordsFolderDelete() {
        ResourceDetailData folder = resourceMock(301L, "삭제폴더");
        given(resourceService.lockActiveUnlocked(anyLong(), anyInt(), any())).willReturn(folder);
        given(resourceFindService.findFolderAndDescendants(any())).willReturn(List.of());

        trashUseCase.moveFolderToTrash(301L, USER);

        verify(resourceAuditService).record(EventName.FOLDER_DELETE, folder, USER);
    }

    // EventName: FILE_PERMANENT_DELETE (삭제 리소스마다 fan-out, 경로 조회 없음 → 서비스 위임)
    @Test
    @DisplayName("deleteFile - 삭제되는 리소스마다 FILE_PERMANENT_DELETE를 기록한다")
    void deleteFile_recordsFilePermanentDeletePerResource() {
        TenantContext.set(TENANT);

        var result = new ResourceDeleteResult(
                List.of(), List.of(), 100L, null,
                List.of(new ResourceBrief(600L, "영구삭제1.txt"), new ResourceBrief(601L, "영구삭제2.txt")));
        given(resourceService.deleteResourceAndVersions(anyLong(), anyInt())).willReturn(result);

        trashUseCase.deleteFile(600L, USER, "사용자5");

        verify(resourceAuditService).recordPermanentDelete(EventName.FILE_PERMANENT_DELETE, 600L, "영구삭제1.txt");
        verify(resourceAuditService).recordPermanentDelete(EventName.FILE_PERMANENT_DELETE, 601L, "영구삭제2.txt");
    }
}

package com.thinkfree.drive.application.resource;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.resource.command.ResourceDownloadCommand;
import com.thinkfree.drive.application.resource.converter.ResourceCommandConverter;
import com.thinkfree.drive.application.resource.converter.ResourceToZipEntryConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FILE_FOLDER 카테고리 중 ResourceDownloadUseCase의 멀티 다운로드 fan-out 감사 로그 기록 호출 테스트.
 * 사용자가 직접 선택한 항목(depth=0)만 항목별 FILE_DOWNLOAD/FOLDER_DOWNLOAD로 기록 요청하고,
 * 폴더 선택 시 딸려온 하위(depth>0)는 기록 대상이 아님을 검증한다.
 */
@DisplayName("ResourceDownloadUseCase(멀티 다운로드 fan-out) 감사 로그 기록 호출 테스트")
class ResourceDownloadUseCaseAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private ResourceCommandConverter resourceCommandConverter;
    @Mock private ResourceToZipEntryConverter resourceToZipEntryConverter;
    @Mock private ResourceFindService resourceFindService;
    @Mock private StorageService storageService;
    @Mock private ResourcePermissionService resourcePermissionService;
    @Mock private DownloadTokenService downloadTokenService;
    @Mock private ResourceAuditService resourceAuditService;

    @Captor private ArgumentCaptor<List<ResourceHierarchyData>> itemsCaptor;
    @Captor private ArgumentCaptor<Function<ResourceHierarchyData, EventName>> eventNameResolverCaptor;

    @InjectMocks
    private ResourceDownloadUseCase resourceDownloadUseCase;

    private ResourceHierarchyData item(long seq, String name, int depth, boolean isFolder) {
        ResourceHierarchyData d = Mockito.mock(ResourceHierarchyData.class);
        given(d.getResourceSeq()).willReturn(seq);
        given(d.getResourceName()).willReturn(name);
        given(d.getDepth()).willReturn(depth);
        given(d.getIsFolder()).willReturn(isFolder);
        given(d.getParentFolderSeq()).willReturn(10L);
        return d;
    }

    // EventName: FILE_DOWNLOAD, FOLDER_DOWNLOAD (사용자 선택 항목 depth=0만 fan-out)
    @Test
    @DisplayName("createSignedUrl - 선택 항목(depth=0)만 FILE/FOLDER_DOWNLOAD로 기록하고 하위(depth>0)는 제외")
    void createSignedUrl_fanOutOnlyTopLevelSelectedItems() {
        // 본인 소유 항목(sharedByUserSeq == requestUserSeq)이라 권한체크/공유분기는 타지 않음
        var command = new ResourceDownloadCommand(List.of(
                new ResourceDownloadCommand.DownloadItem(USER, 400L),
                new ResourceDownloadCommand.DownloadItem(USER, 401L)
        ));

        var file = item(400L, "선택파일.txt", 0, false);
        var folder = item(401L, "선택폴더", 0, true);
        var childFile = item(500L, "하위파일.txt", 1, false); // 폴더 하위 → 제외 대상
        given(resourceFindService.findResourceHierarchy(any())).willReturn(List.of(file, folder, childFile));

        resourceDownloadUseCase.createSignedUrl(command, USER, false);

        verify(resourceAuditService).recordAll(itemsCaptor.capture(), eq(USER), eventNameResolverCaptor.capture());

        // depth>0인 하위파일은 기록 대상에서 제외
        assertThat(itemsCaptor.getValue()).containsExactly(file, folder);

        var resolver = eventNameResolverCaptor.getValue();
        assertThat(resolver.apply(file)).isEqualTo(EventName.FILE_DOWNLOAD);
        assertThat(resolver.apply(folder)).isEqualTo(EventName.FOLDER_DOWNLOAD);
    }
}

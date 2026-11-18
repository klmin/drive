package com.thinkfree.drive.resource.version.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.version.converter.ResourceVersionConverter;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.resource.version.properties.ResourceVersionProperty;
import com.thinkfree.drive.resource.version.query.ResourceVersionDeleteQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionFindQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionNumberQuery;
import com.thinkfree.drive.resource.version.repository.ResourceVersionRepository;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * W8 — 버전 쓰기 흐름이 엔티티 대신 값({@link ResourceVersionRowData})을 받고, 상태는 관리 엔티티 변경 감지로 고치는지.
 * <ul>
 *   <li>deleteVersion — 잠근 행 목록에서 대상(SUCCESS)을 고른다(FindService 재조회 없음 — 이중 로드 통합), 없거나 SUCCESS 가 아니면 NOT_FOUND</li>
 *   <li>updateStatus — 고칠 행 로드 + changeStatus, 없으면 UPDATE_FAIL(구 0행)</li>
 *   <li>cleanVersionIfExists — 한도 초과 시 돌려주는 값만 번호 +1(구 엔티티 메모리 변경 대체), 잠금·만료 판정 결과 원본은 그대로</li>
 * </ul>
 * DB 반영·저장/롤백/finalize 흐름은 {@code DefaultResourceVersionRepositoryTest}·{@code VersionSaveRollbackIntegrationTest}.
 */
@DisplayName("DefaultResourceVersionService — 값 운반·상태 변경 감지(W8)")
class DefaultResourceVersionServiceChangeDetectionTest {

    private static final int USER = 2;
    private static final long RESOURCE = 500L;
    private static final Instant T = Instant.parse("2026-10-13T00:00:00Z");

    private ResourceVersionRepository repository;
    private ResourceVersionConverter converter;
    private ResourceVersionFindService findService;
    private UserService userService;
    private StorageService storageService;
    private DefaultResourceVersionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ResourceVersionRepository.class);
        converter = mock(ResourceVersionConverter.class);
        findService = mock(ResourceVersionFindService.class);
        userService = mock(UserService.class);
        storageService = mock(StorageService.class);
        service = new DefaultResourceVersionService(converter, new ResourceVersionProperty(5), findService, userService, repository,
                mock(ApplicationEventPublisher.class), storageService);

        when(converter.toDeleteResourceVersionQuery(any(), any(), anyInt())).thenReturn(mock(ResourceVersionDeleteQuery.class));
        when(converter.toResourceVersionDecreaseNumberQuery(any(), anyInt(), any(), any())).thenReturn(mock(ResourceVersionNumberQuery.class));
        when(converter.toResourceVersionIncreaseNumberQuery(any(), anyInt(), any(), any())).thenReturn(mock(ResourceVersionNumberQuery.class));
        when(converter.toResourceVersionFindQuery(any(), anyInt(), any())).thenAnswer(inv -> ResourceVersionFindQuery.builder().status(inv.getArgument(2)).build());
        when(repository.delete(any())).thenReturn(1);
        when(repository.temporaryVersionNumber(any())).thenReturn(1);
        when(repository.increaseAllVersionNumbers(any())).thenReturn(1);
        when(repository.decreaseAllVersionNumbers(any())).thenReturn(1);
        when(storageService.findLastModified(anyString())).thenReturn(Optional.empty());
    }

    private static ResourceVersionRowData row(long seq, int number, long size, ResourceVersionStatus status) {
        return new ResourceVersionRowData(seq, USER, USER, RESOURCE, "key/" + seq, number, size, status, T, null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ResourceVersionErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("deleteVersion — 잠근 목록에서 SUCCESS 대상을 골라 삭제(FindService 호출 없음), 결과·사용량은 그 행 값")
    void deleteVersion_picksFromLockedRows() {
        when(repository.lockAllVersionsByResource(USER, RESOURCE))
                .thenReturn(List.of(row(10, 1, 100, ResourceVersionStatus.SUCCESS), row(11, 2, 200, ResourceVersionStatus.DELETE), row(12, 3, 300, ResourceVersionStatus.SUCCESS)));

        var result = service.deleteVersion(RESOURCE, 12, USER);

        assertThat(result.resourceSaveName()).isEqualTo("key/12");
        assertThat(result.sizeByte()).isEqualTo(300L);
        verify(userService).decreaseUseQuotaByte(300L, USER);
        verify(converter).toResourceVersionDecreaseNumberQuery(RESOURCE, USER, ResourceVersionStatus.SUCCESS, 3);
        verifyNoInteractions(findService);
    }

    @Test
    @DisplayName("deleteVersion — 대상이 SUCCESS 가 아니거나 없으면 NOT_FOUND(구 findResourceVersion 과 같음), 삭제·사용량 변경 없음")
    void deleteVersion_notFound() {
        when(repository.lockAllVersionsByResource(USER, RESOURCE)).thenReturn(List.of(row(11, 2, 200, ResourceVersionStatus.DELETE)));

        assertCode(() -> service.deleteVersion(RESOURCE, 11, USER), ResourceVersionErrorCode.NOT_FOUND);
        assertCode(() -> service.deleteVersion(RESOURCE, 99, USER), ResourceVersionErrorCode.NOT_FOUND);
        verify(repository, never()).delete(any());
        verify(userService, never()).decreaseUseQuotaByte(anyLong(), anyInt());
    }

    @Test
    @DisplayName("updateStatus — 고칠 행 로드 + changeStatus(변경 감지), 없으면 UPDATE_FAIL")
    void updateStatus_changeDetection() {
        ResourceVersion managed = ResourceVersion.builder().resourceVersionSeq(7L).userSeq(USER).status(ResourceVersionStatus.PENDING).build();
        when(repository.findByUserSeqAndResourceVersionSeq(USER, 7L)).thenReturn(managed);

        service.updateStatus(7L, USER, ResourceVersionStatus.SUCCESS);
        assertThat(managed.getStatus()).isEqualTo(ResourceVersionStatus.SUCCESS);

        assertCode(() -> service.updateStatus(8L, USER, ResourceVersionStatus.SUCCESS), ResourceVersionErrorCode.UPDATE_FAIL);
        assertCode(() -> service.updateStatus(null, USER, ResourceVersionStatus.SUCCESS), ResourceVersionErrorCode.UPDATE_FAIL);
    }

    @Test
    @DisplayName("cleanVersionIfExists — 만료 PENDING 되돌림 + 만료 DELETE 복구(changeStatus), 한도 초과면 돌려주는 값만 번호 +1(원본 값·엔티티 메모리 변경 없음), 사용량 = −현재 크기 + 복구 크기")
    void cleanVersionIfExists_returnsAdjustedValue() {
        ResourceVersionRowData pending = row(20, 5, 50, ResourceVersionStatus.PENDING);
        ResourceVersionRowData pushedOut = row(21, 1, 70, ResourceVersionStatus.DELETE);
        when(repository.lockAllVersionsByResource(USER, RESOURCE)).thenReturn(List.of());
        when(repository.existsAliveVersion(any())).thenReturn(false);
        when(repository.findExpiredVersion(any())).thenAnswer(inv -> {
            ResourceVersionStatus s = ((ResourceVersionFindQuery) inv.getArgument(0)).getStatus();
            return s == ResourceVersionStatus.PENDING ? pending : pushedOut;
        });
        ResourceVersion pushedOutManaged = ResourceVersion.builder().resourceVersionSeq(21L).userSeq(USER).status(ResourceVersionStatus.DELETE).build();
        when(repository.findByUserSeqAndResourceVersionSeq(USER, 21L)).thenReturn(pushedOutManaged);

        ResourceVersionRowData result = service.cleanVersionIfExists(USER, RESOURCE, 40L, "key/current");

        assertThat(result.getResourceVersionSeq()).isEqualTo(20L);
        assertThat(result.getVersionNumber()).as("한도(5) 초과 — 반환 값만 +1").isEqualTo(6);
        assertThat(pending.getVersionNumber()).as("원본 값은 그대로").isEqualTo(5);
        assertThat(pushedOutManaged.getStatus()).as("밀려났던 버전 복구 — 변경 감지").isEqualTo(ResourceVersionStatus.SUCCESS);
        verify(userService).increaseUseQuotaByte(eq(30L), eq(USER)); // -40 + 70
    }
}

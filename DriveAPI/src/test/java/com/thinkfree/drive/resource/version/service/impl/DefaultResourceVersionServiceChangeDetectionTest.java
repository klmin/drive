package com.thinkfree.drive.resource.version.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.version.converter.ResourceVersionConverter;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.resource.version.properties.ResourceVersionProperty;
import com.thinkfree.drive.resource.version.query.ResourceVersionDeleteQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionNumberQuery;
import com.thinkfree.drive.resource.version.repository.ResourceVersionRepository;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * W8 — 버전 쓰기 흐름이 엔티티 대신 값({@link ResourceVersionRowData})을 받는지.
 * <ul>
 *   <li>deleteVersion — 잠근 행 목록에서 대상(SUCCESS)을 고른다(FindService 재조회 없음 — 이중 로드 통합), 없거나 SUCCESS 가 아니면 NOT_FOUND</li>
 * </ul>
 * DB 반영은 {@code DefaultResourceVersionRepositoryTest}.
 */
@DisplayName("DefaultResourceVersionService — 값 운반(W8)")
class DefaultResourceVersionServiceChangeDetectionTest {

    private static final int USER = 2;
    private static final long RESOURCE = 500L;
    private static final Instant T = Instant.parse("2026-10-13T00:00:00Z");

    private ResourceVersionRepository repository;
    private ResourceVersionConverter converter;
    private ResourceVersionFindService findService;
    private UserService userService;
    private DefaultResourceVersionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ResourceVersionRepository.class);
        converter = mock(ResourceVersionConverter.class);
        findService = mock(ResourceVersionFindService.class);
        userService = mock(UserService.class);
        service = new DefaultResourceVersionService(converter, new ResourceVersionProperty(5), findService, userService, repository);

        when(converter.toDeleteResourceVersionQuery(any(), any(), anyInt())).thenReturn(mock(ResourceVersionDeleteQuery.class));
        when(converter.toResourceVersionDecreaseNumberQuery(any(), anyInt(), any(), any())).thenReturn(mock(ResourceVersionNumberQuery.class));
        when(repository.delete(any())).thenReturn(1);
        when(repository.temporaryVersionNumber(any())).thenReturn(1);
        when(repository.decreaseAllVersionNumbers(any())).thenReturn(1);
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
}

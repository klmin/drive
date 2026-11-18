package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.file.write.service.FileWriteService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * FileWriteOrchestrator 단위 — 데이터 롤백은 스토리지 실패에만(5차 패스 D1). 스토리지 이후 단계(afterStorage)·후처리 이벤트 발행 실패는 결과를 유지한다.
 */
@DisplayName("FileWriteOrchestrator — 스토리지 실패만 롤백, afterStorage·이벤트 발행 실패는 저장 유지")
class FileWriteOrchestratorTest {

    private final FileWriteService handler = Mockito.mock(FileWriteService.class);
    private final ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
    private final SystemConfigFindService systemConfigFindService = Mockito.mock(SystemConfigFindService.class);
    private final UserFindService userFindService = Mockito.mock(UserFindService.class);

    private FileWriteOrchestrator orchestrator;
    private FileUploadDto dto;
    private ResourceSaveResult result;

    @BeforeEach
    void setUp() {
        given(handler.supports()).willReturn(ResourceSaveType.VERSION);
        orchestrator = new FileWriteOrchestrator(List.of(handler), publisher, systemConfigFindService, userFindService);
        dto = FileUploadDto.builder().resourceSeq(10L).userSeq(2).fileSize(30L).resourceSaveType(ResourceSaveType.VERSION).build();
        result = new ResourceSaveResult(10L, 2, null, null, null, "doc.docx", null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        given(handler.prepareMeta(dto, 2)).willReturn(result);
    }

    @Test
    @DisplayName("스토리지 실패 — rollback 후 StorageException 전파, afterStorage·이벤트 없음")
    void storageFailureRollsBack() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(handler).doStorage(dto, result);

        assertThatThrownBy(() -> orchestrator.write(dto, 2)).isInstanceOf(StorageException.class);

        verify(handler).rollback(result);
        verify(handler, never()).afterStorage(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("스토리지 실패 + 보상(rollback)도 실패 — 응답은 원인 StorageException, 보상 실패는 suppressed")
    void rollbackFailureKeepsStorageCause() {
        var storageFailure = StorageException.type(StorageErrorCode.UPLOAD_FAILED);
        var rollbackFailure = ApiRuntimeException.type(ResourceErrorCode.DELETE_FAIL);
        willThrow(storageFailure).given(handler).doStorage(dto, result);
        willThrow(rollbackFailure).given(handler).rollback(result);

        assertThatThrownBy(() -> orchestrator.write(dto, 2))
                .isSameAs(storageFailure)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(rollbackFailure));

        verify(handler, never()).afterStorage(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("afterStorage 실패 — 롤백 없이 결과 반환, 이벤트는 발행")
    void afterStorageFailureKeepsResult() {
        willThrow(ApiRuntimeException.type(ResourceErrorCode.UPDATE_FAIL)).given(handler).afterStorage(result);

        assertThat(orchestrator.write(dto, 2)).isSameAs(result);

        verify(handler, never()).rollback(any());
        verify(publisher).publishEvent(any(FileUploadedEvent.class));
    }

    @Test
    @DisplayName("이벤트 발행 실패(비동기 큐 포화 TaskRejectedException) — 롤백 없이 결과 반환")
    void publishFailureKeepsResult() {
        willThrow(new TaskRejectedException("queue full")).given(publisher).publishEvent(any(Object.class));

        assertThat(orchestrator.write(dto, 2)).isSameAs(result);

        verify(handler).afterStorage(result);
        verify(handler, never()).rollback(any());
    }

    @Test
    @DisplayName("RetryUtil.isRetryable — 용량 부족(IS_FULL)만 재시도 제외, 그 밖의 ApiRuntimeException·다른 예외는 재시도 대상")
    void retryableJudgement() {
        assertThat(RetryUtil.isRetryable(ApiRuntimeException.type(QuotaErrorCode.IS_FULL))).isFalse();
        assertThat(RetryUtil.isRetryable(ApiRuntimeException.type(ResourceErrorCode.INSERT_FAIL))).isTrue();
        assertThat(RetryUtil.isRetryable(new IllegalStateException())).isTrue();
    }
}

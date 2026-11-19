package com.thinkfree.drive.cleanup.storage.service;

import com.thinkfree.drive.cleanup.storage.constants.StorageDeletionConstants;
import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageDeletionProcessorTest {

    @Mock
    private StorageDeletionService storageDeletionService;
    @Mock
    private StorageService storageService;
    @InjectMocks
    private StorageDeletionProcessor processor;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<List<Long>> succeededCaptor = ArgumentCaptor.forClass(List.class);
    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<List<StorageDeletionFailQuery>> failCaptor = ArgumentCaptor.forClass(List.class);

    @Test
    @DisplayName("처리할 행이 없으면 스토리지를 부르지 않는다")
    void noRows() {
        given(storageDeletionService.findDue(any(), anyInt(), anyInt())).willReturn(List.of());

        assertThat(processor.processDue()).isZero();

        verify(storageService, never()).deleteObjectsReportingFailures(any());
        verify(storageDeletionService, never()).applyResult(any(), any());
    }

    @Test
    @DisplayName("성공 키의 행은 지우고, 실패 키의 행은 사유·백오프로 갱신한다 — 같은 키 두 행은 스토리지에 한 번만 보낸다")
    void successAndFailure() {
        var rows = List.of(
                new StorageDeletionData(1L, "1/10", 0),
                new StorageDeletionData(2L, "1/11", 3),
                new StorageDeletionData(3L, "1/10", 0));
        given(storageDeletionService.findDue(any(), eq(StorageDeletionConstants.MAX_ATTEMPT_COUNT), eq(StorageDeletionConstants.BATCH_SIZE))).willReturn(rows);
        given(storageService.deleteObjectsReportingFailures(List.of("1/10", "1/11")))
                .willReturn(new StorageDeleteObjectsResult(Map.of("1/11", "AccessDenied: denied")));

        Instant before = Instant.now();
        assertThat(processor.processDue()).isEqualTo(2);

        verify(storageDeletionService).applyResult(succeededCaptor.capture(), failCaptor.capture());
        assertThat(succeededCaptor.getValue()).containsExactly(1L, 3L);
        assertThat(failCaptor.getValue()).singleElement().satisfies(q -> {
            assertThat(q.storageDeletionSeq()).isEqualTo(2L);
            assertThat(q.lastError()).isEqualTo("AccessDenied: denied");
            assertThat(q.nextAttemptDate()).isBetween(before.plus(8, ChronoUnit.MINUTES), Instant.now().plus(8, ChronoUnit.MINUTES));
        });
    }

    @Test
    @DisplayName("묶음이 가득 차면 다음 묶음을 더 꺼내고, 덜 차면 멈춘다")
    void loopsWhileBatchIsFull() {
        var full = IntStream.range(0, StorageDeletionConstants.BATCH_SIZE)
                .mapToObj(i -> new StorageDeletionData((long) i, "1/" + i, 0)).toList();
        var rest = List.of(new StorageDeletionData(5000L, "1/5000", 0));
        given(storageDeletionService.findDue(any(), anyInt(), anyInt())).willReturn(full, rest);
        given(storageService.deleteObjectsReportingFailures(any())).willReturn(new StorageDeleteObjectsResult(Map.of()));

        assertThat(processor.processDue()).isEqualTo(StorageDeletionConstants.BATCH_SIZE + 1);

        verify(storageDeletionService, times(2)).findDue(any(), anyInt(), anyInt());
        verify(storageDeletionService, times(2)).applyResult(any(), any());
    }

    @Test
    @DisplayName("묶음 전체 실패 — 모든 행이 실패로 반영되고(삭제 없음) 다음 묶음으로 넘어가지 않는다")
    void wholeBatchFailure() {
        var rows = List.of(new StorageDeletionData(1L, "1/10", 0), new StorageDeletionData(2L, "1/11", 0));
        given(storageDeletionService.findDue(any(), anyInt(), anyInt())).willReturn(rows);
        given(storageService.deleteObjectsReportingFailures(any()))
                .willReturn(new StorageDeleteObjectsResult(Map.of("1/10", "SdkClientException: timeout", "1/11", "SdkClientException: timeout")));

        assertThat(processor.processDue()).isZero();

        verify(storageDeletionService).applyResult(succeededCaptor.capture(), failCaptor.capture());
        assertThat(succeededCaptor.getValue()).isEmpty();
        assertThat(failCaptor.getValue()).extracting(StorageDeletionFailQuery::storageDeletionSeq).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("아직 리소스·버전이 참조하는 key 는 스토리지에 보내지 않고 REFERENCED 실패로 남긴다")
    void referencedKeyIsNotDeleted() {
        var rows = List.of(new StorageDeletionData(1L, "1/live", 0), new StorageDeletionData(2L, "1/orphan", 0));
        given(storageDeletionService.findDue(any(), anyInt(), anyInt())).willReturn(rows);
        given(storageDeletionService.findReferencedKeys(any())).willReturn(java.util.Set.of("1/live"));
        given(storageService.deleteObjectsReportingFailures(List.of("1/orphan"))).willReturn(new StorageDeleteObjectsResult(Map.of()));

        assertThat(processor.processDue()).isEqualTo(1);

        verify(storageDeletionService).applyResult(succeededCaptor.capture(), failCaptor.capture());
        assertThat(succeededCaptor.getValue()).containsExactly(2L);
        assertThat(failCaptor.getValue()).singleElement().satisfies(q -> {
            assertThat(q.storageDeletionSeq()).isEqualTo(1L);
            assertThat(q.lastError()).isEqualTo(StorageDeletionConstants.REFERENCED_ERROR);
        });
    }

    @Test
    @DisplayName("묶음의 key 가 모두 참조 중이면 스토리지를 부르지 않는다")
    void allReferenced() {
        given(storageDeletionService.findDue(any(), anyInt(), anyInt())).willReturn(List.of(new StorageDeletionData(1L, "1/live", 0)));
        given(storageDeletionService.findReferencedKeys(any())).willReturn(java.util.Set.of("1/live"));

        assertThat(processor.processDue()).isZero();

        verify(storageService, never()).deleteObjectsReportingFailures(any());
    }

    @Test
    @DisplayName("ABORT 행은 DELETE 묶음과 따로 행마다 abort — 성공은 지우고 실패는 백오프, 같은 key 의 DELETE 행과 섞이지 않는다")
    void abortRowsProcessedSeparately() {
        given(storageDeletionService.findDue(any(), anyInt(), anyInt())).willReturn(List.of(new StorageDeletionData(1L, "1/a", 0)));
        given(storageService.deleteObjectsReportingFailures(List.of("1/a"))).willReturn(new StorageDeleteObjectsResult(Map.of()));
        given(storageDeletionService.findDueAborts(any(), eq(StorageDeletionConstants.MAX_ATTEMPT_COUNT), eq(StorageDeletionConstants.ABORT_BATCH_SIZE)))
                .willReturn(List.of(new StorageDeletionData(10L, "1/a", 0, "upload-ok"), new StorageDeletionData(11L, "1/b", 2, "upload-fail")));
        // lenient — 다른 인자("1/a")로 부르는 호출이 strict stubs 의 PotentialStubbingProblem(런타임 예외)으로 잡혀 실패로 세지지 않게
        org.mockito.Mockito.lenient().doThrow(new RuntimeException("boom")).when(storageService).abortMultipartUpload("1/b", "upload-fail");

        assertThat(processor.processDue()).isEqualTo(2);

        verify(storageService).abortMultipartUpload("1/a", "upload-ok");
        verify(storageDeletionService, times(2)).applyResult(succeededCaptor.capture(), failCaptor.capture());
        assertThat(succeededCaptor.getAllValues().get(0)).containsExactly(1L);
        assertThat(succeededCaptor.getAllValues().get(1)).containsExactly(10L);
        assertThat(failCaptor.getAllValues().get(1)).singleElement().satisfies(q -> {
            assertThat(q.storageDeletionSeq()).isEqualTo(11L);
            assertThat(q.lastError()).contains("boom");
        });
    }

    @Test
    @DisplayName("백오프 = 2^(이전 실패 횟수) 분, 상한 60분")
    void backoff() {
        assertThat(StorageDeletionProcessor.backoffMinutes(0)).isEqualTo(1);
        assertThat(StorageDeletionProcessor.backoffMinutes(3)).isEqualTo(8);
        assertThat(StorageDeletionProcessor.backoffMinutes(5)).isEqualTo(32);
        assertThat(StorageDeletionProcessor.backoffMinutes(6)).isEqualTo(60);
        assertThat(StorageDeletionProcessor.backoffMinutes(40)).isEqualTo(60);
        assertThat(StorageDeletionProcessor.backoffMinutes(63)).as("시프트 칸 수가 63·64 여도 음수·1분으로 되돌아가지 않음").isEqualTo(60);
        assertThat(StorageDeletionProcessor.backoffMinutes(64)).isEqualTo(60);
    }
}

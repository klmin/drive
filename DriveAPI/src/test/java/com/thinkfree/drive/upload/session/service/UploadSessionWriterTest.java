package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.result.StoragePart;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSaveType;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.service.UploadSessionTxService.CleanupPhase;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 서버 내부 쓰기 — 단계 순서·실패 시 기대 상태와 정리 방식·커밋 재시도·커밋 뒤 예외 복구. */
@DisplayName("UploadSessionWriter — 세션 → 저장 → 커밋, 실패 분기")
class UploadSessionWriterTest {

    private static final int USER = 7;
    private static final int TENANT = 3;
    private static final long MIB = 1024L * 1024;
    private static final UUID ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final String KEY = TENANT + "/" + ID;

    private final UploadSessionTxService tx = Mockito.mock(UploadSessionTxService.class);
    private final UploadCommitService commit = Mockito.mock(UploadCommitService.class);
    private final StorageService storage = Mockito.mock(StorageService.class);
    private final UserFindService userFindService = Mockito.mock(UserFindService.class);
    private final QuotaService quotaService = Mockito.mock(QuotaService.class);
    private final UploadSessionProperty property = new UploadSessionProperty(Duration.ofHours(1), Duration.ofMinutes(30), Duration.ofDays(7),
            Duration.ofHours(24), DataSize.ofMegabytes(16), DataSize.ofMegabytes(32), DataSize.ofTerabytes(5), 4, 50, 500, true, 1);
    private final UploadSessionWriter writer = new UploadSessionWriter(tx, commit, storage, userFindService, quotaService, property);

    private final ResourceSaveResult saved = new ResourceSaveResult(100L, USER, null, null, null, "a.docx", KEY, null, null, null,
            null, null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        given(userFindService.findTenantSeqByUserSeq(USER)).willReturn(TENANT);
        given(tx.create(any())).willReturn(ID);
    }

    private static FileUploadDto streamDto(long size) {
        return FileUploadDto.builder().userSeq(USER).fileSize(size).contentType("application/octet-stream").originalFileName("a.docx")
                .resourceSaveType(ResourceSaveType.DUPLICATE)
                .contentSource(new FileContentSource.Stream(new FileData("application/octet-stream", new ByteArrayInputStream(new byte[8]), size)))
                .build();
    }

    @Test
    @DisplayName("key 는 {tenantSeq}/{세션 ID}, 테넌트가 없으면 0/{세션 ID}")
    void storageKey() {
        assertThat(UploadSessionWriter.storageKey(3, ID)).isEqualTo(KEY);
        assertThat(UploadSessionWriter.storageKey(null, ID)).isEqualTo("0/" + ID);
    }

    @Test
    @DisplayName("32MB 미만 — key 기록 → 단일 PUT → COMPLETING → 크기 확인 → 커밋")
    void singlePut() {
        given(storage.headContentLength(KEY)).willReturn(10L);
        given(commit.commitDuplicate(eq(ID), eq(KEY), any(), eq(USER))).willReturn(saved);

        assertThat(writer.writeDuplicate(streamDto(10), USER)).isSameAs(saved);

        var order = inOrder(tx, storage, commit);
        order.verify(tx).create(Mockito.argThat(q -> q.saveType() == UploadSaveType.DUPLICATE && q.sizeByte() == 10 && q.tenantSeq() == TENANT));
        order.verify(tx).attachStorage(ID, KEY, null);
        order.verify(storage).putObject(eq(KEY), anyString(), any(), eq(10L));
        order.verify(tx).markCompleting(ID, USER);
        order.verify(storage).headContentLength(KEY);
        order.verify(commit).commitDuplicate(eq(ID), eq(KEY), any(), eq(USER));
        verify(storage, never()).createMultipartUpload(anyString(), anyString());
    }

    @Test
    @DisplayName("32MB 이상 — 멀티파트(파트 16MB), uploadId 기록 뒤 파트 업로드, COMPLETING 뒤 조립")
    void multipart() {
        long size = 40 * MIB;
        List<StoragePart> parts = List.of(new StoragePart(1, "e1", 16 * MIB), new StoragePart(2, "e2", 16 * MIB), new StoragePart(3, "e3", 8 * MIB));
        given(storage.createMultipartUpload(eq(KEY), anyString())).willReturn("up-1");
        given(storage.uploadPartsFromStream(eq(KEY), eq("up-1"), any(), eq(size), eq(16 * MIB))).willReturn(parts);
        given(storage.headContentLength(KEY)).willReturn(size);
        given(commit.commitDuplicate(any(), any(), any(), anyInt())).willReturn(saved);

        writer.writeDuplicate(streamDto(size), USER);

        var order = inOrder(tx, storage);
        order.verify(tx).attachStorage(ID, KEY, "up-1");
        order.verify(storage).uploadPartsFromStream(eq(KEY), eq("up-1"), any(), eq(size), eq(16 * MIB));
        order.verify(tx).markCompleting(ID, USER);
        order.verify(storage).completeMultipartUpload(KEY, "up-1", parts);
        verify(storage, never()).putObject(anyString(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadId 를 세션에 못 남기면 — 받은 uploadId 로 ABORT 를 직접 등록(failWithAbort), 일반 실패 처리는 하지 않는다")
    void attachFailureRegistersAbortDirectly() {
        given(storage.createMultipartUpload(eq(KEY), anyString())).willReturn("up-1");
        willThrow(new IllegalStateException("db down")).given(tx).attachStorage(ID, KEY, "up-1");

        assertThatThrownBy(() -> writer.writeDuplicate(streamDto(40 * MIB), USER)).hasMessage("db down");

        verify(tx).failWithAbort(eq(ID), eq(KEY), eq("up-1"), eq(UploadSessionFailReason.STORAGE_ERROR), anyString());
        verify(tx, never()).fail(any(), any(), any(), any(), any());
        verify(storage, never()).uploadPartsFromStream(anyString(), anyString(), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("단일 PUT 실패 — PENDING 에서 FAILED(STORAGE_ERROR), 조립 전 정리(단일 요청이면 DELETE)")
    void putFailure() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storage).putObject(anyString(), anyString(), any(), anyLong());
        given(tx.fail(any(), any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> writer.writeDuplicate(streamDto(10), USER)).isInstanceOf(StorageException.class);

        verify(tx).fail(eq(ID), eq(UploadSessionStatus.PENDING), eq(UploadSessionFailReason.STORAGE_ERROR), anyString(), eq(CleanupPhase.BEFORE_ASSEMBLE));
        verify(commit, never()).commitDuplicate(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("조립(complete) 실패 — 결과 불명확: COMPLETING 에서 FAILED, ABORT + DELETE")
    void completeFailureIsAmbiguous() {
        given(storage.createMultipartUpload(eq(KEY), anyString())).willReturn("up-1");
        given(storage.uploadPartsFromStream(any(), any(), any(), anyLong(), anyLong())).willReturn(List.of(new StoragePart(1, "e", 1)));
        willThrow(StorageException.type(StorageErrorCode.MULTIPART_FAILED)).given(storage).completeMultipartUpload(any(), any(), any());
        given(tx.fail(any(), any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> writer.writeDuplicate(streamDto(40 * MIB), USER)).isInstanceOf(StorageException.class);

        verify(tx).fail(eq(ID), eq(UploadSessionStatus.COMPLETING), eq(UploadSessionFailReason.COMMIT_ERROR), anyString(), eq(CleanupPhase.AMBIGUOUS));
    }

    @Test
    @DisplayName("커밋 tx 의 일시적 DB 오류(교착 등)는 커밋만 재시도 — 스토리지는 다시 부르지 않는다")
    void commitRetriedOnTransientError() {
        given(storage.headContentLength(KEY)).willReturn(10L);
        given(commit.commitDuplicate(any(), any(), any(), anyInt()))
                .willThrow(new CannotAcquireLockException("deadlock"))
                .willThrow(new CannotAcquireLockException("deadlock"))
                .willReturn(saved);

        assertThat(writer.writeDuplicate(streamDto(10), USER)).isSameAs(saved);

        verify(commit, times(3)).commitDuplicate(any(), any(), any(), anyInt());
        verify(storage, times(1)).putObject(anyString(), anyString(), any(), anyLong());
        verify(tx, never()).fail(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("커밋 재시도 소진 — COMPLETING 에서 FAILED, 조립 뒤 정리(DELETE)")
    void commitRetriesExhausted() {
        given(storage.headContentLength(KEY)).willReturn(10L);
        given(commit.commitDuplicate(any(), any(), any(), anyInt())).willThrow(new CannotAcquireLockException("deadlock"));
        given(tx.fail(any(), any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> writer.writeDuplicate(streamDto(10), USER)).isInstanceOf(CannotAcquireLockException.class);

        verify(commit, times(UploadSessionWriter.COMMIT_ATTEMPTS)).commitDuplicate(any(), any(), any(), anyInt());
        verify(tx).fail(eq(ID), eq(UploadSessionStatus.COMPLETING), eq(UploadSessionFailReason.COMMIT_ERROR), anyString(), eq(CleanupPhase.AFTER_ASSEMBLE));
    }

    @Test
    @DisplayName("커밋 뒤 예외(응답 유실) — FAILED 전환이 0행이고 세션이 COMPLETED 면 저장된 결과를 돌려준다(DB 상태가 정본)")
    void commitSucceededDespiteError() {
        given(storage.headContentLength(KEY)).willReturn(10L);
        given(commit.commitDuplicate(any(), any(), any(), anyInt())).willThrow(new IllegalStateException("connection reset after commit"));
        given(tx.fail(any(), any(), any(), any(), any())).willReturn(false);
        given(tx.find(ID, USER)).willReturn(Optional.of(new UploadSessionData(ID, TENANT, USER, USER, UploadSaveType.DUPLICATE, null, null, null, null,
                "a.docx", "application/octet-stream", 10, KEY, null, null, null, UploadSessionStatus.COMPLETED, null, 100L, "a (1).docx", null, null, null)));

        ResourceSaveResult result = writer.writeDuplicate(streamDto(10), USER);

        assertThat(result.resourceSeq()).isEqualTo(100L);
        assertThat(result.resourceName()).isEqualTo("a (1).docx");
    }

    @Test
    @DisplayName("커밋 시 용량 초과 — FAILED(QUOTA_EXCEEDED) + DELETE, 용량 초과 알림, IS_FULL 전파")
    void commitQuotaExceeded() {
        given(storage.headContentLength(KEY)).willReturn(10L);
        given(commit.commitDuplicate(any(), any(), any(), anyInt())).willThrow(ApiRuntimeException.type(QuotaErrorCode.IS_FULL));
        given(tx.fail(any(), any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> writer.writeDuplicate(streamDto(10), USER))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(tx).fail(eq(ID), eq(UploadSessionStatus.COMPLETING), eq(UploadSessionFailReason.QUOTA_EXCEEDED), anyString(), eq(CleanupPhase.AFTER_ASSEMBLE));
        verify(quotaService).notifyQuotaExceeded(USER, USER);
    }

    @Test
    @DisplayName("실패 기록조차 실패 — 원래 예외를 던지고(실패 기록 예외는 suppressed) 세션은 스케줄러가 정리")
    void failRecordingFailure() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storage).putObject(anyString(), anyString(), any(), anyLong());
        given(tx.fail(any(), any(), any(), any(), any())).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> writer.writeDuplicate(streamDto(10), USER))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    @DisplayName("스트림 길이가 신고 크기와 다르면 스토리지를 부르지 않고 VALIDATION 으로 끝낸다")
    void declaredSizeMismatch() {
        var dto = streamDto(10).toBuilder()
                .contentSource(new FileContentSource.Stream(new FileData("text/plain", new ByteArrayInputStream(new byte[3]), 3))).build();
        given(tx.fail(any(), any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> writer.writeDuplicate(dto, USER)).isInstanceOf(ApiRuntimeException.class);

        verify(storage, never()).putObject(anyString(), anyString(), any(), anyLong());
        verify(tx).fail(eq(ID), eq(UploadSessionStatus.PENDING), eq(UploadSessionFailReason.VALIDATION), anyString(), eq(CleanupPhase.BEFORE_ASSEMBLE));
    }
}

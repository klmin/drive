package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.result.StoragePart;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.upload.session.enums.UploadSaveType;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import com.thinkfree.drive.upload.session.model.UploadPartPlan;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.query.UploadSessionCreateQuery;
import com.thinkfree.drive.upload.session.service.UploadSessionTxService.CleanupPhase;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 서버 내부 쓰기 — 길이를 아는 내용 하나를 세션 → 저장 → 커밋으로 반영한다(docs/design/upload-session-redesign.md 규칙 16, §6.4).
 * 외부 인터페이스(업로드 API·MCP·복사·템플릿)는 지금처럼 내용을 한 번에 받고, 내부만 청크 API 와 같은 세션·커밋 로직을 탄다.
 * <ol>
 *   <li>세션 생성(사용자 행 잠금·용량 사전 필터·INSERT) — 짧은 tx</li>
 *   <li>스토리지(tx 밖): 32MB 미만 단일 PUT / 이상은 16MB 이상 파트로 멀티파트 / 스토리지 객체는 서버측 복사. key = {tenantSeq}/{세션 ID}</li>
 *   <li>COMPLETING 선점 → (멀티파트면) 조립 → 크기 확인</li>
 *   <li>커밋 tx — 일시적 DB 오류(교착 등)는 커밋 tx 만 재시도(객체는 이미 있다)</li>
 * </ol>
 * 실패하면 상태를 끝내는 쪽(여기)이 같은 tx 에서 정리를 등록한다 — 여기서 S3 abort·delete 를 직접 부르지 않는다(규칙 5).
 * 이 클래스는 트랜잭션을 열지 않는다. 호출자 트랜잭션 안에서 부르면 스토리지 호출이 그 트랜잭션 안에 들어가므로 경고한다(규칙 7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadSessionWriter {

    /** 커밋 tx 재시도 횟수(첫 시도 포함) */
    static final int COMMIT_ATTEMPTS = 3;
    /** 테넌트 없는 사용자의 key 접두 */
    static final String NO_TENANT_PREFIX = "0";

    private final UploadSessionTxService uploadSessionTxService;
    private final UploadCommitService uploadCommitService;
    private final StorageService storageService;
    private final UserFindService userFindService;
    private final QuotaService quotaService;
    private final UploadSessionProperty property;

    /** 세션 key — 계산은 여기 한 곳(세션 생성 시)뿐이고, 이후 조회·삭제는 DB 에 저장된 값으로만 한다(규칙 1). */
    static String storageKey(Integer tenantSeq, UUID uploadSessionId) {
        return (tenantSeq == null ? NO_TENANT_PREFIX : tenantSeq.toString()) + "/" + uploadSessionId;
    }

    /** DUPLICATE(신규) 쓰기. 내용 출처는 dto 의 contentSource(없으면 업로드 파일). */
    public ResourceSaveResult writeDuplicate(FileUploadDto dto, int requestUserSeq) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("[UploadSessionWriter] called inside a transaction — storage calls will run while it is open. userSeq: {}", dto.getUserSeq());
        }
        int userSeq = dto.getUserSeq();
        long size = dto.getFileSize();
        Integer tenantSeq = userFindService.findTenantSeqByUserSeq(userSeq);

        UUID id;
        try {
            id = uploadSessionTxService.create(new UploadSessionCreateQuery(tenantSeq, userSeq, requestUserSeq, UploadSaveType.DUPLICATE,
                    dto.getParentFolderSeq(), null, null, null, dto.getOriginalFileName(), dto.getContentType(), size, null, null));
        } catch (ApiRuntimeException e) {
            notifyIfQuotaExceeded(e, userSeq, requestUserSeq);
            throw e;
        }
        String key = storageKey(tenantSeq, id);
        Progress progress = new Progress();

        try {
            store(id, key, dto, progress);
            uploadSessionTxService.markCompleting(id, requestUserSeq);
            progress.status = UploadSessionStatus.COMPLETING;
            if (progress.parts != null) {
                progress.phase = CleanupPhase.AMBIGUOUS;
                storageService.completeMultipartUpload(key, progress.uploadId, progress.parts);
            }
            progress.phase = CleanupPhase.AFTER_ASSEMBLE;
            long stored = storageService.headContentLength(key);
            if (stored != size) {
                throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SIZE_MISMATCH).with("expected", size).with("stored", stored);
            }
            return commitWithRetry(id, key, dto, requestUserSeq);
        } catch (RuntimeException e) {
            ResourceSaveResult committed = failOrRecover(id, userSeq, requestUserSeq, progress, e);
            if (committed != null) {
                return committed;
            }
            notifyIfQuotaExceeded(e, userSeq, requestUserSeq);
            throw e;
        }
    }

    /** 진행 위치 — 실패 시 기대 상태·정리 방식을 정한다. */
    private static final class Progress {
        UploadSessionStatus status = UploadSessionStatus.PENDING;
        CleanupPhase phase = CleanupPhase.BEFORE_ASSEMBLE;
        String uploadId;
        List<StoragePart> parts;
    }

    private void store(UUID id, String key, FileUploadDto dto, Progress progress) {
        long size = dto.getFileSize();
        FileContentSource source = dto.resolveContentSource();
        if (source instanceof FileContentSource.StorageCopy copy) {
            uploadSessionTxService.attachStorage(id, key, null);
            storageService.copyObjectAnySize(copy.sourceKey(), key);
            return;
        }
        try (InputStream in = open(source, size)) {
            if (size < property.singleRequestThreshold().toBytes()) {
                uploadSessionTxService.attachStorage(id, key, null);
                storageService.putObject(key, dto.getContentType(), in, size);
                return;
            }
            String uploadId = storageService.createMultipartUpload(key, dto.getContentType());
            try {
                uploadSessionTxService.attachStorage(id, key, uploadId);
            } catch (RuntimeException e) {
                // uploadId 가 세션에 없으므로 일반 실패 처리로는 abort 가 등록되지 않는다 — 여기서 직접 등록하고 세션도 끝낸다
                uploadSessionTxService.failWithAbort(id, key, uploadId, UploadSessionFailReason.STORAGE_ERROR, describe(e));
                progress.status = UploadSessionStatus.FAILED;
                throw e;
            }
            progress.uploadId = uploadId;
            long partSize = UploadPartPlan.of(size, property.minPartSize().toBytes()).partSize();
            progress.parts = storageService.uploadPartsFromStream(key, uploadId, in, size, partSize);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read upload content", e);
        }
    }

    private static InputStream open(FileContentSource source, long declaredSize) throws IOException {
        if (source instanceof FileContentSource.Upload upload) {
            MultipartFile file = upload.file();
            if (file.getSize() != declaredSize) {
                throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SIZE_MISMATCH).with("expected", declaredSize).with("received", file.getSize());
            }
            return file.getInputStream();
        }
        if (source instanceof FileContentSource.Stream stream) {
            FileData data = stream.data();
            if (data.contentLength() != declaredSize) {
                throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SIZE_MISMATCH).with("expected", declaredSize).with("received", data.contentLength());
            }
            return data.content();
        }
        throw new IllegalStateException("Unsupported content source: " + source);
    }

    private ResourceSaveResult commitWithRetry(UUID id, String key, FileUploadDto dto, int requestUserSeq) {
        for (int attempt = 1; ; attempt++) {
            try {
                return uploadCommitService.commitDuplicate(id, key, dto, requestUserSeq);
            } catch (TransientDataAccessException e) {
                // 교착(40P01)·잠금 대기 실패 등 — 커밋 tx 는 통째로 롤백됐으므로 다시 해도 안전
                if (attempt >= COMMIT_ATTEMPTS) {
                    throw e;
                }
                log.warn("[UploadSessionWriter commit retry] id: {}, attempt: {}, error: {}", id, attempt, e.getMessage());
            }
        }
    }

    /**
     * 실패 처리 — 기대 상태에서 FAILED 로 바꾸고 정리 등록. 조건부 UPDATE 가 0행이면 커밋이 실제로는 성공한 것(커밋 뒤 예외·응답 유실)일 수 있어
     * 세션을 다시 읽어 COMPLETED 면 그 결과를 돌려준다(DB 상태가 정본, §6.3).
     *
     * @return 실제로 커밋됐으면 그 결과, 아니면 null
     */
    private ResourceSaveResult failOrRecover(UUID id, int userSeq, int requestUserSeq, Progress progress, RuntimeException cause) {
        if (progress.status == UploadSessionStatus.FAILED) {
            return null;
        }
        try {
            boolean failed = uploadSessionTxService.fail(id, progress.status, reasonOf(cause, progress), describe(cause), progress.phase);
            if (failed) {
                return null;
            }
            var session = uploadSessionTxService.find(id, requestUserSeq).orElse(null);
            if (session != null && session.status() == UploadSessionStatus.COMPLETED) {
                log.warn("[UploadSessionWriter] commit succeeded despite error — returning stored result. id: {}", id, cause);
                return new ResourceSaveResult(session.resultResourceSeq(), userSeq, null, null, null, session.resultName(), session.storageKey(),
                        null, null, null, session.sizeByte(), session.sizeByte(), Instant.now(), null, null, null, null, null, null);
            }
        } catch (RuntimeException failError) {
            // 실패 기록조차 못 함 — 세션은 PENDING/COMPLETING 으로 남고 스케줄러가 TTL·30분 뒤 FAILED + 정리 등록
            cause.addSuppressed(failError);
            log.error("[UploadSessionWriter] could not mark session failed — left for scheduler. id: {}", id, failError);
        }
        return null;
    }

    private static UploadSessionFailReason reasonOf(RuntimeException e, Progress progress) {
        if (e instanceof ApiRuntimeException api) {
            return api.getCode() == QuotaErrorCode.IS_FULL ? UploadSessionFailReason.QUOTA_EXCEEDED : UploadSessionFailReason.VALIDATION;
        }
        if (e instanceof StorageException) {
            return progress.status == UploadSessionStatus.PENDING ? UploadSessionFailReason.STORAGE_ERROR : UploadSessionFailReason.COMMIT_ERROR;
        }
        return UploadSessionFailReason.COMMIT_ERROR;
    }

    private void notifyIfQuotaExceeded(RuntimeException e, int userSeq, int requestUserSeq) {
        if (e instanceof ApiRuntimeException api && api.getCode() == QuotaErrorCode.IS_FULL) {
            try {
                quotaService.notifyQuotaExceeded(userSeq, requestUserSeq);
            } catch (RuntimeException notifyError) {
                log.error("[UploadSessionWriter] quota exceeded notification failed. userSeq: {}", userSeq, notifyError);
            }
        }
    }

    private static String describe(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}

package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.storage.constants.StorageConstants;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.result.StoragePart;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSaveType;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import com.thinkfree.drive.upload.session.model.UploadPartPlan;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.query.UploadSessionCreateQuery;
import com.thinkfree.drive.upload.session.repository.UploadLockRepository;
import com.thinkfree.drive.upload.session.service.UploadSessionTxService.CleanupPhase;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.MediaType;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
    /** 확장자 → MIME 사전 조회만(내용 감지 아님) — 파사드는 스레드 세이프 */
    private static final Tika CONTENT_TYPES = new Tika();
    /** 테넌트 없는 사용자의 key 접두 */
    static final String NO_TENANT_PREFIX = "0";

    private final UploadSessionTxService uploadSessionTxService;
    private final UploadCommitService uploadCommitService;
    private final StorageService storageService;
    private final UserFindService userFindService;
    private final QuotaService quotaService;
    private final UploadLockRepository uploadLockRepository;
    private final ResourceLockUnlockService resourceLockUnlockService;
    private final UploadSessionProperty property;
    private final UploadContentInspector uploadContentInspector;

    /** 세션 key — 계산은 여기 한 곳(세션 생성 시)뿐이고, 이후 조회·삭제는 DB 에 저장된 값으로만 한다(규칙 1). */
    static String storageKey(Integer tenantSeq, UUID uploadSessionId) {
        return (tenantSeq == null ? NO_TENANT_PREFIX : tenantSeq.toString()) + "/" + uploadSessionId;
    }


    /**
     * 저장 유형별 쓰기 — DUPLICATE(신규) / VERSION(현재 내용을 버전으로) / RESTORE(버전 객체를 새 key 로 복사한 VERSION) / OVERWRITE(현재 내용 교체).
     * 내용 출처는 dto 의 contentSource(없으면 업로드 파일). 저장 유형은 dto 의 resourceSaveType, 출처가 스토리지 객체인 VERSION 은 RESTORE.
     */
    public ResourceSaveResult write(FileUploadDto requested, int requestUserSeq) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("[UploadSessionWriter] called inside a transaction — storage calls will run while it is open. userSeq: {}", requested.getUserSeq());
        }
        OpenedSession session = open(requested, requestUserSeq, null, null);
        Progress progress = new Progress();
        try {
            store(session.id(), session.key(), session.dto(), progress);
            uploadSessionTxService.markCompleting(session.id(), requestUserSeq);
        } catch (RuntimeException e) {
            return recoverOrThrow(session, requestUserSeq, progress, e);
        }
        List<StoragePart> parts = progress.parts;
        // 서버 내부 쓰기 — 권한은 호출부(aspect·툴)가 직전에 확인했다
        return finishClaimed(session, requestUserSeq, progress.uploadId, () -> parts, null);
    }

    /**
     * 세션을 연 결과 — 저장·완료 단계가 쓰는 값. dto 의 Content-Type 은 서버가 정한 값으로 바뀌어 있다.
     *
     * @param key            세션 key({tenantSeq}/{id}) — 스토리지 준비 때 세션에 기록한다
     * @param baseStorageKey OVERWRITE 충돌 감지 기준(그 외 null)
     */
    public record OpenedSession(UUID id, String key, UploadSaveType saveType, String baseStorageKey, FileUploadDto dto) {
    }

    /**
     * §6.1 ① — Content-Type 결정·덮어쓰기 기준 key·복원 잠김 사전 확인 → 세션 INSERT(PENDING). 스토리지는 아직 준비하지 않는다.
     * 내부 쓰기와 청크 업로드 API(세션 생성)가 함께 쓴다. 권한·크기 정책은 호출자가 먼저 확인한다.
     *
     * @param partSize  파트 크기(청크 API 세션만, 그 외 null)
     * @param partCount 파트 수(청크 API 세션만, 그 외 null)
     * @throws ApiRuntimeException 대상 없음 NOT_FOUND, 잠김 RESOURCE_LOCKED, 용량 부족 IS_FULL(알림 발송), 세션 수 초과 UPLOAD_SESSION_LIMIT
     */
    public OpenedSession open(FileUploadDto requested, int requestUserSeq, Long partSize, Integer partCount) {
        UploadSaveType saveType = saveTypeOf(requested);
        // Content-Type 은 클라이언트 값을 버리고 서버가 확장자로 정한다(규칙 3) — 세션·스토리지·리소스 행에 같은 값
        FileUploadDto dto = requested.toBuilder()
                .contentType(resolveContentType(effectiveName(saveType, requested)))
                .build();
        int userSeq = dto.getUserSeq();
        Integer tenantSeq = userFindService.findTenantSeqByUserSeq(userSeq);
        // 덮어쓰기 충돌 감지 기준 — 세션 시작 시 대상의 현재 key(불변 key 라 커밋 때 같으면 그 사이 내용이 안 바뀐 것, §4.3)
        // 진행 중 용량(reserveByte) — 덮어쓰기는 커밋 때 새 크기 − 이전 크기만 늘어나므로 그 양만(0 이상). 그 사이 다른 저장이 끼어
        // 버전 저장으로 바뀌면 커밋 tx 가 전체 크기로 다시 판정한다(사전 필터가 느슨해질 뿐 한도를 넘지 않는다)
        var base = saveType == UploadSaveType.OVERWRITE
                ? uploadLockRepository.findFileBase(userSeq, dto.getResourceSeq())
                                      .orElseThrow(() -> ApiRuntimeException.type(ResourceErrorCode.NOT_FOUND))
                : null;
        String baseStorageKey = base == null ? null : base.storageKey();
        long reserveByte = base == null ? dto.getFileSize() : Math.max(0L, dto.getFileSize() - base.sizeByte());
        // 잠긴 파일은 복원 불가 — 스토리지 복사 전에 미리 거절(최종 판정은 커밋 tx 의 행 잠금 뒤 확인)
        if (dto.isUnlockedRequired() && dto.getResourceSeq() != null) {
            resourceLockUnlockService.checkLockedResource(dto.getResourceSeq(), userSeq);
        }
        String fileName = dto.getOriginalFileName() != null ? dto.getOriginalFileName() : String.valueOf(dto.getResourceSeq());

        UUID id;
        try {
            id = uploadSessionTxService.create(new UploadSessionCreateQuery(tenantSeq, userSeq, requestUserSeq, saveType,
                    dto.getParentFolderSeq(), dto.getResourceSeq(), null, baseStorageKey, fileName, dto.getContentType(), dto.getFileSize(),
                    partSize, partCount, reserveByte));
        } catch (ApiRuntimeException e) {
            notifyIfQuotaExceeded(e, userSeq, requestUserSeq);
            throw e;
        }
        return new OpenedSession(id, storageKey(tenantSeq, id), saveType, baseStorageKey, dto);
    }

    /** complete 단계에서 조립할 파트를 정한다(COMPLETING 선점 뒤, 조립 전에 불림). null = 조립 없음(단일 PUT·CopyObject 로 이미 객체가 있다). */
    @FunctionalInterface
    public interface PartsResolver {
        List<StoragePart> resolve();
    }

    /**
     * §6.3 ②~⑦ — COMPLETING 선점이 끝난 세션을 (멀티파트면) 조립 → 저장 크기 확인 → 커밋. 실패하면 진행 위치에 맞게 FAILED + 정리 등록.
     * 내부 쓰기와 청크 업로드 API(complete)가 함께 쓴다 — 실패 처리 표(조립 전/중/후)가 한 곳에만 있도록.
     *
     * @param uploadId 멀티파트 uploadId(단일 요청이면 null)
     * @param parts    조립할 파트 — 여기서 던지는 예외는 조립 전 실패(멀티파트 ABORT / 단일 DELETE 등록)
     * @param recheckActor 커밋 tx 에서 권한을 다시 볼 요청자(규칙 14). 청크 업로드 complete 는 업로드가 길어 그 사이 공유가 해제될 수 있으므로
     *                     넘기고, 서버 내부 쓰기는 호출부가 직전에 확인했으므로 null
     */
    public ResourceSaveResult finishClaimed(OpenedSession session, int requestUserSeq, String uploadId, PartsResolver parts,
                                            RequestActor recheckActor) {
        Progress progress = new Progress();
        progress.status = UploadSessionStatus.COMPLETING;
        progress.uploadId = uploadId;
        long size = session.dto().getFileSize();
        try {
            List<StoragePart> resolved = parts.resolve();
            if (resolved != null) {
                progress.phase = CleanupPhase.AMBIGUOUS;
                storageService.completeMultipartUpload(session.key(), uploadId, resolved);
            }
            progress.phase = CleanupPhase.AFTER_ASSEMBLE;
            long stored = storageService.headContentLength(session.key());
            if (stored != size) {
                throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SIZE_MISMATCH).with("expected", size).with("stored", stored);
            }
            // 매직바이트(규칙 3) — 사용자가 올린 내용만. 복사·복원(StorageCopy)은 이미 저장소에 있던 내용이라 건너뛴다
            if (!(session.dto().resolveContentSource() instanceof FileContentSource.StorageCopy)) {
                uploadContentInspector.verify(session.key(), effectiveName(session.saveType(), session.dto()), size);
            }
            return commitWithRetry(session.saveType(), session.id(), session.key(), session.baseStorageKey(), session.dto(), requestUserSeq,
                                   recheckActor);
        } catch (RuntimeException e) {
            return recoverOrThrow(session, requestUserSeq, progress, e);
        }
    }

    /**
     * 저장될 파일의 확장자를 담은 이름 — Content-Type 결정과 매직바이트 검사가 쓴다. 신규는 업로드한 파일명,
     * 버전 저장·덮어쓰기·복원은 대상 파일의 확장자. 기존 파일 갱신은 대상의 이름·확장자를 그대로 두고 내용만 바꾸므로,
     * 업로드한 이름(photo.png)으로 보면 report.pdf 에 PNG 내용·image/png 가 들어간다. 대상 확장자를 모르면 업로드한 이름.
     */
    private String effectiveName(UploadSaveType saveType, FileUploadDto dto) {
        if (saveType == UploadSaveType.VERSION || saveType == UploadSaveType.OVERWRITE || saveType == UploadSaveType.RESTORE) {
            var ext = uploadLockRepository.findFileExt(dto.getUserSeq(), dto.getResourceSeq());
            if (ext.isPresent()) {
                return "target." + ext.get();
            }
        }
        return dto.getOriginalFileName();
    }

    /** 실패 처리 후 다시 던진다 — 단, 실제로는 커밋이 끝나 있었으면 그 결과를 돌려준다. */
    private ResourceSaveResult recoverOrThrow(OpenedSession session, int requestUserSeq, Progress progress, RuntimeException e) {
        int userSeq = session.dto().getUserSeq();
        ResourceSaveResult committed = failOrRecover(session.id(), userSeq, requestUserSeq, progress, e);
        if (committed != null) {
            return committed;
        }
        notifyIfQuotaExceeded(e, userSeq, requestUserSeq);
        throw e;
    }

    /** COMPLETED 세션에 남긴 결과 — 커밋 뒤 예외 복구·complete 재요청 멱등 응답용. */
    public static ResourceSaveResult storedResult(UploadSessionData session) {
        return new ResourceSaveResult(session.resultResourceSeq(), session.userSeq(), null, null, null, session.resultName(), session.storageKey(),
                null, null, null, session.sizeByte(), session.sizeByte(), Instant.now(), null, null, null, null, null, null);
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
            storeCopy(id, key, copy.sourceKey(), dto.getContentType(), progress);
            return;
        }
        try (InputStream in = open(source, size)) {
            if (size < property.singleRequestThreshold().toBytes()) {
                uploadSessionTxService.attachStorage(id, key, null);
                storageService.putObject(key, dto.getContentType(), in, size);
                return;
            }
            String uploadId = startMultipart(id, key, dto.getContentType(), progress);
            long partSize = UploadPartPlan.of(size, property.minPartSize().toBytes()).partSize();
            List<StoragePart> parts = new ArrayList<>();
            long offset = 0;
            for (int partNumber = 1; offset < size; partNumber++) {
                long length = Math.min(partSize, size - offset);
                parts.add(new StoragePart(partNumber, storageService.uploadPart(key, uploadId, partNumber, in, length), length));
                offset += length;
                // 파트마다 활동 시각 갱신 — 전송이 TTL 보다 길어도 스케줄러가 유휴로 보고 끝내지 않게. 이미 끝났으면 여기서 멈춘다
                uploadSessionTxService.touch(id);
            }
            progress.parts = parts;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read upload content", e);
        }
    }

    /**
     * 서버측 복사 — 5GiB 이하는 CopyObject 한 번, 넘으면 멀티파트 UploadPartCopy. 멀티파트의 uploadId 는 만든 직후 세션에 남겨
     * 중간에 프로세스가 죽어도 정리(ABORT)가 등록되게 한다.
     */
    private void storeCopy(UUID id, String key, String sourceKey, String contentType, Progress progress) {
        long sourceSize = storageService.headContentLength(sourceKey);
        if (sourceSize <= StorageConstants.MAX_SINGLE_COPY_SIZE) {
            uploadSessionTxService.attachStorage(id, key, null);
            storageService.copy(sourceKey, key);
            return;
        }
        String uploadId = startMultipart(id, key, contentType, progress);
        long partSize = Math.max(StorageConstants.DEFAULT_COPY_PART_SIZE,
                                 (sourceSize + StorageConstants.MAX_PART_COUNT - 1) / StorageConstants.MAX_PART_COUNT);
        List<StoragePart> parts = new ArrayList<>();
        long offset = 0;
        for (int partNumber = 1; offset < sourceSize; partNumber++) {
            long end = Math.min(offset + partSize, sourceSize) - 1;
            parts.add(new StoragePart(partNumber, storageService.uploadPartCopy(sourceKey, key, uploadId, partNumber, offset, end), end - offset + 1));
            offset = end + 1;
            uploadSessionTxService.touch(id);
        }
        progress.parts = parts;
    }

    /** 멀티파트 시작 + uploadId 기록. 기록에 실패하면 세션 행에 uploadId 가 없어 일반 실패 처리로는 abort 가 안 걸리므로 직접 등록하고 세션도 끝낸다. */
    private String startMultipart(UUID id, String key, String contentType, Progress progress) {
        String uploadId = storageService.createMultipartUpload(key, contentType);
        try {
            uploadSessionTxService.attachStorage(id, key, uploadId);
        } catch (RuntimeException e) {
            uploadSessionTxService.failWithAbort(id, key, uploadId, UploadSessionFailReason.STORAGE_ERROR, describe(e));
            progress.status = UploadSessionStatus.FAILED;
            throw e;
        }
        progress.uploadId = uploadId;
        return uploadId;
    }

    /**
     * 서버가 정하는 Content-Type(규칙 3) — 클라이언트가 보낸 값은 쓰지 않고 파일명 확장자로만(Tika 확장자 사전 — MCP 업로드·
     * 매직바이트 검사와 같은 사전). 모르는 확장자·확장자 없음은 application/octet-stream. 다운로드는 attachment·nosniff 라
     * 이 값으로 브라우저가 내용을 실행하지 않지만, 클라이언트가 text/html 같은 값을 붙여 저장하는 길을 닫는다.
     */
    static String resolveContentType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return CONTENT_TYPES.detect(fileName);
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

    private static UploadSaveType saveTypeOf(FileUploadDto dto) {
        ResourceSaveType type = dto.getResourceSaveType();
        if (type == null) {
            throw new IllegalArgumentException("ResourceSaveType must not be null");
        }
        return switch (type) {
            case DUPLICATE -> UploadSaveType.DUPLICATE;
            case OVERWRITE -> UploadSaveType.OVERWRITE;
            case VERSION -> dto.resolveContentSource() instanceof FileContentSource.StorageCopy ? UploadSaveType.RESTORE : UploadSaveType.VERSION;
        };
    }

    private ResourceSaveResult commit(UploadSaveType saveType, UUID id, String key, String baseStorageKey, FileUploadDto dto, int requestUserSeq,
                                      RequestActor recheckActor) {
        return switch (saveType) {
            case DUPLICATE -> uploadCommitService.commitDuplicate(id, key, dto, requestUserSeq, recheckActor);
            case VERSION, RESTORE -> uploadCommitService.commitVersion(id, key, dto, requestUserSeq, recheckActor);
            case OVERWRITE -> uploadCommitService.commitOverwrite(id, key, baseStorageKey, dto, requestUserSeq, recheckActor);
        };
    }

    private ResourceSaveResult commitWithRetry(UploadSaveType saveType, UUID id, String key, String baseStorageKey, FileUploadDto dto, int requestUserSeq,
                                               RequestActor recheckActor) {
        for (int attempt = 1; ; attempt++) {
            try {
                return commit(saveType, id, key, baseStorageKey, dto, requestUserSeq, recheckActor);
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
                return storedResult(session);
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
            if (api.getCode() instanceof PermissionErrorCode) {
                return UploadSessionFailReason.PERMISSION_DENIED;
            }
            if (api.getCode() == UploadSessionErrorCode.UPLOAD_CONTENT_MISMATCH) {
                return UploadSessionFailReason.CONTENT_MISMATCH;
            }
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

package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.result.StoragePart;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.systemconfig.exception.SystemConfigErrorCode;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import com.thinkfree.drive.upload.session.model.UploadPartPlan;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.repository.UploadLockRepository;
import com.thinkfree.drive.upload.session.repository.UploadSessionRepository;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.upload.session.service.UploadSessionTxService.CleanupPhase;
import com.thinkfree.drive.upload.session.service.UploadSessionWriter.OpenedSession;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.file.FileExtractResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * 청크 업로드 API(서버 경유 멀티파트 — docs/design/upload-session-redesign.md §5.3·§6.1~§6.3).
 * 브라우저가 파트를 하나씩 보내고, 끊기면 올라간 파트를 확인해 빠진 것만 다시 보낸 뒤 complete 한다.
 * <ul>
 *   <li>세션 생성·complete 의 실패 처리·커밋은 서버 내부 쓰기({@link UploadSessionWriter})와 같은 코드를 쓴다(규칙 16)</li>
 *   <li>모든 조회는 요청자 조건 — 다른 사용자의 세션은 없는 것과 같다(404, 규칙 14)</li>
 *   <li>청크 API 세션 = part_count 가 있는 세션. 서버 내부 쓰기 세션(같은 요청자라도)은 이 API 로 건드릴 수 없다 — 진행 중인 내부 쓰기를 가로채 complete·취소하지 못하게</li>
 *   <li>단일 요청 기준(32MB) 미만은 받지 않는다 — 기존 업로드 API(한 요청)를 쓴다. 단일 객체는 complete 뒤 늦게 도착한 PUT 이 커밋된 객체를 덮어쓸 수 있어서다(멀티파트는 complete 뒤 파트 PUT 이 NoSuchUpload)</li>
 * </ul>
 * 트랜잭션을 열지 않는다 — 스토리지 호출은 tx 밖, 상태 변경은 {@link UploadSessionTxService} 의 짧은 tx(규칙 7). 권한 검사는 호출자(usecase) 책임.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadChunkService {

    private final UploadSessionWriter uploadSessionWriter;
    private final UploadSessionTxService uploadSessionTxService;
    private final UploadSessionRepository uploadSessionRepository;
    private final UploadLockRepository uploadLockRepository;
    private final StorageService storageService;
    private final SystemConfigFindService systemConfigFindService;
    private final UserFindService userFindService;
    private final UploadSessionProperty property;

    /** 세션 생성 결과 — uploadId 는 내보내지 않는다. */
    public record Started(UUID sessionId, long partSize, int partCount) {
    }

    /** complete 결과. replayed = 이미 COMPLETED 였던 세션의 저장된 결과(재요청 멱등) — 감사 기록을 다시 남기지 않는다. */
    public record Completed(ResourceSaveResult result, ResourceSaveType saveType, boolean replayed) {
    }

    /** 세션 상태 + 올라간 파트 번호(기대 크기와 같은 것만 — 나머지는 다시 보내야 한다). */
    public record Status(UploadSessionData session, List<Integer> uploadedParts) {
    }

    /**
     * §6.1 — 크기 정책 → 세션 INSERT(용량 사전 필터·세션 수) → createMultipartUpload → uploadId 기록.
     *
     * @param userSeq 소유자(용량 대상) — 공유 폴더면 공유한 사용자
     * @throws ApiRuntimeException 작은 파일 UPLOAD_PART_INVALID, 크기 정책 FILE_SIZE_LIMIT_EXCEEDED, 용량 IS_FULL, 세션 수 UPLOAD_SESSION_LIMIT
     */
    public Started start(int userSeq, int requestUserSeq, ResourceSaveType saveType, Long parentFolderSeq, Long resourceSeq, String fileName, long size) {
        if (size < property.singleRequestThreshold().toBytes()) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_PART_INVALID)
                                     .with("reason", "size below chunk threshold — use the single-request upload")
                                     .with("minSize", property.singleRequestThreshold().toBytes());
        }
        if (size > property.absoluteMaxSize().toBytes()) {
            throw ApiRuntimeException.type(SystemConfigErrorCode.FILE_SIZE_LIMIT_EXCEEDED);
        }
        systemConfigFindService.validateFileSizeLimit(userFindService.findTenantSeqByUserSeq(userSeq), size);

        UploadPartPlan plan = UploadPartPlan.of(size, property.minPartSize().toBytes());
        FileUploadDto dto = toDto(userSeq, requestUserSeq, saveType, parentFolderSeq, resourceSeq, fileName, null, size);
        OpenedSession session = uploadSessionWriter.open(dto, requestUserSeq, plan.partSize(), plan.partCount());

        String uploadId;
        try {
            uploadId = storageService.createMultipartUpload(session.key(), session.dto().getContentType());
        } catch (RuntimeException e) {
            // 스토리지에 아무것도 없다 — 세션만 끝낸다(storage_key 가 비어 있어 정리 등록도 없다)
            failQuietly(session.id(), UploadSessionStatus.PENDING, UploadSessionFailReason.STORAGE_ERROR, e, CleanupPhase.BEFORE_ASSEMBLE);
            throw e;
        }
        try {
            uploadSessionTxService.attachStorage(session.id(), session.key(), uploadId);
        } catch (RuntimeException e) {
            // 세션 행에 uploadId 가 없어 일반 실패 처리로는 abort 가 안 걸린다 — 받은 uploadId 로 직접 등록
            uploadSessionTxService.failWithAbort(session.id(), session.key(), uploadId, UploadSessionFailReason.STORAGE_ERROR, describe(e));
            throw e;
        }
        return new Started(session.id(), plan.partSize(), plan.partCount());
    }

    /**
     * §6.2 — 파트 하나를 받은 그대로 스토리지로 흘린다(메모리·임시 파일에 담지 않음). 같은 번호 재전송은 덮어쓴다(규칙 15).
     *
     * @param contentLength 요청의 Content-Length(없으면 음수)
     * @throws ApiRuntimeException 세션 없음 404, PENDING 아님·대상 삭제됨(세션을 FAILED(TARGET_DELETED) 로 끝냄) 409 UPLOAD_SESSION_FAILED,
     *                             길이 없음 411, 번호·크기 다름 400 UPLOAD_PART_INVALID
     */
    public void uploadPart(UUID sessionId, int requestUserSeq, int partNumber, long contentLength, InputStream in) {
        UploadSessionData session = find(sessionId, requestUserSeq);
        requirePending(session);
        long expected = planOf(session).expectedPartSize(partNumber);
        if (expected < 0) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_PART_INVALID).with("partNumber", partNumber).with("partCount", session.partCount());
        }
        if (contentLength < 0) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_PART_LENGTH_REQUIRED);
        }
        if (contentLength != expected) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_PART_INVALID)
                                     .with("partNumber", partNumber).with("expected", expected).with("received", contentLength);
        }
        if (!isTargetAlive(session)) {
            // 그 사이 대상이 삭제됐다 — 나머지 수 GB 를 다 받고 complete 에서야 실패하지 않도록 지금 끝낸다(ABORT 등록)
            failQuietly(sessionId, UploadSessionStatus.PENDING, UploadSessionFailReason.TARGET_DELETED,
                        new IllegalStateException("upload target deleted"), CleanupPhase.BEFORE_ASSEMBLE);
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_FAILED).with("reason", UploadSessionFailReason.TARGET_DELETED);
        }
        try {
            storageService.uploadPart(session.storageKey(), session.uploadId(), partNumber, in, contentLength);
        } catch (StorageException e) {
            if (e.getCode() == StorageErrorCode.NO_SUCH_UPLOAD) {
                // 스토리지 쪽 업로드가 사라졌다(lifecycle 정리 등) — 더 올려도 소용없으므로 세션을 끝낸다. abort 등록은 멱등이라 그대로 둔다
                failQuietly(sessionId, UploadSessionStatus.PENDING, UploadSessionFailReason.NO_SUCH_UPLOAD, e, CleanupPhase.BEFORE_ASSEMBLE);
                throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_FAILED).with("reason", UploadSessionFailReason.NO_SUCH_UPLOAD);
            }
            throw e;
        }
        // 0행 = 그 사이 취소·만료·complete — 이 파트는 쓰이지 않는다(정리는 상태를 끝낸 쪽이 이미 등록)
        uploadSessionTxService.touch(sessionId);
    }

    /** 세션 상태 + (PENDING 이면) 올라간 파트 — 이어 올리기용. 파트 정본은 스토리지 ListParts(§6.2, DB 에 기록하지 않는다). */
    public Status status(UUID sessionId, int requestUserSeq) {
        UploadSessionData session = find(sessionId, requestUserSeq);
        if (session.status() != UploadSessionStatus.PENDING || session.uploadId() == null) {
            return new Status(session, List.of());
        }
        UploadPartPlan plan = planOf(session);
        try {
            List<Integer> uploaded = storageService.listParts(session.storageKey(), session.uploadId()).stream()
                                                   .filter(p -> p.size() == plan.expectedPartSize(p.partNumber()))
                                                   .map(StoragePart::partNumber)
                                                   .toList();
            return new Status(session, uploaded);
        } catch (StorageException e) {
            if (e.getCode() == StorageErrorCode.NO_SUCH_UPLOAD) {
                return new Status(session, List.of());
            }
            throw e;
        }
    }

    /** 요청자의 청크 API 세션 목록(최근 생성 순, 진행 중 세션 수 상한만큼). */
    public List<UploadSessionData> list(int requestUserSeq, UploadSessionStatus status) {
        return uploadSessionRepository.findByRequesterAndStatus(requestUserSeq, status, property.maxActiveSessionsPerUser()).stream()
                                      .filter(UploadChunkService::isChunkSession)
                                      .toList();
    }

    /** 요청자의 청크 API 세션 — 없거나 남의 것·내부 쓰기 세션이면 404. */
    public UploadSessionData find(UUID sessionId, int requestUserSeq) {
        return uploadSessionTxService.find(sessionId, requestUserSeq)
                                     .filter(UploadChunkService::isChunkSession)
                                     .orElseThrow(() -> ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_NOT_FOUND));
    }

    /**
     * §6.3 — COMPLETING 선점 → ListParts 검증 → 조립 → 크기 확인 → 커밋. 선점 이후는 서버 내부 쓰기와 같은 코드({@link UploadSessionWriter#finishClaimed}).
     * 선점 0행: COMPLETED 면 이전 결과(멱등), COMPLETING 이면 409 UPLOAD_SESSION_IN_PROGRESS, FAILED 면 410 UPLOAD_SESSION_GONE.
     * 검증 실패는 세션을 FAILED + ABORT 로 끝낸다(처음부터 다시) — 클라이언트는 complete 전에 GET 으로 빠진 파트를 채운다.
     */
    public Completed complete(UUID sessionId, int requestUserSeq) {
        UploadSessionData session = find(sessionId, requestUserSeq);
        ResourceSaveType saveType = resourceSaveTypeOf(session);
        if (session.status() == UploadSessionStatus.PENDING && session.uploadId() == null) {
            // 세션 생성 중 스토리지 준비 전에 멈춘 세션 — 조립할 것이 없다(TTL 뒤 스케줄러가 끝낸다)
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_FAILED).with("status", session.status());
        }
        try {
            uploadSessionTxService.markCompleting(sessionId, requestUserSeq);
        } catch (ApiRuntimeException e) {
            if (e.getCode() != UploadSessionErrorCode.UPLOAD_SESSION_FAILED) {
                throw e;
            }
            UploadSessionData now = find(sessionId, requestUserSeq);
            return switch (now.status()) {
                case COMPLETED -> new Completed(UploadSessionWriter.storedResult(now), saveType, true);
                case FAILED -> throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_GONE).with("reason", now.failReason());
                case PENDING, COMPLETING -> throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_IN_PROGRESS);
            };
        }
        FileUploadDto dto = toDto(session.userSeq(), requestUserSeq, saveType, session.parentFolderSeq(), session.resourceSeq(),
                session.fileName(), session.contentType(), session.sizeByte());
        OpenedSession opened = new OpenedSession(sessionId, session.storageKey(), session.saveType(), session.baseStorageKey(), dto);
        // 업로드가 길어 그 사이 공유가 해제됐을 수 있다 — 커밋 tx 에서 요청자 권한을 다시 본다(규칙 14). 청크 API 는 익명 불가
        ResourceSaveResult result = uploadSessionWriter.finishClaimed(opened, requestUserSeq, session.uploadId(), () -> verifiedParts(session),
                                                                      RequestActor.user(requestUserSeq));
        return new Completed(result, saveType, false);
    }

    /**
     * 취소 — PENDING → FAILED(CANCELED) + ABORT 등록. 이미 끝났거나 complete 중이어도 성공(재요청 멱등, 규칙 15).
     *
     * @return 취소 후 세션 상태
     */
    public UploadSessionStatus cancel(UUID sessionId, int requestUserSeq) {
        UploadSessionData session = find(sessionId, requestUserSeq);
        if (session.status() != UploadSessionStatus.PENDING) {
            return session.status();
        }
        if (uploadSessionTxService.fail(sessionId, UploadSessionStatus.PENDING, UploadSessionFailReason.CANCELED, "canceled by user", CleanupPhase.BEFORE_ASSEMBLE)) {
            return UploadSessionStatus.FAILED;
        }
        return find(sessionId, requestUserSeq).status();
    }

    /** §6.3 ② — 개수 == partCount, 1..N 연속, 각 크기 == 기대값(합계는 이로써 신고 크기와 같다). */
    private List<StoragePart> verifiedParts(UploadSessionData session) {
        UploadPartPlan plan = planOf(session);
        List<StoragePart> parts = storageService.listParts(session.storageKey(), session.uploadId());
        for (int i = 0; i < plan.partCount(); i++) {
            int partNumber = i + 1;
            StoragePart part = i < parts.size() ? parts.get(i) : null;
            if (part == null || part.partNumber() != partNumber || part.size() != plan.expectedPartSize(partNumber)) {
                throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_PARTS_INCOMPLETE)
                                         .with("partNumber", partNumber).with("partCount", plan.partCount()).with("received", parts.size());
            }
        }
        if (parts.size() != plan.partCount()) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_PARTS_INCOMPLETE).with("partCount", plan.partCount()).with("received", parts.size());
        }
        return parts;
    }

    private static void requirePending(UploadSessionData session) {
        if (session.status() != UploadSessionStatus.PENDING || session.uploadId() == null) {
            ApiRuntimeException e = ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_FAILED).with("status", session.status());
            if (session.failReason() != null) {
                e.with("reason", session.failReason());
            }
            throw e;
        }
    }

    /**
     * 커밋 대상이 아직 살아 있는지 — 신규는 부모 폴더(루트면 확인할 것 없음), 버전·덮어쓰기는 대상 파일.
     * 소유자(userSeq) 기준 — 커밋 tx 와 같은 조건(공유 폴더에 올리는 중이면 공유한 사용자의 리소스).
     */
    private boolean isTargetAlive(UploadSessionData session) {
        return switch (session.saveType()) {
            case DUPLICATE -> session.parentFolderSeq() == null
                    || uploadLockRepository.existsLive(session.userSeq(), session.parentFolderSeq(), ResourceType.FOLDER);
            case VERSION, OVERWRITE, RESTORE -> session.resourceSeq() != null
                    && uploadLockRepository.existsLive(session.userSeq(), session.resourceSeq(), ResourceType.FILE);
        };
    }

    /** 청크 API 세션 — 세션 생성 때 파트 계획을 남긴다(서버 내부 쓰기는 남기지 않는다). */
    private static boolean isChunkSession(UploadSessionData session) {
        return session.partCount() != null && session.partSize() != null;
    }

    /** 세션에 남긴 파트 계획 — 설정이 바뀌어도 진행 중 세션은 시작 때 계획 그대로. */
    private static UploadPartPlan planOf(UploadSessionData session) {
        return new UploadPartPlan(session.sizeByte(), session.partSize(), session.partCount());
    }

    private static ResourceSaveType resourceSaveTypeOf(UploadSessionData session) {
        return switch (session.saveType()) {
            case DUPLICATE -> ResourceSaveType.DUPLICATE;
            case VERSION, RESTORE -> ResourceSaveType.VERSION;
            case OVERWRITE -> ResourceSaveType.OVERWRITE;
        };
    }

    /**
     * 세션 생성과 complete 가 같은 dto 를 만든다 — 웹 업로드({@code FileCommandConverter.toUploadDto})와 같은 필드.
     * contentType 이 null 이면 세션 생성 때 서버가 확장자로 정한다(규칙 3).
     */
    static FileUploadDto toDto(int userSeq, int requestUserSeq, ResourceSaveType saveType, Long parentFolderSeq, Long resourceSeq,
                               String fileName, String contentType, long size) {
        FileExtractResult extracted = ResourceUtil.extract(fileName);
        return FileUploadDto.builder()
                            .userSeq(userSeq)
                            .ownerSeq(requestUserSeq)
                            .resourceSeq(resourceSeq)
                            .parentFolderSeq(parentFolderSeq)
                            .baseFileName(extracted.baseFileName())
                            .originalFileName(extracted.originalFileName())
                            .originalFileNameWithoutExt(extracted.originalFileNameWithoutExt())
                            .ext(extracted.ext())
                            .contentType(contentType)
                            .fileSize(size)
                            .copyNumber(extracted.copyNumber())
                            .copyFile(extracted.copyFile())
                            .resourceSaveType(saveType)
                            .lockedResourceCheck(true)
                            .build();
    }

    private void failQuietly(UUID sessionId, UploadSessionStatus expected, UploadSessionFailReason reason, RuntimeException cause, CleanupPhase phase) {
        try {
            uploadSessionTxService.fail(sessionId, expected, reason, describe(cause), phase);
        } catch (RuntimeException failError) {
            // 실패 기록조차 못 함 — 세션은 PENDING 으로 남고 TTL 뒤 스케줄러가 정리한다
            cause.addSuppressed(failError);
            log.error("[UploadChunkService] could not mark session failed — left for scheduler. id: {}", sessionId, failError);
        }
    }

    private static String describe(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}

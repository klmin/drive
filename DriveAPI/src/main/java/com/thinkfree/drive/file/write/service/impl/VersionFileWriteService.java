package com.thinkfree.drive.file.write.service.impl;

import com.thinkfree.drive.common.annotation.ApiRetryable;
import com.thinkfree.drive.file.write.service.FileWriteService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.resource.version.service.ResourceVersionRetryService;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VERSION(현재 내용을 이력으로 남기고 새 내용 저장) 쓰기 핸들러 — 파일 버전 업로드·웹오피스 저장·버전 복원 세 흐름의 공통 경로(5차 패스 D1).
 * 구 흐름은 사용량 예약·메타 저장·보상이 각각 별도 트랜잭션이었고, 스토리지 뒤의 마무리·후처리 실패에도 메타를 되돌려
 * 스토리지 내용과 어긋났다. Duplicate/Overwrite 와 같은 구조로 바꿨다:
 * <ul>
 *   <li>prepareMeta — 사용량 예약(reserveQuotaInTx)과 버전 메타 저장을 한 트랜잭션, 시도마다 새 트랜잭션으로 재시도</li>
 *   <li>doStorage — 새 내용의 출처별 스토리지 호출(업로드 파일 / 웹오피스 스트림 / 복원할 버전 객체). 실패 시 오케스트레이터가 rollback</li>
 *   <li>afterStorage — finalize(새 버전 PENDING → SUCCESS, 한도에서 밀려난 버전 행 삭제). 실패해도 롤백하지 않음(오케스트레이터가 ERROR 로그).
 *       새 버전이 PENDING 으로 남으면 다음 저장의 만료 정리(cleanVersionIfExists)가 원본 키의 저장 마커(= 그 버전 seq)로 업로드 완료를 확인해
 *       finalize 를 대신 마친다 — 메타·사용량은 그대로(구 흐름은 즉시 되돌렸다)</li>
 *   <li>rollback — 메타 되돌리기 + 사용량 감소를 한 트랜잭션으로 묶고 재시도(구 흐름은 둘이 따로라 반쪽 보상 가능)</li>
 * </ul>
 * 사용량 알림은 오케스트레이터의 FileUploadedEvent(비동기 리스너).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionFileWriteService implements FileWriteService {

    private final ResourceService resourceService;
    private final ResourceVersionRetryService resourceVersionRetryService;
    private final QuotaService quotaService;
    private final UserService userService;
    private final StorageService storageService;

    @Override
    public ResourceSaveType supports() {
        return ResourceSaveType.VERSION;
    }

    @ApiRetryable
    @Transactional
    @Override
    public ResourceSaveResult prepareMeta(FileUploadDto dto, int requestUserSeq) {
        try {
            quotaService.reserveQuotaInTx(dto.getFileSize(), dto.getUserSeq(), requestUserSeq);
            return resourceService.updateWithVersioning(dto);
        } catch (DataAccessException | IllegalStateException e) {
            // ApiRuntimeException(IS_FULL·VERSION_PROCESSING 등)은 원형 그대로 전파 — 인프라 예외만 재시도 가능형으로 변환
            log.error("[VersionFileWriteService prepareMeta fail] retryCount : {}, userSeq : {}, resourceSeq : {}",
                    RetryUtil.getRetryCount(), dto.getUserSeq(), dto.getResourceSeq(), e);
            throw ApiRuntimeException.type(ResourceErrorCode.INSERT_FAIL);
        }
    }

    /** 원본 키의 현재 내용을 버전 키로 남기고 원본 키에 새 내용 — 출처별(업로드 파일 / 웹오피스 스트림 / 복원할 버전 객체). */
    @Override
    public void doStorage(FileUploadDto dto, ResourceSaveResult result) {
        String key = result.resourceSaveName();
        String versionKey = result.versionResourceSaveName();
        // 새 내용에 이 저장의 버전 seq 를 남긴다 — 만료 정리가 원본 키의 마커로 업로드 완료를 판정(cleanVersionIfExists)
        String saveMarker = String.valueOf(result.resourceVersionSeq());
        FileContentSource source = dto.resolveContentSource();
        if (source instanceof FileContentSource.Upload upload) {
            storageService.copyAndUpload(upload.file(), key, versionKey, saveMarker);
        } else if (source instanceof FileContentSource.Stream stream) {
            storageService.copyAndUpload(stream.data(), key, versionKey, saveMarker);
        } else if (source instanceof FileContentSource.StorageCopy copy) {
            storageService.backUpAndRestore(key, versionKey, copy.sourceKey(), saveMarker);
        } else {
            throw new IllegalStateException("Unsupported content source: " + source);
        }
    }

    @Override
    public void afterStorage(ResourceSaveResult result) {
        resourceVersionRetryService.finalizeAfterStorageUpload(result.resourceVersionSeq(), result.userSeq(), result.rollbackData());
    }

    @ApiRetryable
    @Transactional
    @Override
    public void rollback(ResourceSaveResult result) {
        try {
            resourceService.rollbackVersion(result);
            userService.decreaseUseQuotaByte(result.rollbackSizeByte(), result.userSeq());
        } catch (DataAccessException | IllegalStateException e) {
            log.error("[VersionFileWriteService rollback fail] retryCount : {}, userSeq : {}, resourceSeq : {}",
                    RetryUtil.getRetryCount(), result.userSeq(), result.resourceSeq(), e);
            throw ApiRuntimeException.type(ResourceErrorCode.DELETE_FAIL);
        }
    }

    @Recover
    public ResourceSaveResult prepareMetaRecover(ApiRuntimeException e, FileUploadDto dto, int requestUserSeq) {
        log.error("[VersionFileWriteService prepareMeta retry failed] message : {}, userSeq : {}, resourceSeq : {}, retryCount : {}",
                e.getMessage(), dto.getUserSeq(), dto.getResourceSeq(), RetryUtil.getRetryCount());
        throw e;
    }

    @Recover
    public void rollbackRecover(ApiRuntimeException e, ResourceSaveResult result) {
        log.error("[VersionFileWriteService rollback retry failed] message : {}, userSeq : {}, resourceSeq : {}, retryCount : {}",
                e.getMessage(), result.userSeq(), result.resourceSeq(), RetryUtil.getRetryCount());
        throw e;
    }
}

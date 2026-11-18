package com.thinkfree.drive.file.write.service.impl;

import com.thinkfree.drive.common.annotation.ApiRetryable;
import com.thinkfree.drive.file.write.service.FileWriteService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.util.ResourceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DUPLICATE(신규 저장, 동명 시 넘버링) 쓰기 핸들러 — 구 FileUseCase.upload DUPLICATE 분기의 이관이되,
 * 트랜잭션 구성은 Overwrite 동형으로 재설계했다: 쿼터 예약(reserveQuotaInTx)과 메타 저장을 한 트랜잭션에
 * 묶어 저장 실패 시 예약까지 자동 원복된다(구 방식의 "예약 커밋 → 실패 → 보상 감소" 창구 제거).
 * 같은 이유로 REQUIRES_NEW인 RetryService를 거치지 않고 도메인 서비스를 직접 호출한다 — 재시도는
 * prepareMeta 전체에 @ApiRetryable(시도마다 새 트랜잭션). 쿼터 부족(IS_FULL)은 변환 없이 원형 전파해
 * 사용자에게 용량 부족 메시지가 그대로 닿게 한다(RetryUtil 이 재시도에서 제외 — 용량 초과 알림 1회).
 * rollback(스토리지 실패 보상)도 삭제+쿼터감소를 한 트랜잭션으로 묶고 재시도를 달아 부분 보상을 막는다.
 * 크기 정책 검증·업로드 후처리(FileUploadedEvent)는 FileWriteOrchestrator가 공통 처리.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateFileWriteService implements FileWriteService {

    private final ResourceService resourceService;
    private final QuotaService quotaService;
    private final UserService userService;
    private final StorageService storageService;

    @Override
    public ResourceSaveType supports() {
        return ResourceSaveType.DUPLICATE;
    }

    @ApiRetryable
    @Transactional
    @Override
    public ResourceSaveResult prepareMeta(FileUploadDto dto, int requestUserSeq) {
        try {
            quotaService.reserveQuotaInTx(dto.getFileSize(), dto.getUserSeq(), requestUserSeq);
            return resourceService.saveWithCopyCheck(dto);
        } catch (DataAccessException | IllegalStateException e) {
            // ApiRuntimeException(IS_FULL 포함)은 원형 그대로 전파 — 여기서는 인프라 예외만 재시도 가능형으로 변환
            log.error("[DuplicateFileWriteService prepareMeta fail] retryCount : {}, userSeq : {}, originalFileName : {}",
                    RetryUtil.getRetryCount(), dto.getUserSeq(), dto.getOriginalFileName(), e);
            throw ApiRuntimeException.type(ResourceErrorCode.INSERT_FAIL);
        }
    }

    @ApiRetryable
    @Transactional
    @Override
    public void rollback(ResourceSaveResult result) {
        try {
            resourceService.delete(result.resourceSeq(), result.userSeq());
            userService.decreaseUseQuotaByte(result.rollbackSizeByte(), result.userSeq());
        } catch (DataAccessException | IllegalStateException e) {
            log.error("[DuplicateFileWriteService rollback fail] retryCount : {}, userSeq : {}, resourceSeq : {}",
                    RetryUtil.getRetryCount(), result.userSeq(), result.resourceSeq(), e);
            throw ApiRuntimeException.type(ResourceErrorCode.DELETE_FAIL);
        }
    }

    /** 새 키에 내용 — 출처별(업로드 파일 / 스트림(문서 템플릿) / 스토리지 객체 복사(파일 복사)). */
    @Override
    public void doStorage(FileUploadDto dto, ResourceSaveResult result) {
        String key = ResourceUtil.generateKey(result.userSeq(), result.resourceSeq());
        FileWriteService.uploadTo(storageService, dto.resolveContentSource(), key);
    }

    @Recover
    public ResourceSaveResult prepareMetaRecover(ApiRuntimeException e, FileUploadDto dto, int requestUserSeq) {
        log.error("[DuplicateFileWriteService prepareMeta retry failed] message : {}, userSeq : {}, originalFileName : {}, retryCount : {}",
                e.getMessage(), dto.getUserSeq(), dto.getOriginalFileName(), RetryUtil.getRetryCount());
        throw e;
    }

    @Recover
    public void rollbackRecover(ApiRuntimeException e, ResourceSaveResult result) {
        // 보상 최종 실패 = 고아 메타 또는 쿼터 과계상 잔존 가능 — 운영 추적을 위해 명시 로그 후 전파
        log.error("[DuplicateFileWriteService rollback retry failed] message : {}, userSeq : {}, resourceSeq : {}, retryCount : {}",
                e.getMessage(), result.userSeq(), result.resourceSeq(), RetryUtil.getRetryCount());
        throw e;
    }

}

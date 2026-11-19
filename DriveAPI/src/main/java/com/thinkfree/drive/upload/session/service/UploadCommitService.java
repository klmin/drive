package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.cleanup.storage.service.StorageDeletionService;
import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.repository.UploadLockRepository;
import com.thinkfree.drive.upload.session.repository.UploadLockRepository.UserQuota;
import com.thinkfree.drive.upload.session.repository.UploadSessionRepository;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 커밋 tx(docs/design/upload-session-redesign.md §6.3 ⑥, §4.2) — 객체는 이미 스토리지에 있고, 여기서 DB 에 반영한다.
 * <ul>
 *   <li>잠금 순서(규칙 8): 사용자 행 → 대상 리소스 → (버전 행) / 부모 폴더 행</li>
 *   <li>용량 최종 판정·use_quota_byte 변경은 여기서만(규칙 10·11)</li>
 *   <li>교체·밀려난 이전 key 는 같은 tx 에서 삭제 등록(유예, 규칙 6)</li>
 *   <li>COMPLETED 전환이 리소스 반영과 같은 tx — COMPLETING = 미반영, COMPLETED = 반영(§5.1)</li>
 *   <li>사용량 알림 이벤트는 tx 안에서 발행해 커밋 성공 시에만 리스너가 돈다(규칙 13, FileUploadedListener 는 AFTER_COMMIT)</li>
 * </ul>
 * S3 호출 없음. REQUIRES_NEW — 호출자 트랜잭션과 무관하게 이 단위로 커밋·롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadCommitService {

    private final UploadLockRepository uploadLockRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final ResourceService resourceService;
    private final UserService userService;
    private final StorageDeletionService storageDeletionService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UploadSessionProperty property;

    /**
     * DUPLICATE(신규) — 부모 폴더 잠금(규칙 9) → 이름 넘버링 → 파일 행 INSERT(key = 세션 key) → 용량 → COMPLETED.
     *
     * @throws ApiRuntimeException 부모 폴더 없음·휴지통 NOT_FOUND, 용량 초과 IS_FULL
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResourceSaveResult commitDuplicate(UUID uploadSessionId, String storageKey, FileUploadDto dto, int requestUserSeq) {
        int userSeq = dto.getUserSeq();
        long size = dto.getFileSize();

        UserQuota quota = lockUser(userSeq);
        String parentFolderName = null;
        if (dto.getParentFolderSeq() != null) {
            // 루트면 위 사용자 행 잠금이 같은 역할(규칙 9)
            parentFolderName = uploadLockRepository.lockLiveFolder(userSeq, dto.getParentFolderSeq())
                                                   .orElseThrow(() -> ApiRuntimeException.type(ResourceErrorCode.NOT_FOUND))
                                                   .resourceName();
        }

        if (quota.wouldExceed(size)) {
            throw ApiRuntimeException.type(QuotaErrorCode.IS_FULL);
        }

        var result = resourceService.saveUploadedFile(dto, storageKey, parentFolderName);
        userService.increaseUseQuotaByte(size, userSeq);
        return complete(uploadSessionId, result, userSeq, requestUserSeq);
    }

    /**
     * VERSION·RESTORE — 리소스 행 잠금 → 현재 내용(key·크기)을 버전 행으로 넘김 → 리소스를 세션 key·새 크기로.
     * 사용량 = +새 크기 − 밀려난 버전 크기(복원도 같은 정책: 같은 내용이 버전과 현재에 하나씩). 밀려난 버전 key 는 삭제 등록(유예).
     *
     * @throws ApiRuntimeException 대상 없음 NOT_FOUND, 잠김(복원) RESOURCE_LOCKED, 용량 초과 IS_FULL
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResourceSaveResult commitVersion(UUID uploadSessionId, String storageKey, FileUploadDto dto, int requestUserSeq) {
        UserQuota quota = lockUser(dto.getUserSeq());
        return applyVersion(uploadSessionId, storageKey, dto, requestUserSeq, quota);
    }

    /**
     * OVERWRITE — 현재 key 가 세션 시작 때 본 key(baseKey)와 같을 때만 새 key·크기로 바꾸고 이전 key 를 삭제 등록(유예).
     * 그 사이 다른 저장이 내용을 바꿨으면(충돌) 내용을 잃지 않도록 버전 저장으로 반영한다(§4.3 1차).
     *
     * @throws ApiRuntimeException 대상 없음 NOT_FOUND, 용량 초과 IS_FULL
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResourceSaveResult commitOverwrite(UUID uploadSessionId, String storageKey, String baseStorageKey, FileUploadDto dto, int requestUserSeq) {
        int userSeq = dto.getUserSeq();
        UserQuota quota = lockUser(userSeq);

        var result = resourceService.overwriteContent(dto, storageKey, baseStorageKey);
        if (result == null) {
            log.warn("[UploadCommit] overwrite conflict — saving as a new version instead. id: {}, resourceSeq: {}, baseKey: {}",
                     uploadSessionId, dto.getResourceSeq(), baseStorageKey);
            return applyVersion(uploadSessionId, storageKey, dto, requestUserSeq, quota);
        }

        long delta = result.rollbackSizeByte();   // 새 크기 − 이전 크기
        if (delta > 0 && quota.wouldExceed(delta)) {
            throw ApiRuntimeException.type(QuotaErrorCode.IS_FULL);
        }
        adjustQuota(delta, userSeq);
        storageDeletionService.registerDeferred(List.of(baseStorageKey), property.replacedKeyDeleteDelay());
        return complete(uploadSessionId, result, userSeq, requestUserSeq);
    }

    private ResourceSaveResult applyVersion(UUID uploadSessionId, String storageKey, FileUploadDto dto, int requestUserSeq, UserQuota quota) {
        int userSeq = dto.getUserSeq();
        var replaced = resourceService.replaceWithVersioning(dto, storageKey);
        long delta = dto.getFileSize() - replaced.prunedSizeByte();
        if (delta > 0 && quota.wouldExceed(delta)) {
            throw ApiRuntimeException.type(QuotaErrorCode.IS_FULL);
        }
        adjustQuota(delta, userSeq);
        if (replaced.prunedResourceSaveName() != null) {
            storageDeletionService.registerDeferred(List.of(replaced.prunedResourceSaveName()), property.replacedKeyDeleteDelay());
        }
        return complete(uploadSessionId, replaced.saveResult(), userSeq, requestUserSeq);
    }

    private UserQuota lockUser(int userSeq) {
        return uploadLockRepository.lockUser(userSeq).orElseThrow(() -> ApiRuntimeException.type(UserErrorCode.USER_NOT_FOUND));
    }

    private void adjustQuota(long delta, int userSeq) {
        if (delta > 0) {
            userService.increaseUseQuotaByte(delta, userSeq);
        } else if (delta < 0) {
            userService.decreaseUseQuotaByte(-delta, userSeq);
        }
    }

    /** COMPLETED + 사용량 후처리 이벤트 — 0행이면 그 사이 스케줄러가 COMPLETING 시간 초과로 FAILED 처리한 것이라 전체 롤백(정리는 그쪽이 이미 등록). */
    private ResourceSaveResult complete(UUID uploadSessionId, ResourceSaveResult result, int userSeq, int requestUserSeq) {
        if (uploadSessionRepository.markCompleted(uploadSessionId, result.resourceSeq(), result.resourceName()) != 1) {
            throw new IllegalStateException("upload session is no longer COMPLETING: " + uploadSessionId);
        }
        Instant occurred = result.occurredDatetime() != null ? result.occurredDatetime() : Instant.now();
        applicationEventPublisher.publishEvent(new FileUploadedEvent(userSeq, requestUserSeq, occurred));
        return result;
    }
}

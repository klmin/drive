package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.event.file.FileUploadedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.upload.session.repository.UploadLockRepository;
import com.thinkfree.drive.upload.session.repository.UploadSessionRepository;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 커밋 tx(docs/design/upload-session-redesign.md §6.3 ⑥) — 객체는 이미 스토리지에 있고, 여기서 DB 에 반영한다.
 * <ul>
 *   <li>잠금 순서(규칙 8): 사용자 행 → (대상 리소스) → 부모 폴더 행, 모두 FOR NO KEY UPDATE</li>
 *   <li>용량 최종 판정·use_quota_byte 증가는 여기서만(규칙 10·11)</li>
 *   <li>COMPLETED 전환이 리소스 반영과 같은 tx — COMPLETING = 미반영, COMPLETED = 반영(§5.1)</li>
 *   <li>사용량 알림 이벤트는 tx 안에서 발행해 커밋 성공 시에만 리스너가 돈다(규칙 13, FileUploadedListener 는 AFTER_COMMIT)</li>
 * </ul>
 * S3 호출 없음. REQUIRES_NEW — 호출자 트랜잭션과 무관하게 이 단위로 커밋·롤백된다.
 */
@Service
@RequiredArgsConstructor
public class UploadCommitService {

    private final UploadLockRepository uploadLockRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final ResourceService resourceService;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * DUPLICATE(신규) 커밋 — 부모 폴더 잠금(규칙 9) → 이름 넘버링 → 파일 행 INSERT(key = 세션 key) → 용량 → COMPLETED.
     *
     * @throws ApiRuntimeException 부모 폴더 없음·휴지통 NOT_FOUND, 용량 초과 IS_FULL
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResourceSaveResult commitDuplicate(UUID uploadSessionId, String storageKey, FileUploadDto dto, int requestUserSeq) {
        int userSeq = dto.getUserSeq();
        long size = dto.getFileSize();

        var quota = uploadLockRepository.lockUser(userSeq)
                                        .orElseThrow(() -> ApiRuntimeException.type(UserErrorCode.USER_NOT_FOUND));
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

        if (uploadSessionRepository.markCompleted(uploadSessionId, result.resourceSeq(), result.resourceName()) != 1) {
            // 그 사이 스케줄러가 COMPLETING 시간 초과로 FAILED 처리 — 전체 롤백(객체 정리는 그쪽이 이미 등록)
            throw new IllegalStateException("upload session is no longer COMPLETING: " + uploadSessionId);
        }

        applicationEventPublisher.publishEvent(new FileUploadedEvent(userSeq, requestUserSeq, result.occurredDatetime()));
        return result;
    }
}

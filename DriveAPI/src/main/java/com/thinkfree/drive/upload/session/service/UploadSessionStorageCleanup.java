package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.cleanup.storage.service.StorageDeletionService;
import com.thinkfree.drive.upload.session.data.UploadSessionCleanupTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 세션을 FAILED 로 끝낸 쪽이 같은 트랜잭션에서 부르는 정리 등록(docs/design/upload-session-redesign.md 규칙 5, §9.1).
 * 사전 SELECT 중복 확인 없음 — 조건부 UPDATE 가 1행일 때만 부르므로 1회 등록되고, 중복돼도 삭제·abort 는 멱등이다.
 * <ul>
 *   <li>complete(조립) 전 실패·취소·만료 → ABORT_MULTIPART(멀티파트) / DELETE(단일 요청 — 객체가 올라갔을 수 있다)</li>
 *   <li>complete 후 실패 → DELETE</li>
 *   <li>complete 결과 불명확(타임아웃·COMPLETING 시간 초과) → ABORT_MULTIPART + DELETE</li>
 * </ul>
 * 세션 key 는 COMPLETED 전에는 어느 리소스 행도 참조하지 않으므로 즉시 삭제해도 안전하다.
 */
@Component
@RequiredArgsConstructor
public class UploadSessionStorageCleanup {

    private final StorageDeletionService storageDeletionService;

    /** 조립(complete) 전에 끝난 세션. */
    public void registerBeforeAssemble(UploadSessionCleanupTarget target) {
        if (target.uploadId() != null) {
            storageDeletionService.registerAbort(target.storageKey(), target.uploadId());
            return;
        }
        registerDelete(target);
    }

    /** 조립 뒤(객체가 생긴 뒤) 끝난 세션. */
    public void registerAfterAssemble(UploadSessionCleanupTarget target) {
        registerDelete(target);
    }

    /** 조립 성공 여부를 모를 때 — 둘 다. */
    public void registerAmbiguous(UploadSessionCleanupTarget target) {
        if (target.uploadId() != null) {
            storageDeletionService.registerAbort(target.storageKey(), target.uploadId());
        }
        registerDelete(target);
    }

    private void registerDelete(UploadSessionCleanupTarget target) {
        if (target.storageKey() != null) {
            storageDeletionService.register(List.of(target.storageKey()));
        }
    }
}

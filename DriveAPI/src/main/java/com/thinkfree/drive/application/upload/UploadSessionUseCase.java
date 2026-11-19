package com.thinkfree.drive.application.upload;

import com.thinkfree.drive.application.upload.command.UploadSessionStartCommand;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.service.UploadChunkService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * 청크 업로드 API usecase — 권한 검사와 감사 기록만 여기서, 세션·스토리지·커밋은 {@link UploadChunkService}.
 * <ul>
 *   <li>권한은 세션 생성 때 웹 업로드({@code FileUseCase.upload})와 같은 기준(신규 = 부모 폴더 UPLOAD, 그 외 = 대상 EDIT)으로 보고,
 *       complete 때 한 번 더 본다 — 업로드가 길어 그 사이 공유가 해제될 수 있다(설계 규칙 14). 커밋 tx 안 재확인은 todo B</li>
 *   <li>익명 사용자는 쓰지 않는다(시큐리티 설정에서 USER 역할만 — 인텔릭트 익명 덮어쓰기는 기존 단일 요청 API)</li>
 *   <li>파트 PUT·상태 조회·취소는 세션 요청자 조건 조회만으로 충분하다(남의 세션은 404)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UploadSessionUseCase {

    private final UploadChunkService uploadChunkService;
    private final ResourcePermissionService resourcePermissionService;
    private final ResourceAuditService resourceAuditService;

    public UploadChunkService.Started start(UploadSessionStartCommand command, int requestUserSeq) {
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        ResourceSaveType saveType = command.saveType() == null ? ResourceSaveType.DUPLICATE : command.saveType();
        Long resourceSeq = saveType.isDuplicate() ? null : command.resourceSeq();
        Long parentFolderSeq = saveType.isDuplicate() ? command.parentFolderSeq() : null;
        checkPermission(saveType.isDuplicate(), parentFolderSeq, resourceSeq, userSeq, requestUserSeq);
        return uploadChunkService.start(userSeq, requestUserSeq, saveType, parentFolderSeq, resourceSeq, command.fileName(), command.size());
    }

    public void uploadPart(UUID sessionId, int partNumber, long contentLength, InputStream in, int requestUserSeq) {
        uploadChunkService.uploadPart(sessionId, requestUserSeq, partNumber, contentLength, in);
    }

    public UploadChunkService.Status status(UUID sessionId, int requestUserSeq) {
        return uploadChunkService.status(sessionId, requestUserSeq);
    }

    public List<UploadSessionData> list(UploadSessionStatus status, int requestUserSeq) {
        return uploadChunkService.list(requestUserSeq, status == null ? UploadSessionStatus.PENDING : status);
    }

    @AuditLog
    public UploadChunkService.Completed complete(UUID sessionId, int requestUserSeq) {
        UploadSessionData session = uploadChunkService.find(sessionId, requestUserSeq);
        if (session.status() == UploadSessionStatus.PENDING) {
            // 업로드 중 공유 해제·권한 변경 — 선점 전에 다시 본다(실패하면 세션은 PENDING 그대로, 사용자가 취소하거나 TTL 뒤 정리)
            boolean duplicate = session.saveType() == com.thinkfree.drive.upload.session.enums.UploadSaveType.DUPLICATE;
            checkPermission(duplicate, session.parentFolderSeq(), session.resourceSeq(), session.userSeq(), requestUserSeq);
        }
        var completed = uploadChunkService.complete(sessionId, requestUserSeq);
        if (!completed.replayed()) {
            boolean edit = completed.saveType().isOverwrite();
            resourceAuditService.record(edit ? EventName.FILE_EDIT : EventName.FILE_UPLOAD, completed.result().resourceSeq(), session.userSeq());
        }
        return completed;
    }

    public UploadSessionStatus cancel(UUID sessionId, int requestUserSeq) {
        return uploadChunkService.cancel(sessionId, requestUserSeq);
    }

    private void checkPermission(boolean duplicate, Long parentFolderSeq, Long resourceSeq, int userSeq, int requestUserSeq) {
        RequestActor actor = RequestActor.of(requestUserSeq, false);
        if (duplicate) {
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, parentFolderSeq, userSeq, actor, false);
            return;
        }
        if (resourceSeq == null) {
            throw ApiRuntimeException.type(ResourceErrorCode.MISSING_RESOURCE_SEQ);
        }
        resourcePermissionService.checkPermission(SharePermissionType.EDIT, resourceSeq, userSeq, actor, false);
    }
}

package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.upload.session.service.UploadSessionWriter;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 파일 쓰기 진입점 — 테넌트 파일 크기 정책을 확인하고 업로드 세션 쓰기({@link UploadSessionWriter})로 넘긴다.
 * 모든 저장 유형(신규·버전·복원·덮어쓰기)이 세션 → 저장 → 커밋 하나의 경로를 탄다(docs/design/upload-session-redesign.md 규칙 16).
 * 저장 유형별 핸들러·스토리지 실패 보상(rollback)은 없어졌다 — 사용량·메타는 커밋 tx 에서만 바뀌므로 되돌릴 것이 없다.
 * 사용량 알림 이벤트는 커밋 tx 가 발행한다(커밋 성공 시에만 후처리).
 */
@Service
@RequiredArgsConstructor
public class FileWriteOrchestrator {

    private final SystemConfigFindService systemConfigFindService;
    private final UserFindService userFindService;
    private final UploadSessionWriter uploadSessionWriter;

    public ResourceSaveResult write(FileUploadDto dto, int requestUserSeq) {
        if (dto.getResourceSaveType() == null) {
            throw new IllegalArgumentException("ResourceSaveType must not be null");
        }
        Integer ownerTenantSeq = userFindService.findTenantSeqByUserSeq(dto.getUserSeq());
        systemConfigFindService.validateFileSizeLimit(ownerTenantSeq, dto.getFileSize());
        return uploadSessionWriter.write(dto, requestUserSeq);
    }
}

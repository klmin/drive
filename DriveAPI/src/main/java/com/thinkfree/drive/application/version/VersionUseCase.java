package com.thinkfree.drive.application.version;

import com.thinkfree.drive.application.page.OffsetPageInfo;
import com.thinkfree.drive.application.version.command.VersionListCommand;
import com.thinkfree.drive.common.annotation.ApiRetryable;
import com.thinkfree.drive.application.version.converter.VersionCommandConverter;
import com.thinkfree.drive.application.version.converter.VersionInfoConverter;
import com.thinkfree.drive.application.version.info.VersionListInfo;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.aspect.exception.ResourceLockErrorCode;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessQuotaChangeDto;
import com.thinkfree.drive.resource.result.ResourceDeleteResult;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 진입점(내부 API·MCP) 공용 리소스 버전 usecase — 목록/복원/삭제.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 다운로드(스트리밍·토큰)는 전송 결합이라 {@link VersionDownloadUseCase}로 분리.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionUseCase {

    private final VersionCommandConverter versionCommandConverter;
    private final VersionInfoConverter versionInfoConverter;
    private final ResourceRetryService resourceRetryService;
    private final ResourceService resourceService;
    private final ResourceVersionFindService resourceVersionFindService;
    private final UserService userService;
    private final ApplicationEventPublisher publisher;
    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    private final FileWriteOrchestrator fileWriteOrchestrator;

    public OffsetPageInfo<VersionListInfo> findAllPaginated(VersionListCommand command, int requestUserSeq) {
        var query = versionCommandConverter.toListQuery(command, requestUserSeq);
        var list = resourceVersionFindService.findAll(query);
        var count = resourceVersionFindService.countAll(query);
        return OffsetPageInfo.of(versionInfoConverter.toListInfos(list), count, command.pageIndex(), command.pageSize());
    }

    public void restoreVersion(Long resourceSeq, Long resourceVersionSeq, Integer sharedByUserSeq, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);

        var query = versionCommandConverter.toFindQuery(resourceVersionSeq, resourceSeq, userSeq);
        var data = resourceVersionFindService.findResourceVersionWithResource(query);
        var restoreSize = data.getVersionSizeByte();

        // 복원할 버전 객체를 새 내용으로 하는 VERSION 저장(FileWriteOrchestrator) — 쿼터 예약·메타 저장 한 트랜잭션, 스토리지 실패만 롤백,
        // 사용량 알림은 비동기 이벤트(5차 패스 D1). 실패는 어느 단계든 RESTORE_FAIL(구 동작과 같음)
        var fileUploadDto = versionCommandConverter.toFileUploadDto(resourceSeq, userSeq, requestUserSeq, restoreSize)
                .toBuilder().contentSource(new FileContentSource.StorageCopy(data.getVersionResourceSaveName()))
                // 잠김 확인은 버전 저장이 행 잠금 뒤 같은 트랜잭션에서 한다(구 컨트롤러 @CheckResourceLock·MCP 툴 명시 검사 대체)
                .unlockedRequired(true).build();
        try {
            fileWriteOrchestrator.write(fileUploadDto, requestUserSeq);
        } catch (ApiRuntimeException | StorageException e) {
            // 잠김은 복원 실패가 아니라 요청 거절 — RESOURCE_LOCKED(423) 그대로 돌려준다(구 @CheckResourceLock 과 같은 응답)
            if (e instanceof ApiRuntimeException api && api.getCode() == ResourceLockErrorCode.RESOURCE_LOCKED) {
                throw api;
            }
            log.error("[VersionUseCase restoreVersion Exception] resourceSeq : {}, resourceVersionSeq : {}, userSeq : {}", resourceSeq, resourceVersionSeq, userSeq, e);
            throw ApiRuntimeException.type(ResourceVersionErrorCode.RESTORE_FAIL);
        }
    }

    /**
     * 버전 삭제 + 사용량 후처리를 한 트랜잭션으로, 재시도는 그 트랜잭션 전체(시도마다 새 트랜잭션, 10-14 점검 2-a) — 안쪽 재시도
     * (ResourceRetryService·PostProcessingOrchestratorRetry)는 합류 중이라 재시도 없이 오류 코드(DELETE_FAIL·QUOTA_STATE_UPSERT_FAILED)만 만든다.
     * 잠금 순서 = 사용자 → 리소스 → 버전(업로드·버전 저장·덮어쓰기와 같게, 6-d) — 사용자 행을 먼저 잡는다. 뒤의 사용량 갱신은 같은 tx 라 대기 없음.
     */
    @ApiRetryable
    @Transactional
    public void deleteVersion(Long resourceSeq, Long resourceVersionSeq, int userSeq){
        userService.lockUserBySeq(userSeq);
        var deleteResult = resourceRetryService.deleteVersion(resourceSeq, resourceVersionSeq, userSeq);
        var postProcessQuotaChangeContext = new PostProcessQuotaChangeDto(userSeq, userSeq, deleteResult.occurredDatetime());
        postProcessingOrchestratorRetry.processQuotaDecreaseAndNotify(postProcessQuotaChangeContext);
        publisher.publishEvent(new ResourceDeletedEvent(List.of(deleteResult.deleteResourceSaveName())));
    }

    /** {@link #deleteVersion} 과 같은 트랜잭션·재시도·잠금 순서(사용자 → 리소스 → 버전) */
    @ApiRetryable
    @Transactional
    public void deleteAllVersions(Long resourceSeq, int userSeq) {
        userService.lockUserBySeq(userSeq);
        ResourceDeleteResult result = resourceService.deleteAllVersions(resourceSeq, userSeq);
        userService.decreaseUseQuotaByte(result.totalSizeByte(), userSeq);
        var postProcessQuotaChangeContext = new PostProcessQuotaChangeDto(userSeq, userSeq, result.occurredDatetime());
        postProcessingOrchestratorRetry.processQuotaDecreaseAndNotify(postProcessQuotaChangeContext);
        publisher.publishEvent(new ResourceDeletedEvent(result.deleteKeys()));
    }

}

package com.thinkfree.drive.application.trash;

import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.trash.command.TrashListCommand;
import com.thinkfree.drive.application.trash.converter.TrashCommandConverter;
import com.thinkfree.drive.application.trash.converter.TrashInfoConverter;
import com.thinkfree.drive.application.trash.info.TrashListInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.cleanup.resource.service.ResourceCleanUpService;
import com.thinkfree.drive.cleanup.trash.service.TrashCleanUpService;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessTrashDto;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.converter.TrashToResourceConverter;
import com.thinkfree.drive.resource.data.ResourcePathData;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourcePathQuery;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.trash.converter.ResourceToTrashConverter;
import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.enums.TrashSortField;
import com.thinkfree.drive.trash.exception.TrashErrorCode;
import com.thinkfree.drive.trash.service.TrashFindService;
import com.thinkfree.drive.trash.service.TrashService;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진입점(내부 API·외부 API·MCP) 공용 휴지통 usecase — 휴지통 수명주기(입고 moveToTrash/복원 restore/
 * 소멸 deletePermanently/비우기 emptyTrash)를 한곳에 모은다. 삭제(휴지통 이동)는 restore의 역연산이라
 * File/FolderUseCase가 아닌 여기 소속 (updateIsDelete+trash save ↔ restore+trash delete).
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 삭제/복원/완전삭제는 웹에서는 파일·폴더·휴지통 화면이 종류별 메서드로 호출하고, MCP에서는 파일/폴더 통합 툴이
 * 통합 메서드(moveToTrash/restore/deletePermanently)를 직접 호출한다. 비우기는 웹 전용(툴 미제공).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashUseCase {

    private final TrashCommandConverter trashCommandConverter;
    private final TrashInfoConverter trashInfoConverter;
    private final TrashToResourceConverter trashToResourceConverter;
    private final ResourceToTrashConverter resourceToTrashConverter;
    private final ResourceAuditService resourceAuditService;
    private final TrashFindService trashFindService;
    private final TrashService trashService;
    private final ResourceService resourceService;
    private final ResourceFindService resourceFindService;
    private final ResourceConverter resourceConverter;
    private final UserService userService;
    private final ResourceCleanUpService resourceCleanupService;
    private final TrashCleanUpService trashCleanUpService;
    private final ApplicationEventPublisher publisher;
    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;

    public CursorPageInfo<TrashListInfo> findAllPaginated(TrashListCommand command, int userSeq) {
        var query = trashCommandConverter.toListQuery(command, userSeq);
        var list = trashFindService.findAll(query);

        // SQL은 sortBy·sortOrder 미지정 시 삭제일(REGISTER_DATE) 내림차순 — 커서 값 추출도 같은 기본값을 따른다
        TrashSortField sortField = command.sortBy() != null ? command.sortBy() : TrashSortField.REGISTER;
        return CursorPageInfo.of(trashInfoConverter.toListInfos(list), list, command.pageSize(),
                sortField.getExtractor()::apply, TrashData::getResourceSeq, TrashData::getResourceType);
    }

    /**
     * 진입점이 리소스 종류를 알 필요 없는 통합 삭제(휴지통 이동) (MCP 툴 등).
     * 웹은 프런트가 종류별 엔드포인트를 고르지만, 여기서는 미삭제(isDelete=false) 리소스를 잠그며 읽어 resourceType을 파생한다.
     * 잠김 검사도 여기서 한다({@link ResourceService#lockActiveUnlocked}) — 진입점(웹·MCP)이 따로 검사하지 않는다.
     */
    @Transactional
    @AuditLog
    public void moveToTrash(Long resourceSeq, int userSeq) {

        // 본인 스코프(USER_SEQ=요청자) + 미삭제 한정 — 없으면(남의 것이거나 이미 삭제) RESOURCE_005
        var resource = resourceService.lockActiveUnlocked(resourceSeq, userSeq, null);

        if (resource.getResourceType() == ResourceType.FILE) {
            moveFileToTrashInternal(resource, userSeq);
        } else {
            moveFolderToTrashInternal(resource, userSeq);
        }
    }

    @Transactional
    @AuditLog
    public void moveFileToTrash(Long resourceSeq, int userSeq) {
        // 웹 파일 삭제 계약 유지 — FILE 스코프라 폴더 seq면 RESOURCE_005 (구 FileUseCase.delete 동형)
        var resource = resourceService.lockActiveUnlocked(resourceSeq, userSeq, ResourceType.FILE);
        moveFileToTrashInternal(resource, userSeq);
    }

    @Transactional
    @AuditLog
    public void moveFolderToTrash(Long resourceSeq, int userSeq) {
        // 웹 폴더 삭제 계약 유지 — FOLDER 스코프라 파일 seq면 RESOURCE_005 (구 FolderUseCase.delete 동형)
        var resource = resourceService.lockActiveUnlocked(resourceSeq, userSeq, ResourceType.FOLDER);
        moveFolderToTrashInternal(resource, userSeq);
    }

    /**
     * Direct permanent deletion for clients without a Trash UI. One public proxied method owns
     * the transaction: calling the two old HTTP endpoints would commit Trash movement before
     * permanent deletion and could strand an item on failure. Private helpers deliberately join
     * this transaction; do not replace this with controller-to-controller calls or REQUIRES_NEW.
     * Metadata, versions, quota, related records and cleanup jobs either commit together or roll
     * back together. Physical S3 deletion happens only after commit via the storage deletion queue
     * (ResourceDeletedEvent → tb_storage_deletion, registered BEFORE_COMMIT in this transaction).
     * Locking and the is_locked check happen first in {@link ResourceService#lockActiveUnlocked} (same flow as moving to
     * Trash): the target and, for a folder, its subtree are row-locked before the check, so nothing
     * can lock or delete them between the check and the deletion.
     * Tenant membership is enforced by API authentication (user/API key bound to the request tenant).
     */
    @Transactional
    @AuditLog
    public void deleteActivePermanently(Long resourceSeq, int userSeq, String userName, ResourceType type) {
        // 잠금 순서 1 — 소유자 사용자 행(업로드·이름 변경·이동·복원·버전 삭제와 같게). 아래 사용량 감소가 리소스 행 뒤에 사용자 행을 잡아 복원·이름 변경과 순환하던 것 방지
        userService.lockUserBySeq(userSeq);
        var resource = resourceService.lockActiveUnlocked(resourceSeq, userSeq, type);
        if (resource.getResourceType() == ResourceType.FOLDER) {
            moveFolderToTrashInternal(resource, userSeq);
            deleteFolderInternal(resourceSeq, userSeq, userName);
        } else {
            moveFileToTrashInternal(resource, userSeq);
            deleteFileInternal(resourceSeq, userSeq, userName);
        }
    }


    // moveToTrash(통합)와 moveFileToTrash/moveFolderToTrash(웹)가 본문을 공유 — 조회된 entity를 받아 이중 조회가 없고,
    // 같은 클래스 내 public 자기호출의 프록시 우회 문제도 애노테이션 없는 private이라 없다
    private void moveFileToTrashInternal(ResourceDetailData resource, int userSeq) {
        log.info("[File Delete Start] resourceSeq : {}", resource.getResourceSeq());

        resourceAuditService.record(EventName.FILE_DELETE, resource, userSeq);

        resourceService.updateIsDeleteByFile(resource.getResourceSeq(), userSeq);

        var trashSaveDto = resourceToTrashConverter.toSaveDto(resource);
        trashService.save(trashSaveDto);
        log.info("[File Delete Success] resourceSeq : {}", resource.getResourceSeq());
    }

    private void moveFolderToTrashInternal(ResourceDetailData entity, int userSeq) {
        log.info("[Folder Delete Start] resourceSeq : {}", entity.getResourceSeq());

        resourceAuditService.record(EventName.FOLDER_DELETE, entity, userSeq);

        var resourcePathQuery = new ResourcePathQuery(entity.getUserSeq(), entity.getFullPathSeq(), false);
        var resourcePathDataList = resourceFindService.findFolderAndDescendants(resourcePathQuery);
        var pinnedFolderDeleteResourceSeqList = resourcePathDataList.stream().map(ResourcePathData::getResourceSeq).toList();

        resourceCleanupService.deletePinnedFolders(entity.getUserSeq(), pinnedFolderDeleteResourceSeqList);

        var deleteStateQuery = trashCommandConverter.toFolderDeleteStateQuery(entity.getResourceSeq(), entity.getFullPathSeq(), userSeq);
        resourceService.updateIsDeleteByFullPathSeq(deleteStateQuery);

        var trashSaveDto = resourceToTrashConverter.toSaveDto(entity);
        trashService.save(trashSaveDto);

        log.info("[Folder Delete Success] resourceSeq : {}", entity.getResourceSeq());
    }

    /**
     * 진입점이 리소스 종류·복원 위치를 알 필요 없는 통합 복원 (MCP 툴 등).
     * 웹은 프런트가 종류별 엔드포인트를 고르고 부모 폴더 생존 여부(pe)를 선조회해 보내지만,
     * 여기서는 휴지통 항목 조회로 resourceType을, 원 부모 폴더 생존 확인으로 parentExists를 파생한다
     * (부모가 삭제됐으면 루트로 복원). 조회~복원이 한 트랜잭션이라 판별-복원 사이 레이스가 없다.
     */
    @Transactional
    @AuditLog
    public void restore(Long resourceSeq, int userSeq) {

        // 본인 스코프(USER_SEQ=요청자) + 삭제 상태 한정 — 휴지통에 없으면 RESOURCE_005
        var findQuery = resourceConverter.toFindQuery(resourceSeq, userSeq, null, true);
        var resource = resourceFindService.findDetail(findQuery);

        boolean parentExists = resource.getParentFolderSeq() != null
                && resourceFindService.existsByResource(
                        resourceConverter.toFindQuery(resource.getParentFolderSeq(), userSeq, ResourceType.FOLDER, false));

        if (resource.getResourceType() == ResourceType.FILE) {
            restoreFileInternal(resourceSeq, parentExists, userSeq);
        } else {
            restoreFolderInternal(resourceSeq, parentExists, userSeq);
        }
    }

    @Transactional
    @AuditLog
    public void restoreFile(Long resourceSeq, boolean parentExists, int userSeq) {
        restoreFileInternal(resourceSeq, parentExists, userSeq);
    }

    @Transactional
    @AuditLog
    public void restoreFolder(Long resourceSeq, boolean parentExists, int userSeq) {
        restoreFolderInternal(resourceSeq, parentExists, userSeq);
    }

    // restore(통합)와 restoreFile/Folder(웹)가 본문을 공유 — 같은 클래스 내 public 호출은 프록시를
    // 우회해 @Transactional·@AuditLog가 안 걸리므로, 애노테이션 없는 private으로 내려 공유한다
    private void restoreFileInternal(Long resourceSeq, boolean parentExists, int userSeq) {

        var resourceRestoreDto = trashToResourceConverter.toRestoreDto(resourceSeq, userSeq, ResourceType.FILE, parentExists);
        resourceService.restoreFile(resourceRestoreDto);

        var toDeleteQuery = trashCommandConverter.toDeleteQuery(resourceSeq, userSeq);
        trashService.delete(toDeleteQuery);
    }

    private void restoreFolderInternal(Long resourceSeq, boolean parentExists, int userSeq) {

        var resourceRestoreDto = trashToResourceConverter.toRestoreDto(resourceSeq, userSeq, ResourceType.FOLDER, parentExists);
        resourceService.restoreFolder(resourceRestoreDto);

        var toDeleteQuery = trashCommandConverter.toDeleteQuery(resourceSeq, userSeq);
        trashService.delete(toDeleteQuery);
    }

    /**
     * 진입점이 리소스 종류를 알 필요 없는 통합 완전삭제 (MCP 툴 등).
     * 웹은 프런트가 종류별 엔드포인트를 고르지만, 여기서는 휴지통 항목 조회로 resourceType을 파생한다.
     * 삭제 상태(isDelete=true) 한정 조회가 살아있는 리소스 오삭제를 차단하며,
     * 조회~삭제가 한 트랜잭션이라 판별-삭제 사이 레이스가 없다.
     */
    @Transactional
    @AuditLog
    public void deletePermanently(Long resourceSeq, int userSeq, String userName) {

        // 본인 스코프(USER_SEQ=요청자) + 삭제 상태 한정 — 휴지통에 없으면 RESOURCE_005
        var findQuery = resourceConverter.toFindQuery(resourceSeq, userSeq, null, true);
        var resource = resourceFindService.findDetail(findQuery);

        if (resource.getResourceType() == ResourceType.FILE) {
            deleteFileInternal(resourceSeq, userSeq, userName);
        } else {
            deleteFolderInternal(resourceSeq, userSeq, userName);
        }
    }

    @Transactional
    @AuditLog
    public void deleteFile(Long resourceSeq, int userSeq, String userName) {
        deleteFileInternal(resourceSeq, userSeq, userName);
    }

    @Transactional
    @AuditLog
    public void deleteFolder(Long resourceSeq, int userSeq, String userName) {
        deleteFolderInternal(resourceSeq, userSeq, userName);
    }

    // deletePermanently(통합)와 deleteFile/Folder(웹)가 본문을 공유 — 같은 클래스 내 public 호출은 프록시를
    // 우회해 @Transactional·@AuditLog가 안 걸리므로, 애노테이션 없는 private으로 내려 공유한다
    private void deleteFileInternal(Long resourceSeq, int userSeq, String userName) {
        // 잠금 순서 1 — 소유자 사용자 행(업로드·이름 변경·이동·복원·버전 삭제와 같게). 아래 사용량 감소가 리소스 행 뒤에 사용자 행을 잡아 복원·이름 변경과 순환하던 것 방지
        userService.lockUserBySeq(userSeq);

        var result = resourceService.deleteResourceAndVersions(resourceSeq, userSeq);

        userService.decreaseUseQuotaByte(result.totalSizeByte(), userSeq);

        var toDeleteQuery = trashCommandConverter.toDeleteQuery(resourceSeq, userSeq);
        trashService.delete(toDeleteQuery);

        for (var brief : result.resourceBriefs()) {
            resourceAuditService.recordPermanentDelete(EventName.FILE_PERMANENT_DELETE, brief.resourceSeq(), brief.resourceName());
        }

        var postProcessTrashDto = new PostProcessTrashDto(userSeq, userName, result.resourceBriefs(), ResourceType.FILE, userSeq, result.occurredDatetime(), TenantContext.getOrThrow());
        postProcessingOrchestratorRetry.processTrashAndNotify(postProcessTrashDto);

        resourceCleanupService.deleteResourceCascade(resourceSeq, userSeq);

        publisher.publishEvent(new ResourceDeletedEvent(result.deleteKeys()));
    }

    private void deleteFolderInternal(Long resourceSeq, int userSeq, String userName) {
        // 잠금 순서 1 — 소유자 사용자 행(업로드·이름 변경·이동·복원·버전 삭제와 같게). 아래 사용량 감소가 리소스 행 뒤에 사용자 행을 잡아 복원·이름 변경과 순환하던 것 방지
        userService.lockUserBySeq(userSeq);

        var result = resourceService.deleteResourcesAndVersions(resourceSeq, userSeq);

        if(result == null){
            throw ApiRuntimeException.type(TrashErrorCode.DELETE_FAIL);
        }

        userService.decreaseUseQuotaByte(result.totalSizeByte(), userSeq);

        var toDeleteQuery = trashCommandConverter.toDeleteQuery(resourceSeq, userSeq);
        trashService.delete(toDeleteQuery);

        var postProcessTrashDto = new PostProcessTrashDto(userSeq, userName, result.resourceBriefs(), ResourceType.FOLDER, userSeq, result.occurredDatetime(), TenantContext.getOrThrow());
        postProcessingOrchestratorRetry.processTrashAndNotify(postProcessTrashDto);

        resourceCleanupService.deleteResourcesCascade(result.deleteResourceSeqList(), userSeq);

        publisher.publishEvent(new ResourceDeletedEvent(result.deleteKeys()));

    }

    @Transactional
    @AuditLog
    public void emptyTrash(int userSeq) {
        trashCleanUpService.cleanUpResourcesByUser(userSeq);
    }

}

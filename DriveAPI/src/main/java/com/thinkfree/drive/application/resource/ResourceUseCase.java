package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.resource.command.ResourceExistsCommand;
import com.thinkfree.drive.application.resource.command.ResourceFindCommand;
import com.thinkfree.drive.application.resource.command.ResourceMoveCommand;
import com.thinkfree.drive.application.resource.command.ResourceRenameCommand;
import com.thinkfree.drive.application.resource.command.ResourceSearchCommand;
import com.thinkfree.drive.application.resource.converter.ResourceInfoConverter;
import com.thinkfree.drive.application.resource.converter.ResourceCommandConverter;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.postprocess.enums.MoveContextType;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessMoveDto;
import com.thinkfree.drive.application.resource.info.ResourceDetailInfo;
import com.thinkfree.drive.application.resource.info.ResourceExistsInfo;
import com.thinkfree.drive.application.resource.info.ResourceListInfo;
import com.thinkfree.drive.application.resource.info.ResourceRenameInfo;
import com.thinkfree.drive.application.resource.info.ResourceShareLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceTreeInfo;
import com.thinkfree.drive.permission.service.PermissionAggregationService;
import com.thinkfree.drive.resource.data.ResourceData;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.enums.ResourceSearchType;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.global.data.ResourceGlobalListData;
import com.thinkfree.drive.resource.global.service.impl.DefaultResourceGlobalFindService;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.share.dto.ShareMoveImpactDto;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.resource.share.data.ResourceSharedListData;
import com.thinkfree.drive.resource.share.service.ResourceShareFindService;
import com.thinkfree.drive.resource.share.service.ResourceShareLinkService;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 진입점(내부 API·외부 API·MCP) 공용 리소스 usecase.
 * 이동(move)·이름변경(rename)은 타입 무관한 리소스 문제라 File/FolderUseCase가 아닌 여기 소속 —
 * 웹은 종류별 메서드(moveFile/moveFolder·renameFile/renameFolder)를, MCP는 파일/폴더 통합 툴이 통합 메서드(move·rename)를 직접 호출한다.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 다운로드(zip 스트림·토큰 발급)는 같은 패키지의 ResourceDownloadUseCase가 담당한다.
 * 권한 검사는 어댑터 책임 — 웹은 controller aspect(@CheckResourcePermission), MCP는 툴의 명시 호출.
 * 잠김 검사는 이동·이름 변경·휴지통 이동에서 도메인(ResourceService)이 행 잠금 뒤 같은 트랜잭션에서 한다. move의 CustomUserDetails는 (userSeq, userName)으로 해체해 받는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceUseCase {

    private final ResourceCommandConverter resourceCommandConverter;
    private final ResourceInfoConverter resourceInfoConverter;
    private final ResourceFindService resourceFindService;
    private final ResourceService resourceService;
    private final ResourceShareFindService resourceShareFindService;
    private final DefaultResourceGlobalFindService resourceGlobalFindService;
    private final PermissionAggregationService permissionAggregationService;
    private final ResourceLockUnlockService resourceLockUnlockService;
    private final ResourceShareLinkService resourceShareLinkService;
    private final ShareService shareService;
    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    private final UserService userService;

    @Transactional(readOnly = true)
    public CursorPageInfo<ResourceListInfo> search(ResourceSearchCommand command, int requestUserSeq) {

        boolean isMyDrive = ResourceSearchType.MY == command.searchType();
        ResourceSortField sortField = command.sortBy() != null ? command.sortBy() : ResourceSortField.NAME;

        if (isMyDrive) {

            var userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
            var query = resourceCommandConverter.toListQuery(command, userSeq);
            boolean isOwner = ResourceUtil.isOwner(query.getUserSeq(), requestUserSeq);

            if (isOwner) {
                var list = resourceFindService.findAll(query);
                return CursorPageInfo.of(resourceInfoConverter.toListInfos(list), list, command.pageSize(),
                        sortField.getExtractor()::apply, ResourceData::getResourceSeq, ResourceData::getResourceType);
            }

            var list = resourceShareFindService.findAll(requestUserSeq, query);
            return CursorPageInfo.of(resourceInfoConverter.toListInfosFromShared(list), list, command.pageSize(),
                    sortField.getExtractor()::apply, ResourceSharedListData::getResourceSeq, ResourceSharedListData::getResourceType);

        }

        var query = resourceCommandConverter.toListQuery(command, requestUserSeq);
        var list = resourceGlobalFindService.findAll(requestUserSeq, query);
        return CursorPageInfo.of(resourceInfoConverter.toListInfosFromGlobal(list), list, command.pageSize(),
                sortField.getExtractor()::apply, ResourceGlobalListData::getResourceSeq, ResourceGlobalListData::getResourceType);

    }

    public ResourceExistsInfo exists(ResourceExistsCommand command, int requestUserSeq) {
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var query = resourceCommandConverter.toExistsQuery(command, userSeq);
        var resource = resourceFindService.findResourceByExists(query);
        return resourceInfoConverter.toExistsInfo(resource);
    }

    public ResourceDetailInfo findById(ResourceFindCommand command, int requestUserSeq) {
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var isDelete = ObjectUtils.getIfNull(command.isDelete(), false);
        var query = resourceCommandConverter.toFindQuery(command.resourceSeq(), userSeq, null, isDelete);
        var entity = resourceFindService.findByIdAndIsDelete(query);
        return permissionAggregationService.mergeResourcePermissionWithFarthest(requestUserSeq, entity, isDelete, resourceInfoConverter::toDetailInfo);
    }

    public List<ResourceTreeInfo> findResourceTree(int userSeq) {
        var resourceFindQuery = resourceCommandConverter.toFindQuery(null, userSeq, ResourceType.FOLDER, false);
        var resourceTrees = resourceFindService.findResourceTree(resourceFindQuery);
        return resourceInfoConverter.toTreeInfos(resourceTrees);
    }

    /**
     * 진입점이 리소스 종류를 알 필요 없는 통합 이동 (MCP 툴 등).
     * 웹은 프런트가 종류별 엔드포인트를 고르지만, 여기서는 미삭제(isDelete=false) 리소스 조회로 resourceType을 파생한다
     * — 스코프는 (ss ?? 요청자) 소유자 기준, 없으면(스코프 밖이거나 이미 휴지통) RESOURCE_005.
     * 조회~이동이 한 트랜잭션이라 판별-이동 사이 레이스가 없다.
     * (본문의 도메인 서비스(fileMove/folderMove)가 타입 스코프 조회를 자체 수행하므로 파생 조회와 이중이지만,
     * 웹 경로 계약 불변 원칙상 도메인 조회를 걷어내지 않는다 — 파생 조회는 통합 진입점만 지불하는 비용)
     * 권한 검사는 어댑터 책임 — 웹은 controller aspect, MCP는 툴의 명시 호출. 잠김 검사는 ResourceService.fileMove/folderMove 가 행 잠금 뒤에.
     */
    @Transactional
    @AuditLog
    public void move(Long resourceSeq, ResourceMoveCommand command, int requestUserSeq, String requestUserName) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var findQuery = resourceCommandConverter.toFindQuery(resourceSeq, userSeq, null, false);
        var resource = resourceFindService.findDetail(findQuery);

        if (resource.getResourceType() == ResourceType.FILE) {
            moveFileInternal(resourceSeq, command, requestUserSeq, requestUserName);
        } else {
            moveFolderInternal(resourceSeq, command, requestUserSeq, requestUserName);
        }
    }

    @Transactional
    @AuditLog
    public void moveFile(Long resourceSeq, ResourceMoveCommand command, int requestUserSeq, String requestUserName) {
        // 웹 파일 이동 계약 유지 — 본문(fileMove)이 FILE 스코프 조회라 폴더 seq면 RESOURCE_005 (구 FileUseCase.move 동형)
        moveFileInternal(resourceSeq, command, requestUserSeq, requestUserName);
    }

    @Transactional
    public void moveFolder(Long resourceSeq, ResourceMoveCommand command, int requestUserSeq, String requestUserName) {
        // 웹 폴더 이동 계약 유지 — 본문(folderMove)이 FOLDER 스코프 조회라 파일 seq면 RESOURCE_005 (구 FolderUseCase.move 동형).
        // @AuditLog 부재도 웹 시절 그대로 — FOLDER_MOVE는 감사 EventName 자체가 미정의(파일만 FILE_MOVE를 도메인이 적재)
        moveFolderInternal(resourceSeq, command, requestUserSeq, requestUserName);
    }

    // move(통합)와 moveFile/moveFolder(웹)가 본문을 공유 — 같은 클래스 내 public 자기호출은 프록시를
    // 우회해 @Transactional·@AuditLog가 안 걸리므로, 애노테이션 없는 private으로 내려 공유한다
    private void moveFileInternal(Long resourceSeq, ResourceMoveCommand command, int requestUserSeq, String requestUserName) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        int targetUserSeq = MapStructUtil.resolveUserSeq(command.targetUserSeq(), requestUserSeq);
        lockOwnersIfTransfer(userSeq, targetUserSeq);
        var destinationResourceSeq = command.parentFolderSeq();
        var dto = resourceCommandConverter.toFileMoveDto(resourceSeq, destinationResourceSeq, userSeq, targetUserSeq);

        var shareMoveImpactDto = new ShareMoveImpactDto(userSeq, resourceSeq, targetUserSeq, destinationResourceSeq, ResourceType.FILE, false);
        var shareMoveImpactResult = shareService.evaluateMoveShareImpact(shareMoveImpactDto);

        var moveResult = resourceService.fileMove(dto);

        MoveContextType moveContextType = moveResult.userChanged() ? MoveContextType.CROSS_USER : MoveContextType.INTRA_USER;
        var postProcessMoveDto = new PostProcessMoveDto(userSeq, targetUserSeq, requestUserSeq, requestUserName, moveResult.fileName(),
                    List.of(resourceSeq), moveResult.occurredDatetime(), moveResult.totalSizeByte(), moveContextType, shareMoveImpactResult.shareCleanupAction(), ResourceType.FILE, TenantContext.getOrThrow());

        postProcessingOrchestratorRetry.processMoveAndNotify(postProcessMoveDto);

    }

    /**
     * 소유자가 바뀌는 이동은 두 사용자 행을 리소스보다 먼저(user_seq 오름차순) 잠근다 — 업로드·버전 저장·버전 삭제와 같은 사용자 → 리소스 순서(10-14).
     * 구는 리소스 행(첫 로드 잠금) → 사용자 행(후처리 moveQuota) 순이라 같은 파일의 버전 저장과 교착할 수 있었다. 뒤의 사용량 이전은 같은 tx 라 대기 없음.
     */
    private void lockOwnersIfTransfer(int userSeq, int targetUserSeq) {
        if (userSeq != targetUserSeq) {
            userService.lockUsersInSeqOrder(List.of(userSeq, targetUserSeq));
        }
    }

    private void moveFolderInternal(Long resourceSeq, ResourceMoveCommand command, int requestUserSeq, String requestUserName) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        int targetUserSeq = MapStructUtil.resolveUserSeq(command.targetUserSeq(), requestUserSeq);
        lockOwnersIfTransfer(userSeq, targetUserSeq);
        var destinationResourceSeq = command.parentFolderSeq();

        var dto = resourceCommandConverter.toFolderMoveDto(resourceSeq, destinationResourceSeq, userSeq, targetUserSeq);

        var shareMoveImpactDto = new ShareMoveImpactDto(userSeq, resourceSeq, targetUserSeq, destinationResourceSeq, ResourceType.FOLDER, false);
        var shareMoveImpactResult = shareService.evaluateMoveShareImpact(shareMoveImpactDto);

        var moveResult = resourceService.folderMove(dto);

        MoveContextType moveContextType = moveResult.userChanged() ? MoveContextType.CROSS_USER : MoveContextType.INTRA_USER;

        var postProcessMoveDto = new PostProcessMoveDto(userSeq, targetUserSeq, requestUserSeq, requestUserName, null,
                    List.copyOf(moveResult.resourceSeqList()), moveResult.occurredDatetime(), moveResult.totalSizeByte(), moveContextType, shareMoveImpactResult.shareCleanupAction(), ResourceType.FOLDER, TenantContext.getOrThrow());

        postProcessingOrchestratorRetry.processMoveAndNotify(postProcessMoveDto);

    }

    /**
     * 진입점이 리소스 종류를 알 필요 없는 통합 이름변경 (MCP 툴 등).
     * 미삭제(isDelete=false) 리소스 조회로 resourceType을 파생한다 — 스코프는 (ss ?? 요청자) 소유자 기준,
     * 없으면(스코프 밖이거나 이미 휴지통) RESOURCE_005. 조회~변경이 한 트랜잭션이라 판별-변경 사이 레이스가 없다.
     * 커맨드는 웹과 동일하게 그대로 전달한다(move 동형) — 이름 중복 검사 위치는 pfs 가 아니라 잠근 행의 실제 부모,
     * ncp는 파일 경로만 읽는다(폴더 경로는 웹 요청에 필드 자체가 없는 것과 동형으로 무시).
     * 권한 검사는 어댑터 책임 — 웹은 controller aspect, MCP는 툴의 명시 호출. 잠김 검사는 파일이면 ResourceService.resolveName 이 행 잠금 뒤에(ncp=AUTO 스킵), 폴더는 없음.
     */
    @Transactional
    @AuditLog
    public ResourceRenameInfo rename(Long resourceSeq, ResourceRenameCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var findQuery = resourceCommandConverter.toFindQuery(resourceSeq, userSeq, null, false);
        var resource = resourceFindService.findDetail(findQuery);

        if (resource.getResourceType() == ResourceType.FILE) {
            return renameFileInternal(resourceSeq, command, requestUserSeq, originalExtension(resource));
        }
        return renameFolderInternal(resourceSeq, command, requestUserSeq);
    }

    @AuditLog
    public ResourceRenameInfo renameFile(Long resourceSeq, ResourceRenameCommand command, int requestUserSeq) {
        // 웹 파일 이름변경 계약 유지 — 본문(resolveName)이 FILE 스코프 조회라 폴더 seq면 RESOURCE_005,
        // ncp도 요청 값 그대로(NONE/AUTO 분기 포함). @Transactional 부재도 구 FileUseCase.rename 동형
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        // 기존 확장자 조회 — 확장자는 생성 뒤 바뀌지 않아 잠금 전에 읽어도 된다. 스코프는 resolveName 의 잠금 조회와 같다
        var resource = resourceFindService.findDetail(resourceCommandConverter.toFindQuery(resourceSeq, userSeq, null, false));
        return renameFileInternal(resourceSeq, command, requestUserSeq, originalExtension(resource));
    }

    @Transactional
    @AuditLog
    public void renameFolder(Long resourceSeq, ResourceRenameCommand command, int requestUserSeq) {
        // 웹 폴더 이름변경 계약 유지 — 본문이 FOLDER 스코프 조회라 파일 seq면 RESOURCE_005, 응답 본문 없음(구 FolderUseCase.rename 동형)
        renameFolderInternal(resourceSeq, command, requestUserSeq);
    }

    // rename(통합)과 renameFile/renameFolder(웹)가 본문을 공유 — 같은 클래스 내 public 자기호출은 프록시를
    // 우회해 @Transactional·@AuditLog가 안 걸리므로, 애노테이션 없는 private으로 내려 공유한다
    private ResourceRenameInfo renameFileInternal(Long resourceSeq, ResourceRenameCommand command, int requestUserSeq, String originalExt) {
        validateRenameName(command.newResourceName());
        var keptCommand = command.withNewResourceName(keepOriginalExtension(command.newResourceName(), originalExt));
        int userSeq = MapStructUtil.resolveUserSeq(keptCommand.sharedByUserSeq(), requestUserSeq);
        var dto = resourceCommandConverter.toFileRenameDto(resourceSeq, keptCommand, userSeq);
        var result = resourceService.resolveName(dto);
        return new ResourceRenameInfo(result.name(), result.fullName());
    }

    private ResourceRenameInfo renameFolderInternal(Long resourceSeq, ResourceRenameCommand command, int requestUserSeq) {
        validateRenameName(command.newResourceName());
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var dto = resourceCommandConverter.toFolderRenameDto(resourceSeq, command, userSeq);
        // 이름 중복 검사는 행 잠금 뒤 실제 부모 기준으로 folderRenameWithChildFolders 가 한다
        resourceService.folderRenameWithChildFolders(dto);
        // 폴더는 자동 변번이 없어(중복=에러) 확정 이름 = 요청 이름(dto.resourceName) — 웹은 버리고 통합 진입점만 쓴다.
        // 폴더명은 확장자 분리 대상이 아니라("v1.2" 같은 이름) name·fullName 모두 요청 이름 그대로
        return new ResourceRenameInfo(dto.getResourceName(), dto.getResourceName());
    }

    /** 이름 규칙 검사 — {@link ResourceNameRule#validate} */
    static void validateRenameName(String name) {
        ResourceNameRule.validate(name);
    }

    /** 기존 확장자 유지 — 요청 이름이 기존 확장자로 끝나면 한 번만 떼고 기존 확장자를 붙인다. 기존 확장자가 없으면 요청 이름 그대로 */
    static String keepOriginalExtension(String name, String originalExt) {
        if (!StringUtils.hasText(originalExt)) {
            return name;
        }
        String baseName = ResourceUtil.stripExtension(name, originalExt);
        ResourceNameRule.validateBaseName(baseName);
        String fullName = baseName + "." + originalExt;
        if (fullName.length() > ResourceNameRule.MAX_LENGTH) {
            throw ApiRuntimeException.type(ResourceErrorCode.INVALID_NAME);
        }
        return fullName;
    }

    /** 파일의 기존 확장자 — 컬럼 값이 없으면 현재 이름에서 */
    private static String originalExtension(ResourceDetailData resource) {
        return resource.getExt() != null ? resource.getExt() : ResourceUtil.getFileExtension(resource.getResourceName());
    }

    public void lock(long resourceSeq, Integer sharedByUserSeq, int requestUserSeq) {
        var userSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
        resourceLockUnlockService.lockResource(resourceSeq, userSeq, requestUserSeq, Instant.now());
    }

    public void unlock(long resourceSeq, Integer sharedByUserSeq, int requestUserSeq) {
        var userSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
        resourceLockUnlockService.unlockResource(resourceSeq, userSeq, requestUserSeq, Instant.now());
    }

    /**
     * 공유 링크 복사. 공유 등록 여부와 무관하게 호출 가능하며, 토큰이 없으면 발급(get-or-create)한다.
     * 링크 접근 시 권한은 해당 리소스의 공유 존재/유형으로 재판정된다.
     */
    public ResourceShareLinkInfo copyShareLink(long resourceSeq, Integer sharedByUserSeq, int requestUserSeq) {
        var ownerUserSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
        var linkUrl = resourceShareLinkService.getOrCreateLinkUrl(ownerUserSeq, resourceSeq);
        return new ResourceShareLinkInfo(linkUrl);
    }

}

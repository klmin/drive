package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.version.VersionUseCase;
import com.thinkfree.drive.application.version.command.VersionListCommand;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.VersionToolConverter;
import com.thinkfree.drive.mcp.tool.response.VersionListToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 리소스 버전 MCP 툴 — application.version.VersionUseCase를 내부 API와 동일하게 호출한다.
 *
 * 웹 controller의 @CheckResourcePermission aspect는 MCP 경로에 적용되지 않으므로
 * 동일한 권한 검사(목록: 대상 리소스 VIEW / 복원: 대상 리소스 EDIT)를 툴 본문에서 명시 호출한다.
 * 복원의 잠김 검사는 버전 저장(ResourceService.updateWithVersioning)이 행 잠금 뒤 같은 트랜잭션에서 한다 — 웹과 같은 흐름.
 * 버전 삭제는 웹도 권한 aspect 없이 도메인의 소유자 스코프 조회(USER_SEQ 일치)로 강제하므로 툴도 명시 검사 없이 동형 유지.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveVersionTools {

    private final VersionUseCase versionUseCase;
    private final ResourcePermissionService resourcePermissionService;
    private final VersionToolConverter versionToolConverter;

    @Tool(name = "drive_list_resource_versions",
            description = "파일의 버전 이력을 조회한다 — 저장/복원으로 쌓인 이전 버전들의 목록(버전 식별자 resourceVersionSeq, "
                    + "버전 번호, 크기, 그 버전을 만든 사용자(ownerSeq·ownerName·ownerStatus·ownerTenantSeq·ownerTenantName), 생성 일시). "
                    + "항목의 userSeq·userName·tenantSeq·tenantName은 만든 사용자가 아니라 그 파일이 들어 있는 드라이브의 소유자다. 대상 파일의 resourceSeq가 필요하다 — 모르면 먼저 "
                    + "drive_search_resources 등 목록 툴로 찾는다. 다른 사용자가 공유해 준 파일은 sharedByUserSeq=소유자의 "
                    + "userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 함께 넘긴다 (권한이 없으면 에러가 반환된다). "
                    + "폴더는 버전 개념이 없다. 조회 전용이며 버전 복원·삭제는 하지 않는다. "
                    + "결과는 오프셋 페이지네이션이며 totalCount가 전체 버전 수다 — 더 보려면 pageIndex를 1씩 올려 호출한다.")
    public McpToolResult<VersionListToolResponse> listResourceVersions(
            @ToolParam(description = "버전 이력을 조회할 파일의 resourceSeq") Long resourceSeq,
            @ToolParam(description = "다른 사용자가 공유해 준 파일일 때 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 파일이면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "페이지 번호 (1부터). 기본값 1", required = false) Integer pageIndex,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize) {

        log.info("[MCP] drive_list_resource_versions called. resourceSeq : {}, sharedByUserSeq : {}, pageIndex : {}, pageSize : {}",
                resourceSeq, sharedByUserSeq, pageIndex, pageSize);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 버전 목록 API의 @CheckResourcePermission(VIEW)와 동일한 검사
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.VIEW, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), actor, false);

            var command = VersionListCommand.builder()
                    .resourceSeq(resourceSeq)
                    .sharedByUserSeq(sharedByUserSeq)
                    .pageIndex(McpToolParams.normalizePageIndex(pageIndex))
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();
            return versionToolConverter.toListResponse(versionUseCase.findAllPaginated(command, requestUserSeq));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_restore_resource_version",
            description = "파일을 이전 버전으로 되돌린다(복원). 되돌릴 버전의 resourceVersionSeq가 필요하다 — "
                    + "먼저 drive_list_resource_versions로 버전 이력을 조회해 HISTORY 항목에서 고른다 "
                    + "(CURRENT 항목은 현재 본문이라 resourceVersionSeq가 null이며 복원 대상이 아니다). "
                    + "다른 사용자가 공유해 준 파일은 sharedByUserSeq=소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 "
                    + "함께 넘긴다 — 편집 권한(canEdit)이 있어야 하고, 잠긴(isLocked) 파일은 복원할 수 없다. "
                    + "복원하면 복원 직전의 현재 본문이 새 버전으로 쌓인다. 성공 판정 기준은 응답에 error 필드가 없는 것이다"
                    + "(성공 시 본문 없음) — drive_list_resource_versions 재조회는 참고용일 뿐이며, 재조회 결과가 "
                    + "그대로 보여도 실패로 판단해 복원을 반복하면 안 된다(같은 내용이 중복 적재된다). "
                    + "복원만 하며 버전 삭제는 drive_delete_resource_version을 사용한다.")
    public McpToolResult<Void> restoreResourceVersion(
            @ToolParam(description = "복원할 파일의 resourceSeq") Long resourceSeq,
            @ToolParam(description = "되돌릴 버전의 resourceVersionSeq (drive_list_resource_versions 결과 HISTORY 항목의 "
                    + "resourceVersionSeq)") Long resourceVersionSeq,
            @ToolParam(description = "다른 사용자가 공유해 준 파일일 때 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 파일이면 생략", required = false) Integer sharedByUserSeq) {

        log.info("[MCP] drive_restore_resource_version called. resourceSeq : {}, resourceVersionSeq : {}, sharedByUserSeq : {}",
                resourceSeq, resourceVersionSeq, sharedByUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");
            McpToolParams.require(resourceVersionSeq, "resourceVersionSeq");

            // 웹 버전 복원 API의 @CheckResourcePermission(EDIT)과 동일한 검사(잠김 검사는 도메인이 행 잠금 뒤에)
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.EDIT, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), actor, false);

            versionUseCase.restoreVersion(resourceSeq, resourceVersionSeq, sharedByUserSeq, requestUserSeq);
            return null; // 웹 응답(ApiResponse.created(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_delete_resource_version",
            description = "파일의 특정 버전을 영구 삭제한다 — 되돌릴 수 없으므로 사용자가 삭제할 버전을 명시적으로 지정했을 때만 "
                    + "사용한다. 삭제할 버전의 resourceVersionSeq가 필요하다 — 먼저 drive_list_resource_versions로 버전 이력을 "
                    + "조회해 HISTORY 항목에서 고른다 (CURRENT 항목은 현재 본문이라 삭제 대상이 아니다). "
                    + "파일 소유자만 삭제할 수 있다 — 다른 사용자가 공유해 준 파일에는 쓸 수 없다. "
                    + "버전만 삭제하며 파일 자체는 삭제하지 않는다. 성공 판정 기준은 응답에 error 필드가 없는 것이다"
                    + "(성공 시 본문 없음).")
    public McpToolResult<Void> deleteResourceVersion(
            @ToolParam(description = "버전을 삭제할 파일의 resourceSeq") Long resourceSeq,
            @ToolParam(description = "삭제할 버전의 resourceVersionSeq (drive_list_resource_versions 결과 HISTORY 항목의 "
                    + "resourceVersionSeq)") Long resourceVersionSeq) {

        log.info("[MCP] drive_delete_resource_version called. resourceSeq : {}, resourceVersionSeq : {}",
                resourceSeq, resourceVersionSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");
            McpToolParams.require(resourceVersionSeq, "resourceVersionSeq");

            // 웹 버전 삭제 API는 권한 aspect 없음(소유자만 가능) — 도메인이 (resourceSeq, 요청자) 스코프 조회로 강제, 동형 유지
            versionUseCase.deleteVersion(resourceSeq, resourceVersionSeq, requestUserSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

}

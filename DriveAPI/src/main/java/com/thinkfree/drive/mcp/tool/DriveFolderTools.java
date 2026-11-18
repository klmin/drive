package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.folder.FolderUseCase;
import com.thinkfree.drive.application.folder.command.FolderCreateCommand;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.FolderToolConverter;
import com.thinkfree.drive.mcp.tool.response.FolderCreateToolResponse;
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
 * 폴더 MCP 툴 — application.folder.FolderUseCase를 내부 API와 동일하게 호출한다.
 * 이름변경·이동은 DriveResourceTools, 삭제는 DriveTrashTools의 몫이다.
 *
 * 웹 controller의 @CheckResourcePermission aspect는 MCP 경로에 적용되지 않으므로 동일한 검사를
 * 툴 본문에서 명시 호출한다. 감사 로그(@AuditLog·FOLDER_CREATE)는 usecase에 있어 MCP 경로에도 동일하게 남는다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveFolderTools {

    private final FolderUseCase folderUseCase;
    private final ResourcePermissionService resourcePermissionService;
    private final FolderToolConverter folderToolConverter;

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_create_folder",
            description = "드라이브에 새 폴더를 하나 생성한다. 위치 지정 레시피: ① 내 드라이브 최상위에 만들 때는 "
                    + "folderName만 지정 ② 특정 폴더 안에 만들 때는 부모 폴더의 resourceSeq를 parentFolderSeq로 지정 — "
                    + "부모 폴더를 이름으로만 알면 먼저 drive_search_resources(searchType=ALL, keyword=폴더명, resourceType=FOLDER)로 찾고, 동명 폴더가 "
                    + "여러 개면 fullPath로 구분한다 ③ 공유받은 폴더 안에 만들 때는 parentFolderSeq와 함께 그 폴더 소유자의 "
                    + "userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 sharedByUserSeq로 지정 — 업로드 권한(canUpload)이 있어야 한다. "
                    + "같은 위치에 같은 이름의 폴더가 이미 있으면 실패하지 않고 서버가 번호를 붙인 이름으로 생성하며, "
                    + "실제 생성된 이름이 응답 resourceName으로 온다. 폴더 생성 외의 변경(이름변경·이동·삭제)은 이 툴이 하지 않는다.")
    public McpToolResult<FolderCreateToolResponse> createFolder(
            @ToolParam(description = "생성할 폴더 이름. 앞뒤 공백·마침표로 시작·끝, 255자 초과, \\ / : * ? \" < > | 문자, Windows 예약어(CON·PRN·AUX·NUL·COM1~9·LPT1~9) 불가") String folderName,
            @ToolParam(description = "폴더를 만들 부모 폴더의 resourceSeq. 내 드라이브 최상위에 만들면 생략",
                    required = false) Long parentFolderSeq,
            @ToolParam(description = "공유받은 폴더 안에 만들 때 그 폴더 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 드라이브면 생략", required = false) Integer sharedByUserSeq) {

        log.info("[MCP] drive_create_folder called. folderName : {}, parentFolderSeq : {}, sharedByUserSeq : {}",
                folderName, parentFolderSeq, sharedByUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.requireText(folderName, "folderName"); // 웹 @NotBlank(fn) 등가

            // 웹 폴더 업로드 API의 @CheckResourcePermission(UPLOAD)과 동일한 검사 — 검사 대상은 부모 폴더(pfs).
            // 내 드라이브(소유자=요청자)는 즉시 통과라 parentFolderSeq=null(최상위)도 웹과 동일하게 허용된다
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, parentFolderSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq),
                    RequestActor.of(requestUserSeq, details.isAnonymous()), false);

            var command = FolderCreateCommand.builder()
                    .folderName(folderName)
                    .parentFolderSeq(parentFolderSeq)
                    .sharedByUserSeq(sharedByUserSeq)
                    .build();
            return folderToolConverter.toCreateResponse(folderUseCase.create(command, requestUserSeq));
        });
    }

}

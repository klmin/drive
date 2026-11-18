package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.resource.ResourceUseCase;
import com.thinkfree.drive.application.resource.command.ResourceFindCommand;
import com.thinkfree.drive.application.resource.command.ResourceMoveCommand;
import com.thinkfree.drive.application.resource.command.ResourceRenameCommand;
import com.thinkfree.drive.application.resource.command.ResourceSearchCommand;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.ResourceToolConverter;
import com.thinkfree.drive.mcp.tool.response.ResourceDetailToolResponse;
import com.thinkfree.drive.mcp.tool.response.ResourceRenameToolResponse;
import com.thinkfree.drive.mcp.tool.response.ResourceSearchToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.mcp.util.McpUrls;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.enums.ResourceSearchType;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 리소스(파일/폴더) 공통 MCP 툴(검색·메타데이터·이동·이름변경) — application.resource.ResourceUseCase를 내부 API와 동일하게 호출한다.
 * 이동·이름변경은 파일/폴더 통합 툴 하나 — 종류 판별은 usecase가 조회로 파생한다(웹은 프런트가 종류별 엔드포인트 선택).
 *
 * 웹 controller의 @CheckResourcePermission aspect는 MCP 경로에 적용되지 않으므로(적용해도 @Before 예외가
 * 툴 catch 밖에서 터져 인밴드 에러 규약을 못 탄다), 동일한 권한 검사를 툴 본문에서 명시 호출한다.
 * 잠김 검사는 이동·이름 변경 모두 도메인(ResourceService)이 행 잠금 뒤 같은 트랜잭션에서 한다 — 툴·웹 모두 따로 하지 않는다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveResourceTools {

    private final ResourceUseCase resourceUseCase;
    private final ResourcePermissionService resourcePermissionService;
    private final ResourceToolConverter resourceToolConverter;
    private final ClientDomainService clientDomainService;

    @Tool(name = "drive_search_resources",
            description = "Drive에서 파일/폴더를 검색하거나 폴더 내용을 조회한다. 기본 범위는 내 드라이브(MY)다. "
                    + "MY는 폴더 내용 조회 모드로, parentFolderSeq로 지정한 폴더(생략 시 최상위) 한 단계의 항목만 반환하며 "
                    + "keyword는 적용되지 않는다(웹의 내 드라이브 화면과 동일). 이름으로 찾으려면 searchType=ALL + keyword로 호출한다 — "
                    + "ALL은 내 드라이브 전체(하위 폴더 포함)와 공유받은 리소스를 대상으로 이름 부분 일치 검색한다. "
                    + "두 범위 모두 리소스 종류·파일 형식·수정일 범위·소유자로 필터링할 수 있다. 파일 내용(본문) 검색은 지원하지 않고, "
                    + "휴지통에 있는(삭제된) 리소스는 검색되지 않는다 — 휴지통 목록은 drive_list_trash_resources를 사용한다. "
                    + "다른 사용자가 공유해 준 폴더 안을 조회하려면 searchType=MY, sharedByUserSeq=폴더 소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님), "
                    + "parentFolderSeq=그 폴더 resourceSeq로 호출한다 (권한이 없으면 에러가 반환된다). "
                    + "항목의 ext는 파일 형식(점 제외, 업로드 때 정해지고 이름 변경으로 바뀌지 않음, 폴더·확장자 없는 파일은 빈 값)이다 — 형식은 이름 끝이 아니라 ext로 판단한다. "
                    + "결과는 커서 기반 페이지네이션이며, 응답 hasNext가 true면 nextCursor/nextCursorValue/nextCursorType을 "
                    + "다음 호출의 cursorId/cursorValue/cursorType으로 그대로 넘기면 다음 페이지를 받는다. "
                    + "항목에는 webViewUrl(브라우저용 웹 링크)이 포함된다 — 파일은 미리보기, 폴더는 폴더 열람으로 이어지며 "
                    + "사용자가 직접 열람·편집·다운로드하려 할 때 이 링크를 안내한다.")
    public McpToolResult<ResourceSearchToolResponse> searchResources(
            @ToolParam(description = "공유 폴더 조회 시 그 폴더 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 드라이브 조회면 생략. searchType=ALL에서는 적용되지 않는다", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "이름 검색 키워드 (부분 일치). searchType=ALL에서만 적용되며, MY에서는 무시되어 폴더 내용 전체가 반환된다. "
                    + "searchType을 생략하면 MY로 처리되므로 keyword를 쓸 때는 반드시 searchType=ALL을 명시한다 — "
                    + "keyword만 보내면 검색이 아니라 최상위 폴더 목록이 돌아온다. 미지정 시 전체 목록", required = false) String keyword,
            @ToolParam(description = "특정 폴더 안만 조회할 때 그 폴더의 resourceSeq (searchType=MY에서만 적용, ALL에서는 무시된다). "
                    + "공유 폴더 조회(sharedByUserSeq가 본인이 아닌 사용자) 시 필수", required = false) Long parentFolderSeq,
            @ToolParam(description = "리소스 종류 필터 (파일 : FILE, 폴더 : FOLDER). 미지정 시 전체", required = false) ResourceType resourceType,
            @ToolParam(description = "검색 범위. MY(기본)=폴더 내용 조회(parentFolderSeq 한 단계, keyword 미적용), "
                    + "ALL=이름 검색(하위 폴더·공유받은 리소스 포함, keyword 적용). "
                    + "이름으로 파일/폴더를 찾는 요청이면 ALL을 사용하고, 특정 폴더 안의 목록을 보여주는 요청이면 MY를 사용한다. "
                    + "다른 사용자가 공유해 준 폴더 안 조회는 MY + sharedByUserSeq + parentFolderSeq 조합 "
                    + "(ALL에서는 sharedByUserSeq가 무시된다)", required = false) ResourceSearchType searchType,
            @ToolParam(description = "소유자 사용자 ID(이메일) 필터", required = false) String ownerId,
            @ToolParam(description = "수정일 검색 시작일 (yyyy-MM-dd, 해당 일 포함). endDate와 함께 지정해야 필터가 적용된다", required = false) String startDate,
            @ToolParam(description = "수정일 검색 종료일 (yyyy-MM-dd, 해당 일 포함). startDate와 함께 지정해야 필터가 적용된다", required = false) String endDate,
            @ToolParam(description = "파일 형식 필터: DOCUMENT(doc, docx, hwp, hwpx, txt, rtf, odt, md, pages), "
                    + "SPREADSHEET(xls, xlsx, csv, tsv, ods, numbers), PRESENTATION(ppt, pptx, odp, key), PDF(pdf), "
                    + "IMAGE(jpg, jpeg, png, gif, bmp, webp, svg, tiff, tif, heic), NOTE(tfi — Thinkfree 노트). 이름 끝이 아니라 ext 기준. 미지정 시 전체", required = false) FileType fileType,
            @ToolParam(description = "정렬 필드: NAME(이름), SIZE(크기), UPDATE(수정일), OPEN(최근 열람). 미지정 시 기본 정렬", required = false) ResourceSortField sortBy,
            @ToolParam(description = "정렬 방향: ASC(오름차순), DESC(내림차순). sortBy만 지정하면 ASC로 정렬된다", required = false) SortOrder sortOrder,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 ID — 직전 응답의 nextCursor (마지막 resourceSeq). 첫 페이지면 생략", required = false) Long cursorId,
            @ToolParam(description = "다음 페이지 커서 값 — 직전 응답의 nextCursorValue (마지막 항목의 정렬 기준 값). 첫 페이지면 생략", required = false) String cursorValue,
            @ToolParam(description = "다음 페이지 커서 타입 — 직전 응답의 nextCursorType (마지막 항목의 resourceType). 첫 페이지면 생략", required = false) ResourceType cursorType) {

        log.info("[MCP] drive_search_resources called. sharedByUserSeq : {}, keyword : {}, parentFolderSeq : {}, resourceType : {}, searchType : {}, "
                        + "ownerId : {}, startDate : {}, endDate : {}, fileType : {}, sortBy : {}, sortOrder : {}, pageSize : {}, cursorId : {}, cursorValue : {}, cursorType : {}",
                sharedByUserSeq, keyword, parentFolderSeq, resourceType, searchType,
                ownerId, startDate, endDate, fileType, sortBy, sortOrder, pageSize, cursorId, cursorValue, cursorType);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            // 웹 목록 API의 @CheckResourcePermission(VIEW, resourceSeq=pfs, sharedByUserSeq=ss)과 동일한 검사
            // (공유 폴더 조회에서 pfs 누락도 이 안에서 ACCESS_DENIED로 걸러진다 — resolve의 throwIfNull)
            int ownerSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
            resourcePermissionService.checkPermission(SharePermissionType.VIEW, parentFolderSeq, ownerSeq,
                    RequestActor.of(requestUserSeq, details.isAnonymous()), false);

            var command = ResourceSearchCommand.builder()
                    .sharedByUserSeq(sharedByUserSeq)
                    .searchKeyword(keyword)
                    .parentFolderSeq(parentFolderSeq)
                    .resourceType(resourceType)
                    .searchType(searchType == null ? ResourceSearchType.MY : searchType)
                    .ownerId(ownerId)
                    .startDate(McpToolParams.parseDate(startDate, "startDate"))
                    .endDate(McpToolParams.parseDate(endDate, "endDate"))
                    .fileType(fileType)
                    // 도메인은 sortBy·sortOrder가 둘 다 있어야 정렬을 적용 — 한쪽만 오면 짝을 기본값으로 채워 의도를 살린다
                    .sortBy(sortBy == null && sortOrder != null ? ResourceSortField.NAME : sortBy)
                    .sortOrder(sortBy != null && sortOrder == null ? SortOrder.ASC : sortOrder)
                    .cursorValue(cursorValue)
                    .cursorId(cursorId)
                    .cursorType(cursorType)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            // webViewUrl 조립 재료 — 항목별 확정(파일=미리보기, 폴더=폴더 열람)은 컨버터가 한다
            String webBaseUrl = clientDomainService.getDomain(details.getTenantSeq());
            return resourceToolConverter.toSearchResponse(resourceUseCase.search(command, requestUserSeq), webBaseUrl, requestUserSeq);
        });
    }

    @Tool(name = "drive_get_resource_metadata",
            description = "리소스(파일/폴더) 한 건의 상세 메타데이터를 조회한다 — 이름, 종류, 상위 폴더, 전체 경로, 크기, "
                    + "잠금 여부, 요청자의 권한(canView/canEdit/canDownload/canUpload/canDelete/canShare), 소유자·등록자·수정자 정보와 등록/수정 일시. "
                    + "resourceSeq를 이미 아는 리소스 한 건을 확인할 때 사용한다 — resourceSeq를 모르거나 조건으로 찾아야 하면 "
                    + "먼저 drive_search_resources로 검색한 뒤 결과 항목의 resourceSeq를 사용한다. "
                    + "다른 사용자가 공유해 준 리소스는 sharedByUserSeq=소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 함께 넘긴다 "
                    + "(권한이 없으면 에러가 반환된다). 휴지통에 있는 리소스는 isDelete=true로 조회한다. "
                    + "응답의 ext는 파일 형식(점 제외, 업로드 때 정해지고 이름 변경으로 바뀌지 않음, 폴더·확장자 없는 파일은 빈 값)이다 — 형식은 이름 끝이 아니라 ext로 판단한다. "
                    + "응답에는 webViewUrl(브라우저용 웹 링크)이 포함된다 — 파일은 미리보기, 폴더는 폴더 열람으로 이어지며 "
                    + "사용자가 직접 열람·편집·다운로드하려 할 때 이 링크를 안내한다.")
    public McpToolResult<ResourceDetailToolResponse> getResourceMetadata(
            @ToolParam(description = "조회할 리소스의 resourceSeq (검색 결과 항목의 resourceSeq)") Long resourceSeq,
            @ToolParam(description = "공유받은 리소스 조회 시 그 리소스 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "휴지통에 있는(삭제된) 리소스를 조회하려면 true. "
                    + "확실하지 않으면 생략하라 (생략 시 삭제되지 않은 리소스를 조회)", required = false) Boolean isDelete) {

        log.info("[MCP] drive_get_resource_metadata called. resourceSeq : {}, sharedByUserSeq : {}, isDelete : {}",
                resourceSeq, sharedByUserSeq, isDelete);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            // 권한 검사의 소유자 숏컷(owner==actor 즉시 통과) 때문에 null이 NOT_FOUND 오도로 새지 않게 선검증
            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 상세 API의 @CheckResourcePermission(VIEW, isDelete=ANY)과 동일한 검사 — ANY는 isDelete=null
            resourcePermissionService.checkPermission(SharePermissionType.VIEW, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq),
                    RequestActor.of(requestUserSeq, details.isAnonymous()), null);

            // isDelete 미지정(null)은 usecase가 미삭제(false)로 기본 처리 — 웹 API의 "값 없으면 미삭제"와 동일
            var command = new ResourceFindCommand(resourceSeq, sharedByUserSeq, isDelete);
            var info = resourceUseCase.findById(command, requestUserSeq);

            // 웹 링크 조립은 어댑터 책임 — 파일은 read/download와 동일한 /preview, 폴더는 /drive|/shared 열람 링크.
            // ss는 요청과 동일한 접근 스코프(resolveUserSeq) — 표시용 ownerSeq는 공유 폴더 내 제3자 파일에서 어긋난다
            String webViewUrl = McpUrls.webViewUrl(clientDomainService.getDomain(details.getTenantSeq()),
                    info.resourceType(), info.resourceSeq(),
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), requestUserSeq);
            return resourceToolConverter.toDetailResponse(info, webViewUrl);
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_move_resource",
            description = "파일/폴더를 다른 폴더로 이동한다. 원본이 그대로 옮겨지며 사본을 만들지 않는다 — 복제는 drive_copy_file, "
                    + "이름 변경이나 삭제는 이 툴이 하지 않는다. 파일/폴더 구분 없이 대상의 resourceSeq 하나만 넘기면 서버가 종류를 "
                    + "판별하고, 폴더를 이동하면 하위 파일/폴더 전체가 함께 이동한다. resourceSeq는 drive_search_resources 등 "
                    + "목록 툴 결과 항목에서 얻는다. 이동 위치 지정 레시피: ① 내 드라이브 최상위로 옮길 때는 parentFolderSeq 생략 "
                    + "② 특정 폴더 안으로 옮길 때는 그 폴더의 resourceSeq를 parentFolderSeq로 지정 ③ 공유받은 폴더 안으로 옮길 때는 "
                    + "parentFolderSeq와 함께 그 폴더 소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 targetUserSeq로 지정 — "
                    + "업로드 권한(canUpload)이 있어야 한다. 이동 대상이 공유받은 리소스면 그 소유자의 userSeq를 sharedByUserSeq로 "
                    + "지정한다(내 리소스면 생략). 이동 위치에 같은 이름의 리소스가 이미 있어도 이동은 진행되며 이름에 구분 번호가 "
                    + "자동으로 붙는다. 폴더를 자기 자신이나 자기 하위 폴더 안으로 이동할 수 없고, 존재하지 않는 폴더로의 이동, "
                    + "잠긴(사용 중) 리소스의 이동은 에러가 반환된다. 성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> moveResource(
            @ToolParam(description = "이동할 리소스의 resourceSeq (drive_search_resources 등 목록 툴 결과 항목의 resourceSeq)") Long resourceSeq,
            @ToolParam(description = "이동해 넣을 폴더의 resourceSeq. 내 드라이브 최상위로 옮기면 생략",
                    required = false) Long parentFolderSeq,
            @ToolParam(description = "이동 대상이 공유받은 리소스일 때 그 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "공유받은 폴더 안으로 옮길 때 그 폴더 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 드라이브로 옮기면 생략", required = false) Integer targetUserSeq) {

        log.info("[MCP] drive_move_resource called. resourceSeq : {}, parentFolderSeq : {}, sharedByUserSeq : {}, targetUserSeq : {}",
                resourceSeq, parentFolderSeq, sharedByUserSeq, targetUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 이동 API의 권한 aspect 2건과 동일한 검사(파일/폴더 선언 동일) —
            // 이동 위치(pfs, ts 기준)에 UPLOAD + 원본(resourceSeq, ss 기준)에 EDIT. 잠김 검사는 ResourceService.fileMove/folderMove 가 행 잠금 뒤에
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, parentFolderSeq,
                    MapStructUtil.resolveUserSeq(targetUserSeq, requestUserSeq), actor, false);
            resourcePermissionService.checkPermission(SharePermissionType.EDIT, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), actor, false);

            var command = new ResourceMoveCommand(sharedByUserSeq, targetUserSeq, parentFolderSeq);
            resourceUseCase.move(resourceSeq, command, requestUserSeq, details.getUsername());
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_rename_resource",
            description = "파일/폴더의 이름을 변경한다. 이름만 바뀌고 위치와 내용은 그대로다 — 다른 폴더로 옮기려면 "
                    + "drive_move_resource, 사본 생성은 drive_copy_file을 사용한다. 파일/폴더 구분 없이 대상의 resourceSeq 하나만 "
                    + "넘기면 서버가 종류를 판별하고, 폴더 이름을 바꾸면 하위 파일/폴더의 전체 경로도 함께 갱신된다. "
                    + "resourceSeq는 drive_search_resources 등 목록 툴 결과 항목에서 얻는다. resourceName에는 새 이름을 넘긴다. "
                    + "파일 확장자는 이름 변경으로 바뀌지 않는다 — 서버가 항상 기존 확장자를 유지한다: resourceName이 기존 확장자로 "
                    + "끝나면(대소문자 무시) 그 확장자를 한 번만 떼고 기존 확장자를 다시 붙이고, 그렇지 않으면 resourceName 전체를 "
                    + "이름으로 보고 기존 확장자를 붙인다(예: 기존 \"메모.note\"에 \"회의록\" 또는 \"회의록.note\" → \"회의록.note\", "
                    + "\"회의록.txt\" → \"회의록.txt.note\"). 사용자가 확장자(파일 형식) 변경을 요청하면 이 툴로는 할 수 없다고 안내하고 "
                    + "다른 확장자를 붙여 호출하지 않는다. 기존 확장자가 없는 파일만 resourceName이 그대로 저장되고 확장자는 계속 없다(이름의 \".txt\" 같은 부분도 이름 일부). "
                    + "이름 앞뒤 공백, 마침표(.)로 시작하거나 끝나는 이름, 확장자만 있는 이름(예: \".note\"), 255자 초과, \\ / : * ? \" < > | 문자, Windows 예약어(CON·PRN·AUX·NUL·COM1~9·LPT1~9)는 에러가 반환된다. "
                    + "대상이 공유받은 리소스면 그 소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 sharedByUserSeq로 "
                    + "지정한다(내 리소스면 생략) — 편집 권한(canEdit)이 있어야 한다. 같은 폴더에 같은 이름이 이미 있으면 "
                    + "기본적으로 에러가 반환된다 — 다른 이름으로 다시 시도하거나, 파일에 한해 nameConflictPolicy=AUTO를 지정하면 "
                    + "이름에 구분 번호가 자동으로 붙는다(폴더에는 적용되지 않는다). 잠긴(사용 중) 리소스의 이름 변경도 에러가 "
                    + "반환된다. 성공 시 실제 저장된 이름 전체가 resourceName으로 반환된다(파일은 확장자 포함, AUTO로 번호가 붙었으면 "
                    + "번호가 붙은 이름) — 요청한 이름 대신 이 값을 사용자에게 안내한다. 성공 판정 기준은 응답에 error 필드가 없는 것이다.")
    public McpToolResult<ResourceRenameToolResponse> renameResource(
            @ToolParam(description = "이름을 변경할 리소스의 resourceSeq (drive_search_resources 등 목록 툴 결과 항목의 resourceSeq)") Long resourceSeq,
            @ToolParam(description = "변경할 새 이름. 파일은 기존 확장자를 포함하든 빼든 서버가 기존 확장자를 유지한다 "
                    + "(예: 기존 \"보고서.docx\"에 \"보고서 최종\" 또는 \"보고서 최종.docx\" → \"보고서 최종.docx\"). 다른 확장자를 붙이면 확장자가 "
                    + "아니라 이름의 일부로 저장된다. 앞뒤 공백·마침표로 시작·끝, \\ / : * ? \" < > | 문자, Windows 예약어 불가") String resourceName,
            @ToolParam(description = "대상이 공유받은 리소스일 때 그 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "파일 전용 중복 이름 정책: NONE(기본 — 같은 이름이 있으면 에러), "
                    + "AUTO(같은 이름이 있으면 구분 번호 자동 부여). 폴더에는 적용되지 않으니 폴더면 생략. "
                    + "확실하지 않으면 생략하라", required = false) NameConflictPolicy nameConflictPolicy) {

        log.info("[MCP] drive_rename_resource called. resourceSeq : {}, resourceName : {}, sharedByUserSeq : {}, nameConflictPolicy : {}",
                resourceSeq, resourceName, sharedByUserSeq, nameConflictPolicy);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");
            McpToolParams.requireText(resourceName, "resourceName"); // 웹 @NotBlank(rn) 등가

            // 웹 rename API의 권한 aspect 등가 검사 — 원본(resourceSeq, ss 기준)에 EDIT(파일/폴더 선언 동일).
            // 잠김 검사는 파일이면 ResourceService.resolveName 이 행 잠금 뒤에(ncp=AUTO면 스킵), 폴더는 웹과 같이 없음
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.EDIT, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), actor, false);

            // 웹 요청과 동형 커맨드 그대로 전달 — ncp는 파일 경로만 읽는다(폴더는 웹 요청에 필드 부재 동형으로 무시)
            var command = new ResourceRenameCommand(sharedByUserSeq, resourceName, nameConflictPolicy);
            var info = resourceUseCase.rename(resourceSeq, command, requestUserSeq);
            // 웹(name=확장자 제외)과 달리 저장된 이름 전체 — upload_file·create_folder와 같은 resourceName 규약
            return new ResourceRenameToolResponse(info.fullName());
        });
    }

}

package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.starred.StarredUseCase;
import com.thinkfree.drive.application.starred.command.StarredListCommand;
import com.thinkfree.drive.application.starred.command.StarredSaveCommand;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.StarredToolConverter;
import com.thinkfree.drive.mcp.tool.response.StarredListToolResponse;
import com.thinkfree.drive.mcp.tool.response.StarredSaveToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.enums.ResourceType;
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
 * 중요문서함(즐겨찾기) MCP 툴 — application.starred.StarredUseCase를 내부 API와 동일하게 호출한다.
 * 목록은 요청자 본인 데이터만 조회하므로 웹과 마찬가지로 권한 검사가 없고,
 * 등록은 웹 controller의 @CheckResourcePermission(VIEW) aspect가 MCP 경로에 적용되지 않으므로 툴 본문에서 명시 호출한다.
 * 해제는 웹과 동일하게 권한 검사 없음 — 소유권은 도메인의 (starredSeq, userSeq) 스코프 삭제가 강제한다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveStarredTools {

    private final StarredUseCase starredUseCase;
    private final ResourcePermissionService resourcePermissionService;
    private final StarredToolConverter starredToolConverter;
    private final ClientDomainService clientDomainService;

    @Tool(name = "drive_list_starred_resources",
            description = "사용자가 중요문서함에 등록한(별표/즐겨찾기한) 파일/폴더 목록을 조회한다. "
                    + "'중요 문서', '즐겨찾기한 파일', '별표한 것'처럼 사용자가 직접 표시해 둔 리소스가 필요할 때 사용한다. "
                    + "이름이나 조건으로 특정 파일을 찾는 것이 목적이라면 이 툴 대신 drive_search_resources를, "
                    + "최근에 열어본 문서가 필요하면 drive_list_recent_resources를 사용한다. "
                    + "리소스 종류·파일 형식·수정일 범위·소유자로 필터링하고 이름/크기/수정일로 정렬할 수 있다 (기본: 폴더 우선, 이름 오름차순). "
                    + "항목의 ext는 파일 형식(점 제외, 업로드 때 정해지고 이름 변경으로 바뀌지 않음, 폴더·확장자 없는 파일은 빈 값)이다 — 형식은 이름 끝이 아니라 ext로 판단한다. "
                    + "결과는 커서 기반 페이지네이션이며, 응답 hasNext가 true면 nextCursor/nextCursorValue/nextCursorType을 "
                    + "다음 호출의 cursorId/cursorValue/cursorType으로 그대로 넘기면 다음 페이지를 받는다. "
                    + "항목에는 webViewUrl(브라우저용 웹 링크)이 포함된다 — 파일은 미리보기, 폴더는 폴더 열람으로 이어지며 "
                    + "사용자가 직접 열람·편집·다운로드하려 할 때 이 링크를 안내한다.")
    public McpToolResult<StarredListToolResponse> listStarredResources(
            @ToolParam(description = "리소스 종류 필터 (파일 : FILE, 폴더 : FOLDER). 미지정 시 전체", required = false) ResourceType resourceType,
            @ToolParam(description = "소유자 사용자 ID(이메일) 필터", required = false) String ownerId,
            @ToolParam(description = "수정일 검색 시작일 (yyyy-MM-dd, 해당 일 포함). endDate와 함께 지정해야 필터가 적용된다", required = false) String startDate,
            @ToolParam(description = "수정일 검색 종료일 (yyyy-MM-dd, 해당 일 포함). startDate와 함께 지정해야 필터가 적용된다", required = false) String endDate,
            @ToolParam(description = "파일 형식 필터: DOCUMENT(doc, docx, hwp, hwpx, txt, rtf, odt, md, pages), "
                    + "SPREADSHEET(xls, xlsx, csv, tsv, ods, numbers), PRESENTATION(ppt, pptx, odp, key), PDF(pdf), "
                    + "IMAGE(jpg, jpeg, png, gif, bmp, webp, svg, tiff, tif, heic), NOTE(tfi — Thinkfree 노트). 이름 끝이 아니라 ext 기준. 미지정 시 전체", required = false) FileType fileType,
            @ToolParam(description = "정렬 필드: NAME(이름), SIZE(크기), UPDATE(수정일). 미지정 시 기본 정렬(NAME)", required = false) SortField sortBy,
            @ToolParam(description = "정렬 방향: ASC(오름차순), DESC(내림차순). sortBy만 지정하면 ASC로 정렬된다", required = false) SortOrder sortOrder,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 ID — 직전 응답의 nextCursor (마지막 resourceSeq). 첫 페이지면 생략", required = false) Long cursorId,
            @ToolParam(description = "다음 페이지 커서 값 — 직전 응답의 nextCursorValue (마지막 항목의 정렬 기준 값). 첫 페이지면 생략", required = false) String cursorValue,
            @ToolParam(description = "다음 페이지 커서 타입 — 직전 응답의 nextCursorType (마지막 항목의 resourceType). 첫 페이지면 생략", required = false) ResourceType cursorType) {

        log.info("[MCP] drive_list_starred_resources called. resourceType : {}, ownerId : {}, startDate : {}, endDate : {}, "
                        + "fileType : {}, sortBy : {}, sortOrder : {}, pageSize : {}, cursorId : {}, cursorValue : {}, cursorType : {}",
                resourceType, ownerId, startDate, endDate, fileType, sortBy, sortOrder, pageSize, cursorId, cursorValue, cursorType);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            var command = StarredListCommand.builder()
                    .resourceType(resourceType)
                    .ownerId(ownerId)
                    .startDate(McpToolParams.parseDate(startDate, "startDate"))
                    .endDate(McpToolParams.parseDate(endDate, "endDate"))
                    .fileType(fileType)
                    // 도메인은 sortBy·sortOrder가 둘 다 있어야 정렬을 적용 — 한쪽만 오면 짝을 기본값으로 채워 의도를 살린다
                    .sortBy(sortBy == null && sortOrder != null ? SortField.NAME : sortBy)
                    .sortOrder(sortBy != null && sortOrder == null ? SortOrder.ASC : sortOrder)
                    .cursorValue(cursorValue)
                    .cursorId(cursorId)
                    .cursorType(cursorType)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            // webViewUrl 조립 재료 — 항목별 확정(파일=미리보기, 폴더=폴더 열람)은 컨버터가 한다
            String webBaseUrl = clientDomainService.getDomain(details.getTenantSeq());
            return starredToolConverter.toListResponse(starredUseCase.findAllPaginated(command, requestUserSeq), webBaseUrl, requestUserSeq);
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_star_resource",
            description = "파일/폴더를 중요문서함에 등록한다(별표/즐겨찾기 표시). 대상의 resourceSeq가 필요하다 — 모르면 먼저 "
                    + "drive_search_resources 등 목록 툴로 찾는다. 다른 사용자가 공유해 준 리소스는 sharedByUserSeq=소유자의 "
                    + "userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 함께 넘긴다. 이미 등록된 리소스를 다시 등록하면 에러(STARRED_002)가 "
                    + "반환된다 — 목록 툴 응답의 isStarred가 true면 등록하지 않는다. 성공 시 생성된 등록 항목의 starredSeq를 반환한다. "
                    + "등록만 하며 해제(별표 제거)는 drive_unstar_resource를 사용한다.")
    public McpToolResult<StarredSaveToolResponse> starResource(
            @ToolParam(description = "중요문서함에 등록할 리소스의 resourceSeq") Long resourceSeq,
            @ToolParam(description = "다른 사용자가 공유해 준 리소스일 때 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq) {

        log.info("[MCP] drive_star_resource called. resourceSeq : {}, sharedByUserSeq : {}", resourceSeq, sharedByUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 등록 API의 @CheckResourcePermission(VIEW)와 동일한 검사
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.VIEW, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), actor, false);

            var command = StarredSaveCommand.builder()
                    .resourceSeq(resourceSeq)
                    .sharedByUserSeq(sharedByUserSeq)
                    .build();
            return new StarredSaveToolResponse(starredUseCase.save(command, requestUserSeq));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_unstar_resource",
            description = "중요문서함에서 등록을 해제한다(별표/즐겨찾기 제거). 등록 항목의 starredSeq가 필요하다 — "
                    + "리소스의 resourceSeq가 아니다. starredSeq는 drive_list_starred_resources 결과 항목이나 "
                    + "drive_star_resource의 등록 응답에서 얻는다. 중요문서함 표시만 제거하며 파일/폴더 자체는 삭제하지 않는다. "
                    + "본인이 등록한 항목만 해제할 수 있다 — 남의 항목이거나 존재하지 않는 starredSeq면 에러(STARRED_001)가 "
                    + "반환된다. 성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> unstarResource(
            @ToolParam(description = "해제할 등록 항목의 starredSeq (목록 툴 결과 항목 또는 drive_star_resource 응답의 starredSeq)") Long starredSeq) {

        log.info("[MCP] drive_unstar_resource called. starredSeq : {}", starredSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(starredSeq, "starredSeq");

            // 웹 삭제 API는 권한 aspect 없음 — 도메인이 (starredSeq, 요청자) 스코프 삭제로 소유권 강제, 동형 유지
            starredUseCase.delete(starredSeq, requestUserSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

}

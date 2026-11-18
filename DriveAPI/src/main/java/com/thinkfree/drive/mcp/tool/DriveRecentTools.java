package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.recent.RecentUseCase;
import com.thinkfree.drive.application.recent.command.RecentListCommand;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.RecentToolConverter;
import com.thinkfree.drive.mcp.tool.response.RecentListToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 최근 문서함 MCP 툴 — application.recent.RecentUseCase를 내부 API와 동일하게 호출한다.
 * 웹 최근 문서함 API에는 권한 aspect가 없으므로(요청자 본인 데이터만 조회) 툴에도 권한 검사가 없다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveRecentTools {

    private final RecentUseCase recentUseCase;
    private final RecentToolConverter recentToolConverter;
    private final ClientDomainService clientDomainService;

    @Tool(name = "drive_list_recent_resources",
            description = "사용자가 최근에 열거나 수정한 파일/폴더 목록을 최근 사용순으로 조회한다 (공유받아 연 리소스 포함). "
                    + "'최근 문서', '아까 본 파일', '어제 작업하던 문서'처럼 최근 사용 이력이 필요할 때 사용한다. "
                    + "이름이나 조건으로 특정 파일을 찾는 것이 목적이라면 이 툴 대신 drive_search_resources를 사용한다. "
                    + "리소스 종류·파일 형식·수정일 범위·소유자로 필터링할 수 있다. "
                    + "항목의 ext는 파일 형식(점 제외, 업로드 때 정해지고 이름 변경으로 바뀌지 않음, 폴더·확장자 없는 파일은 빈 값)이다 — 형식은 이름 끝이 아니라 ext로 판단한다. "
                    + "결과는 커서 기반 페이지네이션이며, 응답 hasNext가 true면 nextCursor/nextCursorValue를 "
                    + "다음 호출의 cursorId/cursorValue로 그대로 넘기면 다음 페이지를 받는다. "
                    + "항목에는 webViewUrl(브라우저용 웹 링크)이 포함된다 — 파일은 미리보기, 폴더는 폴더 열람으로 이어지며 "
                    + "사용자가 직접 열람·편집·다운로드하려 할 때 이 링크를 안내한다.")
    public McpToolResult<RecentListToolResponse> listRecentResources(
            @ToolParam(description = "리소스 종류 필터 (파일 : FILE, 폴더 : FOLDER). 미지정 시 전체", required = false) ResourceType resourceType,
            @ToolParam(description = "소유자 사용자 ID(이메일) 필터", required = false) String ownerId,
            @ToolParam(description = "수정일 검색 시작일 (yyyy-MM-dd, 해당 일 포함). endDate와 함께 지정해야 필터가 적용된다", required = false) String startDate,
            @ToolParam(description = "수정일 검색 종료일 (yyyy-MM-dd, 해당 일 포함). startDate와 함께 지정해야 필터가 적용된다", required = false) String endDate,
            @ToolParam(description = "파일 형식 필터: DOCUMENT(doc, docx, hwp, hwpx, txt, rtf, odt, md, pages), "
                    + "SPREADSHEET(xls, xlsx, csv, tsv, ods, numbers), PRESENTATION(ppt, pptx, odp, key), PDF(pdf), "
                    + "IMAGE(jpg, jpeg, png, gif, bmp, webp, svg, tiff, tif, heic), NOTE(tfi — Thinkfree 노트). 이름 끝이 아니라 ext 기준. 미지정 시 전체", required = false) FileType fileType,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 ID — 직전 응답의 nextCursor (마지막 resourceSeq). 첫 페이지면 생략", required = false) Long cursorId,
            @ToolParam(description = "다음 페이지 커서 값 — 직전 응답의 nextCursorValue (마지막 항목의 최근 사용일). 첫 페이지면 생략", required = false) String cursorValue) {

        log.info("[MCP] drive_list_recent_resources called. resourceType : {}, ownerId : {}, startDate : {}, endDate : {}, "
                        + "fileType : {}, pageSize : {}, cursorId : {}, cursorValue : {}",
                resourceType, ownerId, startDate, endDate, fileType, pageSize, cursorId, cursorValue);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            var command = RecentListCommand.builder()
                    .resourceType(resourceType)
                    .ownerId(ownerId)
                    .startDate(McpToolParams.parseDate(startDate, "startDate"))
                    .endDate(McpToolParams.parseDate(endDate, "endDate"))
                    .fileType(fileType)
                    .cursorValue(cursorValue)
                    .cursorId(cursorId)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            // webViewUrl 조립 재료 — 항목별 확정(파일=미리보기, 폴더=폴더 열람)은 컨버터가 한다
            String webBaseUrl = clientDomainService.getDomain(details.getTenantSeq());
            return recentToolConverter.toListResponse(recentUseCase.findAllPaginated(command, requestUserSeq), webBaseUrl, requestUserSeq);
        });
    }

}

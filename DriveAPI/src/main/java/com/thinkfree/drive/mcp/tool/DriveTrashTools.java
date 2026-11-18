package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.trash.TrashUseCase;
import com.thinkfree.drive.application.trash.command.TrashListCommand;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.TrashToolConverter;
import com.thinkfree.drive.mcp.tool.response.TrashListToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.trash.enums.TrashSortField;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 휴지통 MCP 툴 — application.trash.TrashUseCase를 내부 API와 동일하게 호출한다.
 * 웹 휴지통 API에는 권한 aspect가 없으므로(요청자 본인 스코프가 SQL로 강제) 툴에도 권한 검사가 없다.
 * 삭제(휴지통 이동)의 잠김 검사는 TrashUseCase(ResourceService.lockActiveUnlocked)가 행 잠금 뒤에 한다 — 웹과 같은 흐름.
 * 삭제·복원·완전삭제는 각각 파일/폴더 통합 툴 하나 — 종류(복원은 복원 위치 pe까지) 판별은 usecase가 조회로 파생한다
 * (웹은 프런트가 종류별 엔드포인트 선택 + 복원은 pe 선조회 전달). 휴지통 비우기 툴은 미제공.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveTrashTools {

    private final TrashUseCase trashUseCase;
    private final TrashToolConverter trashToolConverter;

    @Tool(name = "drive_list_trash_resources",
            description = "휴지통에 있는(삭제된) 파일/폴더 목록을 조회한다. "
                    + "'휴지통', '삭제한 파일', '지운 문서'처럼 사용자가 삭제해 둔 리소스가 필요할 때 사용한다. 조회 전용이며 "
                    + "복원이나 완전삭제는 하지 않는다. 삭제되지 않은 리소스 검색은 drive_search_resources를, "
                    + "중요문서함은 drive_list_starred_resources를 사용한다. "
                    + "각 항목에는 삭제한 사용자(deleteUserId·deleteUserName)와 삭제 일시(deleteDate)가 포함된다. "
                    + "리소스 종류·파일 형식·삭제일 범위·삭제한 사용자로 필터링하고 이름/크기/삭제일로 정렬할 수 있다 (기본: 삭제일 내림차순 — 최근 삭제가 먼저). "
                    + "항목의 ext는 파일 형식(점 제외, 업로드 때 정해지고 이름 변경으로 바뀌지 않음, 폴더·확장자 없는 파일은 빈 값)이다 — 형식은 이름 끝이 아니라 ext로 판단한다. "
                    + "결과는 커서 기반 페이지네이션이며, 응답 hasNext가 true면 nextCursor/nextCursorValue/nextCursorType을 "
                    + "다음 호출의 cursorId/cursorValue/cursorType으로 그대로 넘기면 다음 페이지를 받는다.")
    public McpToolResult<TrashListToolResponse> listTrashResources(
            @ToolParam(description = "리소스 종류 필터 (파일 : FILE, 폴더 : FOLDER). 미지정 시 전체", required = false) ResourceType resourceType,
            @ToolParam(description = "삭제한 사용자 ID(이메일) 필터", required = false) String deleteUserId,
            @ToolParam(description = "삭제일 검색 시작일 (yyyy-MM-dd, 해당 일 포함). endDate와 함께 지정해야 필터가 적용된다", required = false) String startDate,
            @ToolParam(description = "삭제일 검색 종료일 (yyyy-MM-dd, 해당 일 포함). startDate와 함께 지정해야 필터가 적용된다", required = false) String endDate,
            @ToolParam(description = "파일 형식 필터: DOCUMENT(doc, docx, hwp, hwpx, txt, rtf, odt, md, pages), "
                    + "SPREADSHEET(xls, xlsx, csv, tsv, ods, numbers), PRESENTATION(ppt, pptx, odp, key), PDF(pdf), "
                    + "IMAGE(jpg, jpeg, png, gif, bmp, webp, svg, tiff, tif, heic), NOTE(tfi — Thinkfree 노트). 이름 끝이 아니라 ext 기준. 미지정 시 전체", required = false) FileType fileType,
            @ToolParam(description = "정렬 필드: NAME(이름), SIZE(크기), REGISTER(삭제일). 미지정 시 기본 정렬(삭제일 내림차순)", required = false) TrashSortField sortBy,
            @ToolParam(description = "정렬 방향: ASC(오름차순), DESC(내림차순). sortBy만 지정하면 ASC로 정렬된다", required = false) SortOrder sortOrder,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 ID — 직전 응답의 nextCursor (마지막 resourceSeq). 첫 페이지면 생략", required = false) Long cursorId,
            @ToolParam(description = "다음 페이지 커서 값 — 직전 응답의 nextCursorValue (마지막 항목의 정렬 기준 값). 첫 페이지면 생략", required = false) String cursorValue,
            @ToolParam(description = "다음 페이지 커서 타입 — 직전 응답의 nextCursorType (마지막 항목의 resourceType). 첫 페이지면 생략", required = false) ResourceType cursorType) {

        log.info("[MCP] drive_list_trash_resources called. resourceType : {}, deleteUserId : {}, startDate : {}, endDate : {}, "
                        + "fileType : {}, sortBy : {}, sortOrder : {}, pageSize : {}, cursorId : {}, cursorValue : {}, cursorType : {}",
                resourceType, deleteUserId, startDate, endDate, fileType, sortBy, sortOrder, pageSize, cursorId, cursorValue, cursorType);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            var command = TrashListCommand.builder()
                    .resourceType(resourceType)
                    .deleteUserId(deleteUserId)
                    .startDate(McpToolParams.parseDate(startDate, "startDate"))
                    .endDate(McpToolParams.parseDate(endDate, "endDate"))
                    .fileType(fileType)
                    // 도메인은 sortBy·sortOrder가 둘 다 있어야 정렬을 적용 — 한쪽만 오면 짝을 기본값으로 채워 의도를 살린다
                    // (sortOrder 단독 시 짝은 SQL 기본 정렬 컬럼과 동일한 REGISTER(삭제일))
                    .sortBy(sortBy == null && sortOrder != null ? TrashSortField.REGISTER : sortBy)
                    .sortOrder(sortBy != null && sortOrder == null ? SortOrder.ASC : sortOrder)
                    .cursorValue(cursorValue)
                    .cursorId(cursorId)
                    .cursorType(cursorType)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            return trashToolConverter.toListResponse(trashUseCase.findAllPaginated(command, requestUserSeq));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_delete_resource",
            description = "파일/폴더를 삭제한다 — 휴지통으로 이동하며, drive_restore_trash_resource로 복원할 수 있다. "
                    + "완전삭제(영구 삭제)가 아니다 — 휴지통에 있는 것을 영구히 지우려면 drive_delete_trash_resource_permanently를 사용한다. "
                    + "파일/폴더 구분 없이 대상의 resourceSeq 하나만 넘기면 서버가 종류를 판별한다 — resourceSeq는 "
                    + "drive_search_resources 등 목록 툴 결과 항목에서 얻는다. 폴더를 삭제하면 하위 파일/폴더 전체가 함께 휴지통으로 이동한다. "
                    + "본인 소유 리소스만 삭제할 수 있다(공유받은 리소스 불가) — 본인 소유가 아니거나 이미 휴지통에 있는 resourceSeq면 "
                    + "에러(RESOURCE_005), 잠긴(사용 중) 리소스면 에러가 반환된다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> deleteResource(
            @ToolParam(description = "삭제(휴지통 이동)할 리소스의 resourceSeq (drive_search_resources 등 목록 툴 결과 항목의 resourceSeq)") Long resourceSeq) {

        log.info("[MCP] drive_delete_resource called. resourceSeq : {}", resourceSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 잠김 검사는 TrashUseCase.moveToTrash 가 행 잠금 뒤에 한다(웹 삭제 API 와 같은 흐름)
            // 권한 aspect는 웹도 없음 — 본인 스코프 조회가 소유권 강제, 동형 유지
            trashUseCase.moveToTrash(resourceSeq, requestUserSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_restore_trash_resource",
            description = "휴지통에 있는(삭제된) 파일/폴더를 복원한다. 파일/폴더 구분 없이 대상의 resourceSeq 하나만 넘기면 "
                    + "서버가 종류를 판별한다 — resourceSeq는 drive_list_trash_resources 결과 항목에서 얻는다. "
                    + "원래 있던 폴더로 복원되며, 그 폴더가 이미 삭제된 경우에는 내 드라이브 최상위(루트)로 복원된다. "
                    + "복원 위치에 같은 이름의 리소스가 이미 있어도 복원은 진행되며 이름에 구분 번호가 자동으로 붙는다. "
                    + "본인 휴지통의 항목만 복원할 수 있다 — 휴지통에 없는(삭제되지 않았거나 남의) resourceSeq면 "
                    + "에러(RESOURCE_005)가 반환된다. 복원만 하며 삭제(drive_delete_resource)나 "
                    + "완전삭제(drive_delete_trash_resource_permanently), 휴지통 비우기는 하지 않는다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> restoreTrashResource(
            @ToolParam(description = "복원할 리소스의 resourceSeq (drive_list_trash_resources 결과 항목의 resourceSeq)") Long resourceSeq) {

        log.info("[MCP] drive_restore_trash_resource called. resourceSeq : {}", resourceSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 복원 API와 동일하게 권한 aspect 없음 — 본인 스코프 조회가 소유권 강제, 동형 유지
            trashUseCase.restore(resourceSeq, requestUserSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_delete_trash_resource_permanently",
            description = "휴지통에 있는(삭제된) 파일/폴더를 완전삭제한다. 되돌릴 수 없는 영구 삭제다 — "
                    + "휴지통에서도 사라져 복원이 불가능해지고, 파일은 버전 이력까지, 폴더는 하위 파일/폴더 전체가 함께 삭제된다. "
                    + "사용자가 '완전삭제', '영구 삭제', '휴지통에서 완전히 지워줘'처럼 명시적으로 요청한 경우에만 사용하고, "
                    + "실행 전에 대상의 이름과 종류를 사용자에게 확인받는 것이 안전하다. "
                    + "일반적인 '삭제'(휴지통 이동)는 drive_delete_resource를, 복원은 drive_restore_trash_resource를 사용한다. "
                    + "파일/폴더 구분 없이 대상의 resourceSeq 하나만 넘기면 서버가 종류를 판별한다 — "
                    + "resourceSeq는 drive_list_trash_resources 결과 항목에서 얻는다. "
                    + "삭제된 만큼 저장 용량(쿼터)은 반환된다. 본인 휴지통의 항목만 삭제할 수 있다 — "
                    + "휴지통에 없는(삭제되지 않았거나 남의) resourceSeq면 에러(RESOURCE_005)가 반환된다. "
                    + "휴지통 전체 비우기는 지원하지 않는다(항목별로 호출). "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> deleteTrashResourcePermanently(
            @ToolParam(description = "완전삭제할 리소스의 resourceSeq (drive_list_trash_resources 결과 항목의 resourceSeq)") Long resourceSeq) {

        log.info("[MCP] drive_delete_trash_resource_permanently called. resourceSeq : {}", resourceSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 완전삭제 API와 동일하게 권한 aspect 없음 — 본인 스코프 조회가 소유권 강제, 동형 유지
            trashUseCase.deletePermanently(resourceSeq, requestUserSeq, details.getUsername());
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

}

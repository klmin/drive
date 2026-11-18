package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.share.ShareTargetUseCase;
import com.thinkfree.drive.application.share.ShareUseCase;
import com.thinkfree.drive.application.share.command.*;
import com.thinkfree.drive.application.share.info.ShareDetailInfo;
import com.thinkfree.drive.application.share.info.ShareTypesInfo;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.ShareToolConverter;
import com.thinkfree.drive.mcp.tool.converter.SharedToolConverter;
import com.thinkfree.drive.mcp.tool.request.ShareTargetToolItem;
import com.thinkfree.drive.mcp.tool.response.ShareDetailToolResponse;
import com.thinkfree.drive.mcp.tool.response.ShareSaveToolResponse;
import com.thinkfree.drive.mcp.tool.response.ShareUserSearchToolResponse;
import com.thinkfree.drive.mcp.tool.response.SharedListToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.SharePermissionService;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.share.service.ResourceShareLinkService;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 공유 MCP 툴 — application.share의 ShareUseCase·ShareTargetUseCase를 내부 API와 동일하게 호출한다.
 * 쓰기 툴 구성(설계 합의): 등록/수정은 drive_share_resource가 웹 POST(save)/PATCH(update)를 기존 공유 유무로
 * 라우팅해 하나로 담당하고(에이전트는 공유 존재 여부를 사전에 알 수 없으므로 upsert가 계약),
 * 대상 교체는 권한 모델이 달라(소유자 vs canShare 위임자) drive_update_share_targets로 분리한다.
 * 해제(unshare)는 파괴적 동작이라 drive_unshare_resource로 분리하고 실행 전 사용자 확인을 계약으로 둔다.
 * 유형 조회·링크 복사는 독립 툴 없이 각각 에러 메시지·응답 필드(linkUrl)에 흡수.
 *
 * 웹 controller의 @CheckSharePermission aspect는 MCP 경로에 적용되지 않으므로(적용해도 @Before 예외가
 * 툴 catch 밖에서 터져 인밴드 에러 규약을 못 탄다), 동일한 검사를 툴 본문에서 명시 호출한다.
 * 공유문서함 목록은 요청자 본인 대상 데이터만 조회하므로 웹과 마찬가지로 권한 검사가 없다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveShareTools {

    /** 공유 변경 툴(대상 교체·해제) 거부 조건 — 판정은 ShareUseCase·ShareTargetUseCase 의 @TenantFeatureRequired(SHARE). 공유 생성·수정은 유형 사전 검증이 먼저 거부한다 */
    private static final String SHARE_OFF_RESTRICTION =
            "시스템 관리형 테넌트(SYSTEM_MANAGED)는 공유가 꺼져 있어 거부된다(TENANT_011). ";

    private final ShareUseCase shareUseCase;
    private final ShareTargetUseCase shareTargetUseCase;
    private final SharePermissionService sharePermissionService;
    private final ShareFindService shareFindService;
    private final ResourceShareLinkService resourceShareLinkService;
    private final ShareToolConverter shareToolConverter;
    private final SharedToolConverter sharedToolConverter;
    private final ClientDomainService clientDomainService;

    @Tool(name = "drive_get_share_permissions",
            description = "리소스에 걸린 공유 정보를 조회한다 — 공유 유형(shareType), 공유 기본 권한 6종"
                    + "(canView/canEdit/canDownload/canUpload/canDelete/canShare), 비밀번호 설정 여부, 만료일과 "
                    + "공유 대상 전체 목록(대상별 사용자 정보·권한 6종). '누구랑 공유돼 있어?', '이 파일 공유 설정 보여줘', "
                    + "'누가 편집할 수 있어?'처럼 리소스의 공유 상태나 대상별 권한이 필요할 때 사용한다. 조회 전용이며 "
                    + "공유 등록/변경/해제는 하지 않는다. shareSeq는 drive_search_resources·drive_list_recent_resources·"
                    + "drive_list_starred_resources 등 목록 툴 결과 항목의 shareSeq를 그대로 사용한다(이미 갖고 있으면 재검색 불필요) — "
                    + "그 값이 null이면 공유되지 않은 리소스이므로 이 툴을 호출할 필요 없이 '공유 안 됨'으로 답한다. "
                    + "요청자 본인의 권한만 필요하면 이 툴 대신 drive_get_resource_metadata의 can* 필드를 사용한다.")
    public McpToolResult<ShareDetailToolResponse> getSharePermissions(
            @ToolParam(description = "조회할 공유의 shareSeq (search·recent·starred 등 목록 툴 결과 항목의 shareSeq)") Long shareSeq,
            @ToolParam(description = "공유받은 리소스 조회 시 그 리소스 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq) {

        log.info("[MCP] drive_get_share_permissions called. shareSeq : {}, sharedByUserSeq : {}", shareSeq, sharedByUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            // 권한 검사의 소유자 숏컷(owner==actor 즉시 통과) 때문에 null이 언박싱 NPE로 새지 않게 선검증
            McpToolParams.require(shareSeq, "shareSeq");

            // 웹 상세 API의 @CheckSharePermission(VIEW)과 동일한 검사 — aspect의 소유자 해석(ss 없으면 요청자)도 동일
            int ownerSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
            sharePermissionService.authorize(SharePermissionType.VIEW, shareSeq, ownerSeq, requestUserSeq);

            var command = new ShareDetailCommand(shareSeq, sharedByUserSeq);
            return shareToolConverter.toDetailResponse(shareUseCase.detail(command, requestUserSeq));
        });
    }

    @Tool(name = "drive_list_shared_resources",
            description = "공유문서함 목록 — 다른 사용자가 나에게 공유해 준 파일/폴더 목록을 조회한다 (웹 공유문서함 화면과 동일 데이터). "
                    + "'공유받은 문서', '나한테 공유된 파일', '공유문서함 보여줘'처럼 공유받은 리소스 전체가 필요할 때 사용한다. "
                    + "내가 다른 사람에게 공유한 것이나 공유 설정 상세는 이 툴이 아니라 drive_get_share_permissions의 몫이고, "
                    + "공유받은 특정 폴더 '안'의 내용은 drive_search_resources(parentFolderSeq+sharedByUserSeq)로 조회한다. "
                    + "결과 항목의 리소스를 다른 툴(read/download/copy 등)에서 쓸 때는 그 항목의 sharedByUserSeq(없으면 userSeq)를 "
                    + "해당 툴의 sharedByUserSeq/targetUserSeq로 넘긴다. "
                    + "리소스 종류·파일 형식·수정일 범위·소유자로 필터링하고 이름/크기/수정일/열람일/공유일로 정렬할 수 있다 (기본: 공유일 내림차순 — 최근 공유가 먼저). "
                    + "항목의 ext는 파일 형식(점 제외, 업로드 때 정해지고 이름 변경으로 바뀌지 않음, 폴더·확장자 없는 파일은 빈 값)이다 — 형식은 이름 끝이 아니라 ext로 판단한다. "
                    + "결과는 커서 기반 페이지네이션이며, 응답 hasNext가 true면 nextCursor/nextCursorValue/nextCursorType을 "
                    + "다음 호출의 cursorId/cursorValue/cursorType으로 그대로 넘기면 다음 페이지를 받는다. "
                    + "항목에는 webViewUrl(브라우저용 웹 링크)이 포함된다 — 파일은 미리보기, 폴더는 폴더 열람으로 이어지며 "
                    + "사용자가 직접 열람·편집·다운로드하려 할 때 이 링크를 안내한다.")
    public McpToolResult<SharedListToolResponse> listSharedResources(
            @ToolParam(description = "리소스 종류 필터 (파일 : FILE, 폴더 : FOLDER). 미지정 시 전체", required = false) ResourceType resourceType,
            @ToolParam(description = "공유해 준 소유자의 사용자 ID(이메일) 필터", required = false) String ownerId,
            @ToolParam(description = "수정일 검색 시작일 (yyyy-MM-dd, 해당 일 포함). endDate와 함께 지정해야 필터가 적용된다", required = false) String startDate,
            @ToolParam(description = "수정일 검색 종료일 (yyyy-MM-dd, 해당 일 포함). startDate와 함께 지정해야 필터가 적용된다", required = false) String endDate,
            @ToolParam(description = "파일 형식 필터: DOCUMENT(doc, docx, hwp, hwpx, txt, rtf, odt, md, pages), "
                    + "SPREADSHEET(xls, xlsx, csv, tsv, ods, numbers), PRESENTATION(ppt, pptx, odp, key), PDF(pdf), "
                    + "IMAGE(jpg, jpeg, png, gif, bmp, webp, svg, tiff, tif, heic), NOTE(tfi — Thinkfree 노트). 이름 끝이 아니라 ext 기준. 미지정 시 전체", required = false) FileType fileType,
            @ToolParam(description = "정렬 필드: NAME(이름), SIZE(크기), UPDATE(수정일), OPEN(열람일), SHARE(공유일). 미지정 시 기본 정렬(SHARE 내림차순)", required = false) ShareTargetSortField sortBy,
            @ToolParam(description = "정렬 방향: ASC(오름차순), DESC(내림차순). sortBy만 지정하면 ASC로 정렬된다", required = false) SortOrder sortOrder,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 ID — 직전 응답의 nextCursor (마지막 resourceSeq). 첫 페이지면 생략", required = false) Long cursorId,
            @ToolParam(description = "다음 페이지 커서 값 — 직전 응답의 nextCursorValue (마지막 항목의 정렬 기준 값). 첫 페이지면 생략", required = false) String cursorValue,
            @ToolParam(description = "다음 페이지 커서 타입 — 직전 응답의 nextCursorType (마지막 항목의 resourceType). 첫 페이지면 생략", required = false) ResourceType cursorType) {

        log.info("[MCP] drive_list_shared_resources called. resourceType : {}, ownerId : {}, startDate : {}, endDate : {}, "
                        + "fileType : {}, sortBy : {}, sortOrder : {}, pageSize : {}, cursorId : {}, cursorValue : {}, cursorType : {}",
                resourceType, ownerId, startDate, endDate, fileType, sortBy, sortOrder, pageSize, cursorId, cursorValue, cursorType);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            var command = SharedListCommand.builder()
                    .resourceType(resourceType)
                    .ownerId(ownerId)
                    .startDate(McpToolParams.parseDate(startDate, "startDate"))
                    .endDate(McpToolParams.parseDate(endDate, "endDate"))
                    .fileType(fileType)
                    // 도메인은 sortBy·sortOrder가 둘 다 있어야 정렬을 적용 — 한쪽만 오면 짝을 기본값으로 채워 의도를 살린다
                    .sortBy(sortBy == null && sortOrder != null ? ShareTargetSortField.SHARE : sortBy)
                    .sortOrder(sortBy != null && sortOrder == null ? SortOrder.ASC : sortOrder)
                    .cursorValue(cursorValue)
                    .cursorId(cursorId)
                    .cursorType(cursorType)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            // webViewUrl 조립 재료 — 항목별 확정(파일=미리보기, 폴더=폴더 열람)은 컨버터가 한다
            String webBaseUrl = clientDomainService.getDomain(details.getTenantSeq());
            return sharedToolConverter.toListResponse(shareTargetUseCase.findAllPaginated(command, requestUserSeq), webBaseUrl, requestUserSeq);
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_share_resource",
            description = "내가 소유한 파일/폴더의 공유를 등록하거나 수정한다 — 공유가 없으면 새로 만들고 이미 있으면 수정하므로 "
                    + "등록/수정을 구분해 호출할 필요 없다. '김철수에게 공유해줘', '링크 공유 켜줘', '편집 권한 줘', "
                    + "'공유 대상 바꿔줘'처럼 공유 설정을 만들거나 바꿀 때 사용한다. "
                    + "공유 유형(shareType): SPECIFIC_USERS(특정 사용자만 — targets로 대상 지정), ORG_LINK(조직 내부 공개), "
                    + "PUBLIC_LINK(전체 공개 — 링크를 가진 누구나, 익명 포함). 허용되지 않는 유형을 요청하면 에러에 사용 가능한 "
                    + "유형 목록이 함께 반환되므로 사전 확인 없이 호출해도 된다. "
                    + "targets는 대상 '추가'가 아니라 원하는 최종 명단 전체로 치환된다 — 기존 대상을 유지한 채 추가하려면 "
                    + "drive_get_share_permissions로 현재 명단을 읽어 합쳐 보내고, 명단에서 빼고 보내면 그 사람은 해제된다. "
                    + "수정 시 shareType·기본 권한·targets를 생략하면 각각 기존 값이 유지된다 (targets에 빈 배열 []을 명시하면 대상 전체 해제). "
                    + "대상의 userSeq는 drive_search_share_users(이름/이메일 검색)나 기존 공유의 대상 목록(drive_get_share_permissions)에서 얻는다. "
                    + "기본 권한(canView~canShare)은 링크 공유(ORG_LINK/PUBLIC_LINK)의 접속자 권한이고, SPECIFIC_USERS는 대상별 권한(targets 항목)을 쓴다. "
                    + "링크 공유 성공 시 응답 linkUrl에 접속 링크가 포함된다 — 사용자가 링크를 원하면 이 값을 안내한다. "
                    + "소유자 전용 — 내 리소스가 아니면 에러가 반환되며, 공유받은 리소스의 대상 관리는 drive_update_share_targets를 사용한다. "
                    + "시스템 관리형 테넌트(SYSTEM_MANAGED)는 공유가 꺼져 있어 허용되는 공유 유형이 없으므로 '사용 가능한 유형: []' 에러로 거부된다. "
                    + "공유 자체를 완전히 없애려면 이 툴이 아니라 drive_unshare_resource를 사용한다"
                    + "(targets=[]는 대상만 비울 뿐 공유·링크는 유지된다).")
    public McpToolResult<ShareSaveToolResponse> shareResource(
            @ToolParam(description = "공유를 걸 내가 소유한 리소스의 resourceSeq") Long resourceSeq,
            @ToolParam(description = "공유 유형: SPECIFIC_USERS(특정 사용자만), ORG_LINK(조직 내부 공개), PUBLIC_LINK(전체 공개). "
                    + "신규 등록에는 필수, 수정 시 생략하면 기존 유형 유지", required = false) ShareType shareType,
            @ToolParam(description = "링크 공유의 기본 보기 권한. 수정 시 미지정이면 기존 값 유지, 링크 공유인데 그래도 값이 없으면 true", required = false) Boolean canView,
            @ToolParam(description = "링크 공유의 기본 편집 권한. 수정 시 미지정이면 기존 값 유지, 링크 공유인데 그래도 값이 없으면 false", required = false) Boolean canEdit,
            @ToolParam(description = "링크 공유의 기본 다운로드 권한. 수정 시 미지정이면 기존 값 유지, 링크 공유인데 그래도 값이 없으면 false", required = false) Boolean canDownload,
            @ToolParam(description = "링크 공유의 기본 업로드 권한 (폴더 + ORG_LINK에서만 허용). 수정 시 미지정이면 기존 값 유지, 링크 공유인데 그래도 값이 없으면 false", required = false) Boolean canUpload,
            @ToolParam(description = "링크 공유의 기본 공유 권한 (SPECIFIC_USERS 전용이라 링크 공유에선 불허). 수정 시 미지정이면 기존 값 유지, 링크 공유인데 그래도 값이 없으면 false", required = false) Boolean canShare,
            @ToolParam(description = "공유 대상의 최종 명단 전체 (치환 — 빠진 사람은 해제). 수정 시 생략하면 기존 명단 유지, "
                    + "빈 배열 []이면 대상 전체 해제. 항목: userSeq(필수)·canView(기본 true)·canEdit/canDownload/canUpload/canShare(기본 false)·"
                    + "sendEmail(신규 대상 이메일 알림, 기본 true)", required = false) List<ShareTargetToolItem> targets) {

        log.info("[MCP] drive_share_resource called. resourceSeq : {}, shareType : {}, canView : {}, canEdit : {}, "
                        + "canDownload : {}, canUpload : {}, canShare : {}, targets : {}",
                resourceSeq, shareType, canView, canEdit, canDownload, canUpload, canShare, targets);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 이 리소스 자체에 걸린 내 공유 유무로 등록(save)/수정(update) 라우팅 — 소유자 스코프 조회라 남의 리소스면 null,
            // 이어지는 유형 조회(availableShareTypes)의 리소스 조회에서 NOT_FOUND 인밴드로 귀결(웹 소유권 강제와 동형)
            var existing = shareFindService.findSharedResourceByResource(resourceSeq, requestUserSeq, false);
            boolean creating = existing == null;

            // 유형 사전 검증 재료 — 실패 시 사용 가능한 유형 목록을 에러 메시지에 실어 에이전트가 한 번에 교정하게 한다
            var types = shareUseCase.availableShareTypes(new ShareTypesCommand(resourceSeq, null), requestUserSeq);

            ShareType effectiveType = shareType != null ? shareType : (creating ? null : existing.getShareType());
            if (effectiveType == null) {
                throw new IllegalArgumentException("신규 공유 등록에는 shareType이 필수입니다. 사용 가능한 유형: " + allowedTypeNames(types));
            }
            verifyShareTypeAllowed(effectiveType, types);

            // 수정에서 생략된 값 채움용 현재 상태 (웹 PATCH는 전량 전달이 계약이라 부분 수정은 툴이 기존 값으로 보완한다)
            ShareDetailInfo current = creating ? null : shareUseCase.detail(new ShareDetailCommand(existing.getShareSeq(), null), requestUserSeq);

            // update의 targets는 치환(null이면 전부 해제)이라, 수정 시 미지정은 '기존 명단 유지'로 보완해 의도치 않은 해제를 막는다
            List<ShareTargetItemCommand> targetCommands;
            if (targets != null) {
                targetCommands = toTargetCommands(targets);
            } else {
                targetCommands = creating ? null : toKeepTargetCommands(current.targets());
            }

            // 명시값 > (수정이면) 기존 값 > 기본값(보기 true·나머지 false) 순으로 확정해 null을 남기지 않는다
            // — 링크 공유는 validator가 공유 행 권한 5종의 null을 거부(웹은 프론트가 전량 전송)하고,
            // 기존 값도 웹 생성 SPECIFIC 공유나 유형 전환(SPECIFIC→링크)에선 null이라 보정이 필요하다.
            // current.can*()은 Boolean(null 가능)이므로 단락 평가·equals로 언박싱 NPE를 피한다.
            boolean cv = canView != null ? canView : creating || current.canView() == null || current.canView();
            boolean ce = canEdit != null ? canEdit : !creating && Boolean.TRUE.equals(current.canEdit());
            boolean cd = canDownload != null ? canDownload : !creating && Boolean.TRUE.equals(current.canDownload());
            boolean cu = canUpload != null ? canUpload : !creating && Boolean.TRUE.equals(current.canUpload());
            boolean cs = canShare != null ? canShare : !creating && Boolean.TRUE.equals(current.canShare());

            long shareSeq;
            if (creating) {
                var info = shareUseCase.save(new ShareSaveCommand(resourceSeq, effectiveType, cv, ce, cd, cu, cs, targetCommands),
                        requestUserSeq, details.getUserId(), details.getUsername());
                shareSeq = info.shareSeq();
            } else {
                shareUseCase.update(existing.getShareSeq(), new ShareUpdateCommand(effectiveType, cv, ce, cd, cu, cs, targetCommands),
                        requestUserSeq, details.getUserId(), details.getUsername());
                shareSeq = existing.getShareSeq();
            }

            // 링크 공유는 접속 링크까지 응답에 포함 — 웹 '링크 복사'와 동일 생성 경로(토큰 없으면 생성)
            String linkUrl = effectiveType.isPublic() ? resourceShareLinkService.getOrCreateLinkUrl(requestUserSeq, resourceSeq) : null;

            return new ShareSaveToolResponse(shareSeq, resourceSeq, effectiveType.name(), creating, linkUrl);
        });
    }
    
    @Tool(name = "drive_search_share_users",
            description = "공유 대상으로 추가할 수 있는 사용자를 이름 또는 이메일로 검색한다. '김철수에게 공유해줘'처럼 대상이 "
                    + "이름/이메일로 주어지면 이 툴로 userSeq를 알아낸 뒤 drive_share_resource나 drive_update_share_targets의 "
                    + "targets[].userSeq로 넘긴다. 결과가 여러 명(동명이인)이면 임의로 고르지 말고 후보(이름·이메일)를 사용자에게 "
                    + "보여주고 확인받는다. 검색어는 부분 일치이며 공유 소유자(sharedByUserSeq, 미지정 시 요청자 본인)는 결과에서 제외된다. "
                    + "공유 기능이 꺼져 있거나 특정 사용자 공유가 허용되지 않는 환경이면 에러가 반환된다.")
    public McpToolResult<ShareUserSearchToolResponse> searchShareUsers(
            @ToolParam(description = "검색어 — 사용자 이름 또는 이메일(부분 일치)") String searchKeyword,
            @ToolParam(description = "공유받은 리소스의 대상을 관리하는 경우 그 소유자의 userSeq (공유 정책을 소유자 기준으로 판정). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq) {

        log.info("[MCP] drive_search_share_users called. searchKeyword : {}, sharedByUserSeq : {}", searchKeyword, sharedByUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.requireText(searchKeyword, "searchKeyword");

            // 웹 /shares/users/search(shareSeq 없는 경로)는 aspect가 조기 반환해 별도 권한 검사가 없다 —
            // usecase의 공유 설정 검증(shareEnabled/specificUsersAllowed)이 계약, 동형 유지
            var infos = shareUseCase.searchUsers(new ShareUserSearchCommand(searchKeyword, sharedByUserSeq), requestUserSeq);
            var users = infos.stream()
                    .map(u -> new ShareUserSearchToolResponse.ShareUserToolItem(
                            u.userSeq(), u.userId(), u.userName(),
                            u.userStatus() == null ? null : u.userStatus().name(),
                            u.tenantSeq(), u.tenantName()))
                    .toList();
            return new ShareUserSearchToolResponse(users);
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_update_share_targets",
            description = "공유의 대상 명단을 교체한다 — 다른 사용자가 공유해 준 리소스에 내가 공유(canShare) 권한을 갖고 있을 때 "
                    + "대상을 추가/제거/권한 변경하는 툴이다. 내가 소유한 리소스라면 이 툴 대신 drive_share_resource를 사용한다"
                    + "(공유 유형·기본 권한까지 함께 다룰 수 있다). "
                    + "targets는 대상 '추가'가 아니라 원하는 최종 명단 전체로 치환된다 — 기존 대상을 유지하려면 "
                    + "drive_get_share_permissions로 현재 명단을 읽어 합쳐 보내고, 명단에서 빼면 그 사람은 해제된다. "
                    + "빈 배열 []이면 대상 전체 해제(공유 자체는 유지 — 공유를 완전히 없애는 건 소유자가 drive_unshare_resource로 한다). "
                    + "주의: 자기 자신의 canShare를 false로 바꾸거나 자신을 명단에서 빼는 것도 그대로 저장되며, "
                    + "그 뒤에는 본인이 이 공유의 명단을 더 이상 수정할 수 없게 된다(복구는 소유자만 가능) — "
                    + "사용자가 명시적으로 요청한 경우가 아니면 자신의 canShare를 회수하지 않는다. "
                    + "shareSeq와 sharedByUserSeq는 목록 툴 항목이나 drive_get_share_permissions에서 얻는다. "
                    + "공유(canShare) 권한이 없으면 에러가 반환된다. "
                    + SHARE_OFF_RESTRICTION
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> updateShareTargets(
            @ToolParam(description = "대상을 교체할 공유의 shareSeq (목록 툴 항목 또는 drive_get_share_permissions에서 획득)") Long shareSeq,
            @ToolParam(description = "공유받은 리소스일 때 그 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 리소스면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "공유 대상의 최종 명단 전체 (치환 — 빠진 사람은 해제, 빈 배열 []은 대상 전체 해제). "
                    + "항목: userSeq(필수)·canView(기본 true)·canEdit/canDownload/canUpload/canShare(기본 false)·"
                    + "sendEmail(신규 대상 이메일 알림, 기본 true)") List<ShareTargetToolItem> targets) {

        log.info("[MCP] drive_update_share_targets called. shareSeq : {}, sharedByUserSeq : {}, targets : {}",
                shareSeq, sharedByUserSeq, targets);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(shareSeq, "shareSeq");
            // 웹 계약(null=전체 해제)은 LLM 실수에 취약해 툴은 명시를 강제한다 — 전체 해제는 빈 배열로 표현
            McpToolParams.require(targets, "targets");

            // 웹 PUT targets의 @CheckSharePermission(기본 SHARE)과 동일한 검사 — 소유자 해석(ss 없으면 요청자)도 동일
            int ownerSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
            sharePermissionService.authorize(SharePermissionType.SHARE, shareSeq, ownerSeq, requestUserSeq);

            var command = new ShareTargetReplaceCommand(sharedByUserSeq, toTargetCommands(targets));
            shareTargetUseCase.replaceShareTargets(shareSeq, command, requestUserSeq, details.getUserId(), details.getUsername());
            return null; // 웹 응답(ApiResponse.created(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_unshare_resource",
            description = "리소스의 공유를 완전히 해제한다 — 공유 설정·접속 링크·대상 명단이 모두 삭제되며 되돌릴 수 없다. "
                    + "'공유 끊어줘', '공유 해제해줘', '링크 공유 없애줘'처럼 공유 자체를 없앨 때 사용한다. "
                    + "파괴적 동작이므로 사용자가 명시적으로 해제를 요청한 경우에만, 어떤 리소스의 공유인지 실행 전에 "
                    + "사용자에게 확인받고 호출한다. 대상만 비우고 공유(링크)는 남기려면 이 툴이 아니라 "
                    + "drive_share_resource(targets=[])를 사용한다. 소유자 전용 — 내 공유가 아니면 에러가 반환된다. "
                    + SHARE_OFF_RESTRICTION
                    + "shareSeq는 목록 툴 항목이나 drive_get_share_permissions에서 얻는다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> unshareResource(
            @ToolParam(description = "해제할 공유의 shareSeq (목록 툴 항목 또는 drive_get_share_permissions에서 획득)") Long shareSeq) {

        log.info("[MCP] drive_unshare_resource called. shareSeq : {}", shareSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(shareSeq, "shareSeq");

            // 웹 DELETE /shares/{shareSeq} 1:1 — 컨트롤러에 aspect 검사가 없고, usecase의 소유자 스코프 조회
            // (findSharedResourceByShare)가 소유권을 강제한다(남의 공유면 NOT_FOUND 인밴드). 해제 알림·감사는 usecase 내장
            shareUseCase.delete(shareSeq, requestUserSeq, details.getUsername());
            return null; // 웹 응답(본문 없음) 동형
        });
    }

    // 툴 입력 대상 → 커맨드. 웹은 권한 5종이 @NotNull이지만 LLM 입력은 생략이 잦아 기본값을 채운다
    // (canView는 모든 권한의 선행 조건이라 true, 나머지 권한 false, 신규 대상 이메일 알림 true).
    private List<ShareTargetItemCommand> toTargetCommands(List<ShareTargetToolItem> targets) {
        return targets.stream().map(t -> {
            McpToolParams.require(t.userSeq(), "targets[].userSeq");
            return new ShareTargetItemCommand(
                    t.userSeq(),
                    t.canView() != null ? t.canView() : Boolean.TRUE,
                    Boolean.TRUE.equals(t.canEdit()),
                    Boolean.TRUE.equals(t.canDownload()),
                    Boolean.TRUE.equals(t.canUpload()),
                    Boolean.TRUE.equals(t.canShare()),
                    t.sendEmail() != null ? t.sendEmail() : Boolean.TRUE);
        }).toList();
    }

    // 수정 시 targets 미지정 = 기존 명단 그대로 유지 (유지 대상에겐 알림 메일을 다시 보내지 않는다)
    private List<ShareTargetItemCommand> toKeepTargetCommands(List<ShareDetailInfo.ShareTargetItem> targets) {
        if (targets == null) {
            return List.of();
        }
        return targets.stream()
                .map(t -> new ShareTargetItemCommand(t.userSeq(), t.canView(), t.canEdit(), t.canDownload(), t.canUpload(), t.canShare(), Boolean.FALSE))
                .toList();
    }

    // 유형 사전 검증 — usecase 내부 검증과 중복이지만, 에러 메시지에 사용 가능한 유형을 실어주기 위한 툴 계층 보완
    private void verifyShareTypeAllowed(ShareType type, ShareTypesInfo types) {
        boolean allowedByPolicy = switch (type) {
            case SPECIFIC_USERS -> types.allowSpecificUsers();
            case ORG_LINK -> types.allowOrgLink();
            case PUBLIC_LINK -> types.allowPublic();
        };
        if (!allowedByPolicy) {
            throw new IllegalArgumentException("공유 유형 " + type.name() + "은(는) 현재 환경 설정상 허용되지 않습니다. 사용 가능한 유형: " + allowedTypeNames(types));
        }
        // 상/하위 폴더에 이미 공유가 있으면 유형이 고정된다(editable=false) — 고정 유형 외 요청이면 안내
        if (!types.editable() && types.shareTypes().stream().noneMatch(item -> item.code().equals(type.name()))) {
            throw new IllegalArgumentException("상위 또는 하위에 이미 공유가 있어 공유 유형을 바꿀 수 없습니다. 사용 가능한 유형: "
                    + types.shareTypes().stream().map(ShareTypesInfo.ShareTypesItemInfo::code).toList());
        }
    }

    private List<String> allowedTypeNames(ShareTypesInfo types) {
        List<String> names = new ArrayList<>();
        if (types.allowSpecificUsers()) {
            names.add(ShareType.SPECIFIC_USERS.name());
        }
        if (types.allowOrgLink()) {
            names.add(ShareType.ORG_LINK.name());
        }
        if (types.allowPublic()) {
            names.add(ShareType.PUBLIC_LINK.name());
        }
        return names;
    }

}

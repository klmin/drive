package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.resource.info.ResourceDetailInfo;
import com.thinkfree.drive.application.resource.info.ResourceListInfo;
import com.thinkfree.drive.mcp.tool.response.ResourceDetailToolResponse;
import com.thinkfree.drive.mcp.tool.response.ResourceSearchToolResponse;
import com.thinkfree.drive.mcp.util.McpUrls;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 ResponseConverter 등가물.
 * 항목 필드는 ResourceListInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 * 날짜 String 변환은 ToolDateTimeSupport 공용 (웹 @DriveDateTimeFormat 동일 포맷).
 * webViewUrl 조립은 어댑터(툴)가 넘긴 웹 베이스 URL로 여기서 항목별 확정한다 — 파일은 미리보기, 폴더는 폴더 열람 링크.
 */
@Mapper(componentModel = "spring")
public interface ResourceToolConverter extends ToolDateTimeSupport {

    @Mapping(target = "webViewUrl", source = "webViewUrl")
    ResourceSearchToolResponse.ResourceItem toSearchItem(ResourceListInfo info, String webViewUrl);

    @Mapping(target = "webViewUrl", source = "webViewUrl")
    ResourceDetailToolResponse toDetailResponse(ResourceDetailInfo info, String webViewUrl);

    default ResourceSearchToolResponse toSearchResponse(CursorPageInfo<ResourceListInfo> pageInfo, String webBaseUrl,
                                                        int requestUserSeq) {
        // ss는 접근 스코프(행의 userSeq) — 표시용 ownerSeq는 공유 폴더 내 제3자 파일에서 스코프와 어긋난다
        List<ResourceSearchToolResponse.ResourceItem> items = pageInfo.list().stream()
                .map(info -> toSearchItem(info,
                        McpUrls.webViewUrl(webBaseUrl, info.resourceType(), info.resourceSeq(), info.userSeq(), requestUserSeq)))
                .toList();
        return new ResourceSearchToolResponse(items, pageInfo.pageSize(),
                pageInfo.nextCursor(), toCursorValue(pageInfo.nextCursorValue()), pageInfo.nextCursorType(), pageInfo.hasNext());
    }

}

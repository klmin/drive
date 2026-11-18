package com.thinkfree.drive.api.v1.recent.converter;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.v1.recent.response.RecentListResponse;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.recent.info.RecentListInfo;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 data → Info 조립은 RecentInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface RecentResponseConverter {

    List<RecentListResponse> toListResponse(List<RecentListInfo> infos);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }

}

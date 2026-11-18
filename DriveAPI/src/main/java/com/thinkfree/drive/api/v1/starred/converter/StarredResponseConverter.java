package com.thinkfree.drive.api.v1.starred.converter;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.starred.info.StarredListInfo;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 data → Info 조립은 StarredInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface StarredResponseConverter {

    List<StarredListResponse> toListResponse(List<StarredListInfo> infos);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }

}

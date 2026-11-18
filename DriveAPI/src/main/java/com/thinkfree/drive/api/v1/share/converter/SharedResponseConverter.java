package com.thinkfree.drive.api.v1.share.converter;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.v1.share.response.SharedListResponse;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.share.info.SharedListInfo;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 data → Info 조립은 SharedInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface SharedResponseConverter {

    List<SharedListResponse> toListResponse(List<SharedListInfo> infos);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }

}

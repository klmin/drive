package com.thinkfree.drive.api.v1.superadmin.apikey.converter;

import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.application.apikey.info.ApiKeyListInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SuperAdminApiKeyResponseConverter {

    List<ApiKeyListResponse> toListResponse(List<ApiKeyListInfo> infos);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }
}

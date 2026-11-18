package com.thinkfree.drive.api.v1.admin.converter;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.v1.admin.response.AdminResponse;
import com.thinkfree.drive.application.admin.info.AdminUserDetailInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 data/entity → Info 조립은 application의 AdminInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminResponseConverter {

    @Mapping(target = "registerDate", source = "registerDate", qualifiedByName = "formatAdminDate")
    @Mapping(target = "updateDate",   source = "updateDate",   qualifiedByName = "formatAdminDate")
    AdminResponse toAdminResponse(AdminUserDetailInfo info);

    // 구 AdminResponse.of의 날짜 포맷 유지 (웹 응답 무변경)
    @Named("formatAdminDate")
    default String formatAdminDate(Instant date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy. M. d. HH:mm");
        return date != null ? DriveTime.toLocal(date).format(formatter) : null;
    }

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }

}

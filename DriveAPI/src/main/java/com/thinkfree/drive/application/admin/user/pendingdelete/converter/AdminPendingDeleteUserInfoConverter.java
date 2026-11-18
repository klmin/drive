package com.thinkfree.drive.application.admin.user.pendingdelete.converter;

import com.thinkfree.drive.application.admin.user.pendingdelete.info.AdminPendingDeleteUserListInfo;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 도메인 data → application Info 조립 전담. Info → 웹 응답 변환은 api.v1 AdminPendingDeleteUserResponseConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminPendingDeleteUserInfoConverter {

    List<AdminPendingDeleteUserListInfo> toListInfos(List<UserPendingDeleteData> list);
}

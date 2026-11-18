package com.thinkfree.drive.application.admin.converter;

import com.thinkfree.drive.application.admin.info.AdminUserDetailInfo;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.enums.RoleType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 도메인 data/entity → application Info 변환 전담. Info → 웹 응답 변환은 api.v1 AdminResponseConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminInfoConverter {


    @Mapping(target = "isMfaEnabled", expression = "java(user.getMfaStatus().isEnabled())")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "isProtected", expression = "java(Boolean.TRUE.equals(user.getIsProtected()))")
    AdminUserDetailInfo toDetailInfo(UserDetailWithRolesData user, List<RoleType> roles);

}

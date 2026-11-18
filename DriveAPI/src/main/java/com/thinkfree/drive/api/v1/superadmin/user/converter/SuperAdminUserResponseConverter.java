package com.thinkfree.drive.api.v1.superadmin.user.converter;

import com.thinkfree.drive.api.v1.superadmin.user.response.SuperAdminUserSearchResponse;
import com.thinkfree.drive.user.data.UserSearchData;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SuperAdminUserResponseConverter {

    List<SuperAdminUserSearchResponse> toSearchResponse(List<UserSearchData> data);
}

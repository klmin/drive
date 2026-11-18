package com.thinkfree.drive.api.v1.user.converter;

import com.thinkfree.drive.api.v1.user.response.UserSearchResponse;
import com.thinkfree.drive.user.data.UserData;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserResponseConverter {
    List<UserSearchResponse> toUserSearchResponseList(List<UserData> userData);
}

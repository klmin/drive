package com.thinkfree.drive.api.v1.superadmin.sharesetting.converter;

import com.thinkfree.drive.api.v1.superadmin.sharesetting.request.SuperAdminShareSettingSaveRequest;
import com.thinkfree.drive.share.setting.dto.ShareSettingUpsertDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SuperAdminShareSettingRequestConverter {

    /**
     * 전체적용 + 공유 on 요청을 글로벌 공유 설정 DTO 로 변환.
     * 글로벌 행이므로 tenantSeq 는 null, isEnforced 는 항상 true.
     */
    @Mapping(target = "tenantSeq", ignore = true)
    @Mapping(target = "isEnforced", constant = "true")
    @Mapping(target = "isExternalShareAllowed", source = "esa")
    @Mapping(target = "isSpecificUsersAllowed", source = "sua")
    @Mapping(target = "isOrgLinkAllowed", source = "ola")
    @Mapping(target = "isPublicAllowed", source = "pa")
    ShareSettingUpsertDto toGlobalUpsertDto(SuperAdminShareSettingSaveRequest request);
}

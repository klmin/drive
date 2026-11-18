package com.thinkfree.drive.api.v1.admin.sharesetting.converter;

import com.thinkfree.drive.api.v1.admin.sharesetting.request.AdminShareSettingSaveRequest;
import com.thinkfree.drive.share.setting.dto.ShareSettingUpsertDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminShareSettingRequestConverter {

    /**
     * 공유 on 요청을 테넌트 공유 설정 DTO 로 변환.
     * 테넌트 행이므로 isEnforced 는 항상 false('N', CHECK 제약).
     */
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "isEnforced", constant = "false")
    @Mapping(target = "isExternalShareAllowed", source = "request.esa")
    @Mapping(target = "isSpecificUsersAllowed", source = "request.sua")
    @Mapping(target = "isOrgLinkAllowed", source = "request.ola")
    @Mapping(target = "isPublicAllowed", source = "request.pa")
    ShareSettingUpsertDto toTenantUpsertDto(AdminShareSettingSaveRequest request, Integer tenantSeq);
}

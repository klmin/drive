package com.thinkfree.drive.share.setting.converter;

import com.thinkfree.drive.share.setting.dto.ShareSettingUpsertDto;
import com.thinkfree.drive.share.setting.entity.ShareSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShareSettingConverter {

    ShareSetting toEntity(ShareSettingUpsertDto dto);

    /**
     * 글로벌 enforce 전용 DTO. enforce 값만 인자로 받고 상세 flag 는 기본값으로 채운다.
     * (행이 없을 때만 이 기본 flag 로 신규 생성되고, 행이 있으면 enforce 만 갱신된다)
     */
    @Mapping(target = "tenantSeq", ignore = true)
    @Mapping(target = "isEnforced", source = "isEnforced")
    @Mapping(target = "isExternalShareAllowed", constant = "false")
    @Mapping(target = "isSpecificUsersAllowed", constant = "true")
    @Mapping(target = "isOrgLinkAllowed", constant = "true")
    @Mapping(target = "isPublicAllowed", constant = "false")
    ShareSettingUpsertDto toGlobalEnforceDto(Boolean isEnforced);

    /** 테넌트 기본 공유 설정 DTO (개별적용 + 기본 권한). */
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "isEnforced", constant = "false")
    @Mapping(target = "isExternalShareAllowed", constant = "false")
    @Mapping(target = "isSpecificUsersAllowed", constant = "true")
    @Mapping(target = "isOrgLinkAllowed", constant = "true")
    @Mapping(target = "isPublicAllowed", constant = "false")
    ShareSettingUpsertDto toTenantDefaultDto(Integer tenantSeq);

    /**
     * 글로벌 행 저장용 DTO 로 정규화. tenantSeq 는 null 강제, null flag 는 false 로 보정.
     */
    @Mapping(target = "tenantSeq", ignore = true)
    @Mapping(target = "isEnforced", expression = "java(Boolean.TRUE.equals(src.getIsEnforced()))")
    @Mapping(target = "isExternalShareAllowed", expression = "java(Boolean.TRUE.equals(src.getIsExternalShareAllowed()))")
    @Mapping(target = "isSpecificUsersAllowed", expression = "java(Boolean.TRUE.equals(src.getIsSpecificUsersAllowed()))")
    @Mapping(target = "isOrgLinkAllowed", expression = "java(Boolean.TRUE.equals(src.getIsOrgLinkAllowed()))")
    @Mapping(target = "isPublicAllowed", expression = "java(Boolean.TRUE.equals(src.getIsPublicAllowed()))")
    ShareSettingUpsertDto toGlobalDto(ShareSettingUpsertDto src);

    /**
     * 테넌트 행 저장용 DTO 로 정규화. isEnforced 는 항상 false('N') 강제, null flag 는 false 로 보정.
     */
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "isEnforced", constant = "false")
    @Mapping(target = "isExternalShareAllowed", expression = "java(Boolean.TRUE.equals(src.getIsExternalShareAllowed()))")
    @Mapping(target = "isSpecificUsersAllowed", expression = "java(Boolean.TRUE.equals(src.getIsSpecificUsersAllowed()))")
    @Mapping(target = "isOrgLinkAllowed", expression = "java(Boolean.TRUE.equals(src.getIsOrgLinkAllowed()))")
    @Mapping(target = "isPublicAllowed", expression = "java(Boolean.TRUE.equals(src.getIsPublicAllowed()))")
    ShareSettingUpsertDto toTenantDto(ShareSettingUpsertDto src, Integer tenantSeq);
}

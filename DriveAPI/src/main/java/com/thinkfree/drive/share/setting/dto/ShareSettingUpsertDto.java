package com.thinkfree.drive.share.setting.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 공유 설정 등록/수정용 DTO. 슈퍼어드민(글로벌)/테넌트 공통.
 * tenantSeq == null 이면 글로벌 행을 의미한다.
 */
@Getter
@Builder
public class ShareSettingUpsertDto {
    private Integer tenantSeq;
    private Boolean isEnforced;
    private Boolean isExternalShareAllowed;
    private Boolean isSpecificUsersAllowed;
    private Boolean isOrgLinkAllowed;
    private Boolean isPublicAllowed;
}

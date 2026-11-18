package com.thinkfree.drive.api.v1.admin.response;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 사용자 단건 조회 응답 — application AdminUserDetailInfo에서 AdminResponseConverter가 조립한다
 * (날짜는 컨버터가 "yyyy. M. d. HH:mm" 포맷 String으로 변환 — 구 AdminResponse.of 동작 유지).
 */
@Builder
@Getter
public class AdminResponse {
    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus status;
    private Long totalQuotaByte;
    private Long useQuotaByte;
    private List<RoleType> roles;
    private Integer registerUserSeq;
    private String registerDate;
    private Integer updateUserSeq;
    private String updateDate;
    private String countryCode;
    private String phoneNumber;
    private String profileImageKey;
    private Boolean isMfaEnabled;
    /** 보호 계정 — 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경이 거부된다(USER_034). 본인 비밀번호 변경은 SYSTEM_MANAGED 테넌트에서만 거부(TENANT_011) */
    private Boolean isProtected;
}

package com.thinkfree.drive.api.v1.user.response;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Builder
@Getter
public class UserResponse {
    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus status;
    private Long totalQuotaByte;
    private Long useQuotaByte;
    private List<RoleType> roles;       // 보유 역할 전부(tb_user_role) — 토큰의 로그인 역할과 별개
    private Integer registerUserSeq;
    private String registerDate;
    private Integer updateUserSeq;
    private String updateDate;
    private UserLanguage userLanguage;
    private String countryCode;
    private String phoneNumber;
    private DefaultLandingPage defaultLandingPage;
    private String profileImageKey;
    private Integer tenantSeq;
    /** 본인 비밀번호 변경·재설정 링크 가능 여부 — SYSTEM_MANAGED 보호 계정이면 false */
    private Boolean isSelfPasswordChangeable;

    public static UserResponse of(UserDetailData entity, List<RoleType> roles, boolean isSelfPasswordChangeable) {
        return UserResponse.builder()
                .userSeq(entity.getUserSeq())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .status(entity.getStatus())
                .totalQuotaByte(entity.getTotalQuotaByte())
                .useQuotaByte(entity.getUseQuotaByte())
                .roles(roles)
                .registerUserSeq(entity.getRegisterUserSeq())
                .registerDate(formatDate(entity.getRegisterDate()))
                .updateUserSeq(entity.getUpdateUserSeq())
                .updateDate(formatDate(entity.getUpdateDate()))
                .userLanguage(entity.getUserLanguage())
                .countryCode(entity.getCountryCode())
                .phoneNumber(entity.getPhoneNumber())
                .defaultLandingPage(entity.getDefaultLandingPage())
                .profileImageKey(entity.getProfileImageKey())
                .tenantSeq(entity.getTenantSeq())
                .isSelfPasswordChangeable(isSelfPasswordChangeable)
                .build();
    }

    private static String formatDate(Instant date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy. M. d. HH:mm");
        return date != null ? DriveTime.toLocal(date).format(formatter) : null;
    }
}
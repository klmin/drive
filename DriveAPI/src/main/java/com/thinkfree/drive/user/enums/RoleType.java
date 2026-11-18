package com.thinkfree.drive.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum RoleType {
    SUPER_ADMIN("ROLE_SUPER_ADMIN", "슈퍼 관리자"),
    ADMIN("ROLE_ADMIN", "관리자"),
    USER("ROLE_USER", "사용자"),
    ANONYMOUS("ROLE_ANONYMOUS", "익명 사용자");

    private final String authority;
    private final String title;
    private static final Map<String, RoleType> AUTHORITY_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(RoleType::getAuthority, Function.identity()));
    
    public static RoleType fromAuthority(String authority) {
        return AUTHORITY_MAP.get(authority);
    }

    /**
     * 계정 등록 API가 만들 수 있는 role 허용 목록 — 테넌트 관리자 등록 경로의 상한.
     * SUPER_ADMIN(테넌트 관리자가 자기보다 높은 role 생성 = 권한 상승)·ANONYMOUS는 등록 대상이 아니다.
     */
    public boolean isRegistrable() {
        return this == USER || this == ADMIN;
    }

    /** 단독 역할 — 시드로만 존재하고 다른 역할과 같이 가질 수 없다 (SUPER_ADMIN, ANONYMOUS) */
    public boolean isExclusive() {
        return this == SUPER_ADMIN || this == ANONYMOUS;
    }

    /** 등록·수정 요청 역할 묶음이 부여 가능한지 — 1개 이상, 전부 USER·ADMIN */
    public static boolean isAssignable(Collection<RoleType> roles) {
        return roles != null && !roles.isEmpty() && roles.stream().allMatch(role -> role != null && role.isRegistrable());
    }

}

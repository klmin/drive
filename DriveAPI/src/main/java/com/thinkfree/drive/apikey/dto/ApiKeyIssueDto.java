package com.thinkfree.drive.apikey.dto;

import com.thinkfree.drive.user.enums.RoleType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 발급 요청 원본 — role 별 컨트롤러가 공통으로 넘기는 형태.
 * 검증을 거쳐 {@link ApiKeySaveDto}(저장 확정값)가 된다.
 */
@Getter
@Builder
public class ApiKeyIssueDto {

    private String keyName;
    private String keyPurpose;
    private Instant expireDate;
    /** 요청으로 받은 scope 값 목록 (예: api:read). 대상 role 과 무관하게 필수 */
    private List<String> scopes;

    /** 키가 속할 테넌트. 슈퍼관리자만 지정하고, 나머지는 로그인 사용자의 테넌트를 쓴다 */
    private Integer tenantSeq;
    /** 대상 계정. 미지정이면 로그인 사용자 본인 */
    private Integer targetUserSeq;
    /** 키 역할 지정 — 슈퍼관리자가 다른 계정에 발급할 때만(대상 보유 역할 중 USER·ADMIN). 그 외 경로는 null */
    private RoleType requestedRoleType;
    /** 보호 키 요청 — 슈퍼관리자 경로만 true, 그 외 null */
    private Boolean isProtected;

    private Integer loginUserSeq;
    private RoleType loginRoleType;
    private Integer loginTenantSeq;
}

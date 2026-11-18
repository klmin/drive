package com.thinkfree.drive.apikey.dto;

import com.thinkfree.drive.user.enums.RoleType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** 저장 확정값 — 검증을 마친 뒤의 값만 담는다. 요청 원본은 {@link ApiKeyIssueDto} 다 */
@Getter
@Builder
public class ApiKeySaveDto {

    private Integer tenantSeq;
    /** 키가 행세할 대상 사용자 */
    private Integer userSeq;
    private String keyName;
    private String keyPurpose;
    /** 대상 계정의 실제 role */
    private RoleType roleType;
    /** 공백 구분 문자열. role=USER 가 아니면 null */
    private String scope;
    /** 만료일. 무기한은 허용하지 않는다 */
    private Instant expireDate;
    /** 보호 키 여부 — null 없음(tb_api_key.is_protected NOT NULL) */
    private Boolean isProtected;
}

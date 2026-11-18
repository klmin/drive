package com.thinkfree.drive.apikey.data;

import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor // 공개 전체 생성자(18컬럼)는 목록 JPQL 생성자 식용(ApiKeyJpaRepository#LIST_HEAD)
public class ApiKeyData {
    private Integer apiKeySeq;
    private Integer tenantSeq;
    /** 키가 행세하는 대상 사용자. 인증 시 actor 가 된다 */
    private Integer userSeq;
    /** 목록 조회에서만 채워진다 (대상 사용자 표시용) */
    private String userId;
    private String userName;
    /** 슈퍼관리자 목록 조회에서만 채워진다 (테넌트 무관 전체라 구분이 필요) */
    private String tenantName;
    private String keyName;
    private String keyPurpose;
    private String apiKey;
    /** 키에 부여된 권한. 발급 시 대상 계정의 실제 role 과 일치해야 한다 */
    private RoleType roleType;
    /** 공백 구분 문자열. role=USER 키에만 값이 있다 */
    private String scope;
    private ApiKeyStatus status;
    private Instant expireDate;
    /** 목록 조회에서만 채워진다. DB NOW() 기준 — 인증 판정과 같은 시계 */
    private Boolean isExpired;
    private Instant lastUsedDate;
    private Integer registerUserSeq;
    private Instant registerDate;
    /** 보호 키 — 만료일·상태·재발급은 슈퍼관리자 경로에서만 */
    private Boolean isProtected;

    /** JPQL 생성자 식 — 공통 14컬럼(단건·인증 조회). 목록 전용 필드(userId·userName·tenantName·isExpired)는 비운다 */
    public ApiKeyData(Integer apiKeySeq, Integer tenantSeq, Integer userSeq, String keyName, String keyPurpose, String apiKey,
                      RoleType roleType, String scope, ApiKeyStatus status, Instant expireDate, Instant lastUsedDate,
                      Integer registerUserSeq, Instant registerDate, Boolean isProtected) {
        this(apiKeySeq, tenantSeq, userSeq, null, null, null, keyName, keyPurpose, apiKey, roleType, scope, status,
                expireDate, null, lastUsedDate, registerUserSeq, registerDate, isProtected);
    }
}

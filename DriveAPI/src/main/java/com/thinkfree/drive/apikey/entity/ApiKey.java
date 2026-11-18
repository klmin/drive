package com.thinkfree.drive.apikey.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.global.jpa.audit.*;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.type.YesNoConverter;

import java.time.Instant;

/**
 * tb_api_key — INSERT 는 save(IDENTITY), 만료일·상태·재발급은 변경 감지({@link #changeExpireDate}·{@link #changeStatus}·{@link #reissue} — Service 가 쓰기 리포 {@code findById} 로 로드해 범위를 판정한 관리 엔티티에서만 호출, W5).
 * {@link DynamicUpdate} 로 바뀐 컬럼만 UPDATE — 인증마다 JPQL 로 갱신되는 last_used_date 를 덮어쓰지 않는다. 감사 컬럼은 Auditing(직접 바꾸지 않음).
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "tb_api_key")
public class ApiKey extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer apiKeySeq;
    private Integer tenantSeq;
    /** 키가 행세하는 대상 사용자. 인증 시 actor 가 된다 */
    private Integer userSeq;
    private String keyName;
    private String keyPurpose;
    private String apiKey;
    @Enumerated(EnumType.STRING)
    private RoleType roleType;
    /** 공백 구분 문자열. 문자열 조립은 {@link ApiKeyScope#join} 으로만 한다. role=USER 키에만 값이 있다 */
    private String scope;
    @Enumerated(EnumType.STRING)
    private ApiKeyStatus status;
    private Instant expireDate;
    private Instant lastUsedDate;
    /** 보호 키 — 슈퍼관리자가 발급 때(pt) 정하거나 보호 설정·해제 API 로 바꾼다. true 면 만료일·상태·재발급은 슈퍼관리자 경로에서만(테넌트 유형 무관) */
    @Builder.Default
    @Convert(converter = YesNoConverter.class)
    private Boolean isProtected = false;

    public void changeExpireDate(Instant expireDate) {
        this.expireDate = expireDate;
    }

    public void changeStatus(ApiKeyStatus status) {
        this.status = status;
    }

    /** 보호 키 설정·해제 — 슈퍼관리자 경로에서만 호출 */
    public void changeProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }

    /** 재발급 — 새 키 값과 상태(보통 ACTIVE) */
    public void reissue(String apiKey, ApiKeyStatus status) {
        this.apiKey = apiKey;
        this.status = status;
    }
}

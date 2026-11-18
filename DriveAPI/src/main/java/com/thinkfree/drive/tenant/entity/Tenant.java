package com.thinkfree.drive.tenant.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.global.jpa.audit.*;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicUpdate;


@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "tb_tenant")
public class Tenant extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer tenantSeq;
    private String tenantName;
    private String domain;
    private String subdomain;
    private Integer baseDomainSeq;
    @Enumerated(EnumType.STRING)
    private TenantType tenantType;
    /** 생성 후 변경 불가(유형과 같이 변경 메서드 없음) */
    @Enumerated(EnumType.STRING)
    private TenantUserMode userMode;
    @Enumerated(EnumType.STRING)
    private TenantStatus status;
    @Enumerated(EnumType.STRING)
    private LoginType loginType;

    /** 이름·도메인 수정 — 변경 감지 전용(Service 가 쓰기 리포에서 로드한 관리 엔티티에 호출, 감사는 Auditing) */
    public void changeProfile(String tenantName, String domain, String subdomain, Integer baseDomainSeq) {
        this.tenantName = tenantName;
        this.domain = domain;
        this.subdomain = subdomain;
        this.baseDomainSeq = baseDomainSeq;
    }

    /** 베이스 도메인 재바인딩(이름은 그대로) — 변경 감지 전용 */
    public void changeDomainBinding(String domain, String subdomain, Integer baseDomainSeq) {
        this.domain = domain;
        this.subdomain = subdomain;
        this.baseDomainSeq = baseDomainSeq;
    }

    /** 로그인 방식 변경 — 변경 감지 전용 */
    public void changeLoginType(LoginType loginType) {
        this.loginType = loginType;
    }
}

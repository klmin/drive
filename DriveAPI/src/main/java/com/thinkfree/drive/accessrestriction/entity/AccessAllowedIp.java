package com.thinkfree.drive.accessrestriction.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_access_allowed_ip — 테넌트·역할별 접근 허용 IP(CIDR, UNIQUE (tenant_seq, role_type, ip_address, cidr_prefix)). 전체 교체 방식(테넌트+역할 삭제 → N건 INSERT).
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", AccessAllowedCountry §1-25 와 같은 꼴): 조회는 JPQL, 삭제는 JPQL — {@code AccessAllowedIpJpaRepository}. **N건 INSERT 만 JdbcClient 배치**({@code NamedParameterJdbcTemplate.batchUpdate}, 생성 키 불필요).
 * 등록 감사는 {@code create} 가 register_date 를 현재 시각으로 넣고 배치가 {@link #assignRegisterAudit} 로 바인딩 값을 write-back. 변경 메서드 없는 불변 엔티티. role_type 은 {@code @Enumerated(STRING)}(varchar(20) = 이름).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_access_allowed_ip")
public class AccessAllowedIp extends BaseRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ipSeq;
    private Integer tenantSeq;
    @Enumerated(EnumType.STRING)
    private RoleType roleType;
    private String ipAddress;
    private Integer cidrPrefix;

    private AccessAllowedIp(Integer tenantSeq, RoleType roleType, String ipAddress, Integer cidrPrefix, Integer registerUserSeq) {
        this.tenantSeq = tenantSeq;
        this.roleType = roleType;
        this.ipAddress = ipAddress;
        this.cidrPrefix = cidrPrefix;
        assignRegisterAudit(registerUserSeq, Instant.now()); // JPA persist 면 Auditing 이 덮어씀(배치 INSERT 는 리포가 JdbcAudit 로 다시 씀)
    }

    public static AccessAllowedIp create(Integer tenantSeq, RoleType roleType, String ipAddress, Integer cidrPrefix, Integer registerUserSeq) {
        return new AccessAllowedIp(tenantSeq, roleType, ipAddress, cidrPrefix, registerUserSeq);
    }
}

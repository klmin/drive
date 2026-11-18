package com.thinkfree.drive.accessrestriction.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 허용 IP 한 건(ip_address, cidr_prefix) — 읽기 전용 값. */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(AccessAllowedIpJpaRepository#findAllByTenantSeqAndRoleType)
public class AccessAllowedIpData {

    private String ipAddress;
    private Integer cidrPrefix;
}

package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.data.AccessAllowedIpData;
import com.thinkfree.drive.user.enums.RoleType;

import java.util.List;

/**
 * tb_access_allowed_ip 읽기 — {@code AccessAllowedIpService.findAllIpCidrs}(IpAccessControlService·관리자/슈퍼어드민 설정 조회) 가 주입. 쓰기는 {@link AccessAllowedIpRepository}.
 */
public interface AccessAllowedIpQueryRepository {

    /** 테넌트·역할의 허용 IP 전부 — ip_address, cidr_prefix 오름차순 */
    List<AccessAllowedIpData> findAllByTenantAndRole(Integer tenantSeq, RoleType roleType);
}

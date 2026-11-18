package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.entity.AccessAllowedIp;
import com.thinkfree.drive.user.enums.RoleType;

import java.util.List;

/**
 * tb_access_allowed_ip 쓰기 — {@code AccessAllowedIpService.replaceAllowedIps}(테넌트+역할 전체 교체: 삭제 → N건 INSERT) 가 주입. 읽기는 {@link AccessAllowedIpQueryRepository}.
 */
public interface AccessAllowedIpRepository {

    void deleteByTenantAndRole(Integer tenantSeq, RoleType roleType);

    /** JDBC 배치 INSERT(생성 키 없음). 등록 감사가 비어 있으면 컨텍스트 사용자·현재 시각으로 채우고 엔티티에 반영. 빈/널 목록은 SQL 없이 끝 */
    void insertAll(List<AccessAllowedIp> list);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}

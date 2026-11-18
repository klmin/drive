package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.entity.AccessAllowedIp;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.jooq.tables.records.TbAccessAllowedIpRecord;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_ACCESS_ALLOWED_IP;

/**
 * tb_access_allowed_ip 쓰기 — 구 AccessAllowedIpMapper.xml 의 deleteByTenantAndRole(JPQL)·insertAll(**JDBC 배치**) (§1 v2, AccessAllowedCountry §1-25 와 같은 결정). 읽기는 {@link DefaultAccessAllowedIpQueryRepository}.
 * <ul>
 *   <li>{@code insertAll} = {@link JooqBatches#insert} 레코드 배치 — 구 XML 의 다중 VALUES 1문 자리. IDENTITY 엔티티 N건 INSERT 는 Hibernate 가 배치하지 못한다. 생성 키는 돌려받지 않는다(호출부가 안 읽음, 구 XML 동일).
 *       등록 감사는 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭 — {@code create} 가 넣어 둔 사용자는 시스템 작업일 때만 남음) + {@code assignRegisterAudit} write-back</li>
 *   <li>{@code deleteByTenantAndRole} = JPQL {@code @Modifying}. 같은 트랜잭션에서 이어지는 배치 INSERT 와 섞일 관리 인스턴스는 없다(읽기 리포가 detach, 삭제는 벌크)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultAccessAllowedIpRepository implements AccessAllowedIpRepository {

    private final AccessAllowedIpJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public void deleteByTenantAndRole(Integer tenantSeq, RoleType roleType) {
        jpaRepository.deleteByTenantSeqAndRoleType(tenantSeq, roleType);
    }

    @Override
    public void insertAll(List<AccessAllowedIp> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        JooqBatches.insert(dsl, list, this::toRecord);
    }

    private TbAccessAllowedIpRecord toRecord(AccessAllowedIp e) {
        Integer registerUserSeq = JdbcAudit.userSeqOr(e.getRegisterUserSeq());
        Instant registerDate = JdbcAudit.nowOr(e.getRegisterDate());
        e.assignRegisterAudit(registerUserSeq, registerDate);
        TbAccessAllowedIpRecord r = dsl.newRecord(TB_ACCESS_ALLOWED_IP);
        r.setTenantSeq(e.getTenantSeq());
        r.setRoleType(e.getRoleType());
        r.setIpAddress(e.getIpAddress());
        r.setCidrPrefix(e.getCidrPrefix());
        r.setRegisterDate(registerDate);
        r.setRegisterUserSeq(registerUserSeq);
        return r;
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return jpaRepository.deleteByTenantSeq(tenantSeq);
    }
}

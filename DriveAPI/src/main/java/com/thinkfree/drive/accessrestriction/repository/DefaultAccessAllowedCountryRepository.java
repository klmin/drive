package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.entity.AccessAllowedCountry;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.jooq.tables.records.TbAccessAllowedCountryRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_ACCESS_ALLOWED_COUNTRY;

/**
 * tb_access_allowed_country 쓰기 — 구 AccessAllowedCountryMapper.xml 의 deleteByTenant(JPQL)·insertAll(**JDBC 배치**) (§1 v2). 읽기는 {@link DefaultAccessAllowedCountryQueryRepository}.
 * <ul>
 *   <li>{@code insertAll} = {@link JooqBatches#insert} 레코드 배치 — 구 XML 의 다중 VALUES 1문 자리. IDENTITY 엔티티 N건 INSERT 는 Hibernate 가 배치하지 못한다(Notification §1-17 과 같은 결정). 생성 키는 돌려받지 않는다(호출부가 안 읽음, 구 XML 동일).
 *       등록 감사는 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭 — {@code create} 가 넣어 둔 사용자는 시스템 작업일 때만 남음) + {@code assignRegisterAudit} write-back</li>
 *   <li>{@code deleteByTenant} = JPQL {@code @Modifying}. 같은 트랜잭션에서 이어지는 배치 INSERT 와 섞일 관리 인스턴스는 없다(읽기 리포가 detach, 삭제는 벌크)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultAccessAllowedCountryRepository implements AccessAllowedCountryRepository {

    private final AccessAllowedCountryJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public void deleteByTenant(Integer tenantSeq) {
        jpaRepository.deleteByTenantSeq(tenantSeq);
    }

    @Override
    public void insertAll(List<AccessAllowedCountry> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        JooqBatches.insert(dsl, list, this::toRecord);
    }

    private TbAccessAllowedCountryRecord toRecord(AccessAllowedCountry e) {
        Integer registerUserSeq = JdbcAudit.userSeqOr(e.getRegisterUserSeq());
        Instant registerDate = JdbcAudit.nowOr(e.getRegisterDate());
        e.assignRegisterAudit(registerUserSeq, registerDate);
        TbAccessAllowedCountryRecord r = dsl.newRecord(TB_ACCESS_ALLOWED_COUNTRY);
        r.setTenantSeq(e.getTenantSeq());
        r.setCountryIsoCode(e.getCountryIsoCode());
        r.setRegisterDate(registerDate);
        r.setRegisterUserSeq(registerUserSeq);
        return r;
    }
}

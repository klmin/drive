package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.data.LicenseData;
import com.thinkfree.drive.license.data.LicenseVersion;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.function;

/**
 * tb_license 읽기 — 구 LicenseMapper 어노테이션 @Select 4종 중 3종은 JPA({@link LicenseJpaRepository}), DB 시각 1종은 jOOQ(엔티티 무관 함수 호출, 10-30 작업 B P1-③ 에서 JdbcClient → jOOQ). 쓰기(upsert)는 {@link DefaultLicenseRepository}.
 * <p>
 * {@code findLicense} 는 구 {@code Map<String, Object>} 계약(컬럼명 키)을 JPQL 생성자 식 {@link LicenseData} 에서 만든다. 날짜 값은 {@link Instant} — {@code findUpdatedAt} 과 같은 타입이어야
 * {@code LicenseService} 의 캐시 동등 비교(check 3, {@code findUpdatedAt} 값과 equals)가 성립한다(MyBatis 시절 Map 값이 {@code java.sql.Timestamp} 라 항상 거짓이던 문제를 JPA 전환 때 고침).
 * 엔티티를 영속 컨텍스트에 올리지 않으므로 같은 트랜잭션의 upsert(JdbcClient) 뒤에도 DB 현재 값(detach 불필요). 행이 없으면 null(구 selectOne 동형).
 */
@Repository
@RequiredArgsConstructor
public class DefaultLicenseQueryRepository implements LicenseQueryRepository {

    private final LicenseJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public Map<String, Object> findLicense(int tenantSeq) {
        return jpaRepository.findDataByTenantSeq(tenantSeq).map(DefaultLicenseQueryRepository::toMap).orElse(null);
    }

    @Override
    public Instant findUpdatedAt(int tenantSeq) {
        return jpaRepository.findUpdatedAtByTenantSeq(tenantSeq).orElse(null);
    }

    @Override
    public LicenseVersion findLicenseVersion(int tenantSeq) {
        return jpaRepository.findVersionByTenantSeq(tenantSeq).orElse(null);
    }

    @Override
    public LicenseAuthStateData findAuthState(int tenantSeq) {
        return jpaRepository.findAuthStateByTenantSeq(tenantSeq).orElse(null);
    }

    @Override
    public List<Integer> findAllLicensedTenantSeqs() {
        return jpaRepository.findAllTenantSeqs();
    }

    @Override
    public List<Integer> findOnlineLicensedTenantSeqs() {
        return jpaRepository.findOnlineTenantSeqs();
    }

    @Override
    public Long getServerTimeMS() {
        // 트랜잭션 시작 시각(NOW)이 아닌 실행 순간의 DB 시각 — PG 전용 clock_timestamp() 라 jOOQ 에 해당 함수가 없어 이름으로 호출
        return epochMillisRounded(dsl.select(function("clock_timestamp", SQLDataType.INSTANT)).fetchSingle(0, Instant.class));
    }

    /**
     * epoch ms(반올림) — 구 SQL {@code (EXTRACT(EPOCH FROM ts) * 1000)::bigint} 와 같은 값. PG 의 numeric → bigint 는 반올림이고
     * {@link Instant#toEpochMilli()} 는 버림이라, 마이크로초 자리를 반올림해 맞춘다(시각은 항상 양수).
     */
    static long epochMillisRounded(Instant t) {
        return t.getEpochSecond() * 1000 + (t.getNano() + 500_000) / 1_000_000;
    }

    private static Map<String, Object> toMap(LicenseData e) {
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.DB.FIELD.TENANT_SEQ, e.getTenantSeq());
        row.put(Constants.DB.FIELD.DATA, e.getData());
        row.put(Constants.DB.FIELD.CREATED_AT, e.getCreatedAt());
        row.put(Constants.DB.FIELD.UPDATED_AT, e.getUpdatedAt());
        return row;
    }
}

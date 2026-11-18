package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.jooq.tables.records.TbLicenseRecord;
import com.thinkfree.drive.license.data.LicenseAuthResultData;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.thinkfree.drive.jooq.Tables.TB_LICENSE;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.val;

/**
 * tb_license 쓰기 — 구 LicenseMapper 어노테이션 @Insert upsert 를 jOOQ 로({@code ON CONFLICT} 는 JPQL 로 표현 불가 — 변경 감지 대상 아님). 읽기는 {@link DefaultLicenseQueryRepository}.
 * created_at/updated_at 은 구 매퍼와 같이 DB 현재 시각(NOW() = current_timestamp). 감사 컬럼 없음.
 * 온라인 인증 결과 갱신은 JPQL {@code @Modifying}({@link LicenseJpaRepository}).
 */
@Repository
@RequiredArgsConstructor
public class DefaultLicenseRepository implements LicenseRepository {

    private final DSLContext dsl;
    private final LicenseJpaRepository jpaRepository;

    @Override
    public int upsertLicense(int tenantSeq, byte[] data) {
        return upsert(tenantSeq, data, false);
    }

    @Override
    @Transactional
    public int replaceLicense(int tenantSeq, byte[] data, LicenseAuthResultData authResult, boolean keepAuth) {
        int result = upsert(tenantSeq, data, keepAuth);
        // 인증 결과는 파일을 저장한 뒤에 기록한다
        if (authResult != null) {
            updateAuthResult(authResult);
        }
        return result;
    }

    @Override
    public int updateAuthResult(LicenseAuthResultData result) {
        return jpaRepository.updateAuthResult(result.tenantSeq(), result.status(), result.failCount(),
                result.checkedAt(), result.successAt(), result.initAt(), result.signedData());
    }

    @Override
    public int updateAuthResultIfUnchanged(LicenseAuthResultData result, Instant expectedUpdatedAt) {
        return jpaRepository.updateAuthResultIfUnchanged(result.tenantSeq(), result.status(), result.failCount(),
                result.checkedAt(), result.successAt(), result.initAt(), result.signedData(), expectedUpdatedAt);
    }

    /**
     * keepAuth = 같은 라이선스를 다시 등록할 때 — 인증 이력(실패 횟수·최초 INIT 시각·서명된 원장)을 이어 간다.
     * 아니면 파일을 바꾸는 것이라 이전 라이선스의 온라인 인증 결과는 의미가 없으므로 함께 비운다.
     */
    private int upsert(int tenantSeq, byte[] data, boolean keepAuth) {
        InsertOnDuplicateSetMoreStep<TbLicenseRecord> upsert = dsl.insertInto(TB_LICENSE,
                        TB_LICENSE.TENANT_SEQ, TB_LICENSE.DATA, TB_LICENSE.CREATED_AT, TB_LICENSE.UPDATED_AT)
                .values(val(tenantSeq, TB_LICENSE.TENANT_SEQ), val(data, TB_LICENSE.DATA), currentInstant(), currentInstant())
                .onConflict(TB_LICENSE.TENANT_SEQ)
                .doUpdate()
                .set(TB_LICENSE.DATA, excluded(TB_LICENSE.DATA))
                .set(TB_LICENSE.UPDATED_AT, currentInstant());
        if (!keepAuth) {
            upsert = upsert
                    .set(TB_LICENSE.AUTH_STATUS, (String) null)
                    .set(TB_LICENSE.AUTH_FAIL_COUNT, 0)
                    .set(TB_LICENSE.AUTH_CHECKED_AT, (Instant) null)
                    .set(TB_LICENSE.AUTH_SUCCESS_AT, (Instant) null)
                    .set(TB_LICENSE.AUTH_INIT_AT, (Instant) null)
                    .set(TB_LICENSE.AUTH_SIGNED_DATA, (String) null);
        }
        return upsert.execute();
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return jpaRepository.deleteByTenantSeq(tenantSeq);
    }
}

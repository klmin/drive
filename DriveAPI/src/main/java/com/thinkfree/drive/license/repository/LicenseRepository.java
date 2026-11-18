package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.license.data.LicenseAuthResultData;

import java.time.Instant;

/**
 * 라이선스 쓰기(구 LicenseMapper 어노테이션 @Insert upsert) — {@code LicenseService} 가 주입. 읽기는 {@link LicenseQueryRepository}.
 * 구 매퍼의 {@code updateLicense}(@Update) 는 리포 인터페이스에 없던 죽은 statement → 옮기지 않음.
 */
public interface LicenseRepository {

    /**
     * INSERT … ON CONFLICT (tenant_seq) DO UPDATE SET data, updated_at = NOW(). PG 는 삽입·갱신 모두 1 을 돌려준다.
     * 갱신이면 이전 라이선스의 온라인 인증 결과(auth_*)를 비운다.
     */
    int upsertLicense(int tenantSeq, byte[] data);

    /**
     * 등록: 파일과 온라인 인증 결과를 한 트랜잭션으로 저장한다. 오프라인 라이선스면 authResult 가 null 이고 파일만 저장한다.
     * 인증 결과 기록이 실패하면 파일도 되돌린다. 파일만 남으면 인증 상태가 비어 오프라인 라이선스로 취급되고
     * 기동·일일 인증 대상에서 빠진다.
     * keepAuth 면(같은 라이선스를 다시 등록) 기존 인증 컬럼을 비우지 않고 그 위에 기록한다.
     */
    int replaceLicense(int tenantSeq, byte[] data, LicenseAuthResultData authResult, boolean keepAuth);

    /** 온라인 인증 결과(auth_*)만 기록한다. updated_at 은 바꾸지 않는다. */
    int updateAuthResult(LicenseAuthResultData result);

    /**
     * 저장된 파일로 인증한 결과를 기록한다. 인증 서버 응답을 기다리는 동안 다른 파일이 등록됐으면(updated_at 이 다르면)
     * 기록하지 않고 0 을 돌려준다. 앞 파일의 응답이 새 파일의 상태를 덮어쓰지 않게 하려는 것이다.
     */
    int updateAuthResultIfUnchanged(LicenseAuthResultData result, Instant expectedUpdatedAt);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}

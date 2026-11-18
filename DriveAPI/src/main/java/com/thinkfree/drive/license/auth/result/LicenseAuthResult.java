package com.thinkfree.drive.license.auth.result;

import com.thinkfree.drive.license.auth.enums.LicenseAuthStatus;
import com.thinkfree.drive.license.data.LicenseAuthResultData;

import java.time.Instant;

/**
 * 인증 한 번의 판정 결과.
 *
 * @param successAt  인증 서버가 준 마지막 성공 시각. 성공이 아니면 null (저장 시 기존 값 유지)
 * @param initAt     인증 서버가 준 최초 INIT 성공 시각. 성공이 아니면 null (저장 시 기존 값 유지)
 * @param signedData 서명이 확인된 원장 정보. 없으면 null (저장 시 기존 값 유지)
 * @param errorCode  인증 서버의 실패 코드. 연결 실패처럼 응답이 없으면 null
 */
public record LicenseAuthResult
(
    LicenseAuthStatus status,
    int failCount,
    Instant checkedAt,
    Instant successAt,
    Instant initAt,
    String signedData,
    String errorCode
)
{
    public LicenseAuthResultData toData(int tenantSeq) {
        return new LicenseAuthResultData(tenantSeq, status.name(), failCount, checkedAt, successAt, initAt, signedData);
    }
}

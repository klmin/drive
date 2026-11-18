package com.thinkfree.drive.license.result;

/**
 * 라이선스 표시용 요약.
 * 라이선스 도메인 + 시트 정보를 한 곳에 모은 read-only 집계 뷰.
 * 라이선스는 테넌트 단위 — 호출 측에서 tenant_seq 를 지정해 조회한다.
 *
 * @param state     라이선스 유효 여부. false 면 만료/시작 전/유예기간 만료
 * @param expiresOn 만료일 (yyyy-MM-dd). 잔여/초과 일수는 FE에서 오늘 기준으로 계산
 * @param seat      시트 제약이 있는 라이선스(SEAT)일 때만 채워짐. 아니면 null
 */
public record LicenseSummary(
        boolean state,
        String licenseType,
        String expiresOn,
        SeatSummary seat
) {
    public record SeatSummary(int current, int max) {}
}

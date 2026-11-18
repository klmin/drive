import dayjs from 'dayjs';

// 라이선스 플랜(SUB_CATEGORY) — 워터마크 노출 결정
export const LICENSE_PLAN = {
    TRIAL: 'TRIAL', // Thinkfree SDK · Trial
    DEVELOPER: 'DEVELOPER', // Thinkfree SDK · Developer Use Only
    FREE_COMMERCIAL: 'FREE_COMMERCIAL', // Powered by Thinkfree
    COMMERCIAL: 'COMMERCIAL', // 미노출
    ENTERPRISE: 'ENTERPRISE', // 미노출
    OEM_REDISTRIBUTION: 'OEM_REDISTRIBUTION', // 미노출
};

/** 사이드바 Powered by 노출 여부 (FREE_COMMERCIAL 만) */
export function isPoweredByPlan(plan) {
    return plan === LICENSE_PLAN.FREE_COMMERCIAL;
}

// 잔여 일수: 만료일 - 오늘 (양수면 남은 일수)
export function getDaysLeft(expiresOn) {
    return dayjs(expiresOn).diff(dayjs().startOf('day'), 'day');
}

// 초과 일수: 오늘 - 만료일 (양수면 만료 후 경과 일수)
export function getDaysOver(expiresOn) {
    return dayjs().startOf('day').diff(dayjs(expiresOn), 'day');
}

// 인증 실패로 보는 상태 코드: 인증 서버 연결 실패 31일째 / 시작 전 / 무효화
const AUTH_FAIL_CODES = [6895, 6987, 6985];

/**
 * config/license-status 응답으로 차단 상태 판정, 사용 가능하면 null
 *
 * NOT_ISSUED / SEAT_EXCEEDED / AUTH_FAILED / EXPIRED(만료 6995·6989 및 그 밖의 무효)
 * 예전 형식·서명 오류(6997)는 새로 등록해야 하므로 NOT_ISSUED
 * NO_RESPONSE(응답 없음) — 사용자 로그인은 미등록으로 차단, 관리자는 안내하지 않음
 */
export function getLicenseBlockStatus(statusRes) {
    if (!statusRes) return 'NO_RESPONSE';
    if (statusRes.code === 6998 || statusRes.code === 6997) return 'NOT_ISSUED';

    const data = statusRes.data;
    // 시트 수 초과가 만료보다 우선
    if (data?.availableSeatNum < 0 || data?.code === 6993) return 'SEAT_EXCEEDED';
    if (data?.state) return null;
    if (AUTH_FAIL_CODES.includes(data?.code)) return 'AUTH_FAILED';
    return 'EXPIRED';
}

/** 온라인 인증 실패 30일 유예 중인지 (admins/license 응답 기준, 사용은 허용) */
export function isAuthGracePeriod(license) {
    return license?.authStatus === 'GRACE_PERIOD';
}

// license 필드: expiresOn(만료일 yyyy-MM-dd), seat({current, max}), state(유효 여부)
//
// NOT_ISSUED    license 없음
// SEAT_EXCEEDED seat.current > seat.max
// EXPIRING_SOON state true, 잔여 30일 이하
// VALID         state true
// EXPIRED       state false
export function getLicenseStatus(license) {
    if (!license) return 'NOT_ISSUED';
    if (license.seat && license.seat.current > license.seat.max) return 'SEAT_EXCEEDED';
    if (license.state && getDaysLeft(license.expiresOn) <= 30) return 'EXPIRING_SOON';
    if (license.state) return 'VALID';
    return 'EXPIRED';
}

// 테넌트 총 용량·사용자 할당 용량 — GB/MB 로 입력하고 서버에는 byte 로 보낸다
import { convertSizeToBytes } from './math';

export const QUOTA_UNITS = ['GB', 'MB'];

const UNIT_BYTES = { GB: 1024 ** 3, MB: 1024 ** 2 };

// 0보다 큰 정수만 허용(GB·MB 공통) — 서버도 무제한(-1)·0 없음
const isValidQuotaInput = value => /^[1-9]\d*$/.test(String(value ?? ''));

// 테넌트 총 용량 검증
export const validateTenantTotalQuota = (value, t) => {
    if (!isValidQuotaInput(value)) {
        throw new Error(t('superAdmin.tenantDialog.totalQuota.validateFormat'));
    }
};

// 테넌트 총 용량 상·하한 검증 — capacity 는 GET super-admin/tenants/quota-capacity 응답(조회 실패면 null → 건너뜀, 저장 때 서버가 판정)
// 하한: 서버가 반환한 최소 용량(관리형 SINGLE은 실제 사용량, 나머지는 할당량 합). 상한: 기준값 + 스토리지 가용량(가용량 null = 상한 없음), 기준값은 등록 0·수정 현재 총 용량(없으면 할당량 합)
export const validateTenantQuotaCapacity = (quotaByte, capacity, t) => {
    if (!capacity) return;
    const {
        storageAvailByte,
        tenantTotalQuotaByte,
        tenantAllocatedQuotaByte,
        tenantMinimumQuotaByte,
    } = capacity;
    const minimum = tenantMinimumQuotaByte ?? tenantAllocatedQuotaByte;
    if (minimum != null && quotaByte < minimum) {
        throw new Error(t('superAdmin.tenantDialog.totalQuota.validateBelowAllocated'));
    }
    if (storageAvailByte != null) {
        const baseByte = tenantTotalQuotaByte ?? tenantAllocatedQuotaByte ?? 0;
        if (quotaByte > baseByte + storageAvailByte) {
            throw new Error(t('superAdmin.tenantDialog.totalQuota.validateInsufficient'));
        }
    }
};

// 사용자 할당 용량 검증 — 현재 사용량보다 작게는 불가(같은 값은 허용). basis 를 주면 저장할 byte(resolveQuotaBytes)로 비교
export const validateUserQuota = (value, unit, useQuotaBytes, t, { basis = null } = {}) => {
    if (!isValidQuotaInput(value)) {
        throw new Error(t('userInfoValidation.validateStorageLimitNumber'));
    }
    if (useQuotaBytes > resolveQuotaBytes(value, unit, basis)) {
        throw new Error(t('userInfoValidation.validateStorageLimitExceedUsage'));
    }
};

// 사용자 할당 가능 용량(byte) — capacity 는 GET admins/system-config/storage-quota 응답, 수정이면 대상 사용자의 현재 할당량을 더한다
// 조회 실패·테넌트 총 용량 키 없음이면 null(표시·판정 생략)
export const userAvailableQuotaByte = (capacity, currentQuotaByte = 0) => {
    if (capacity?.totalQuotaByte == null) return null;
    return capacity.totalQuotaByte - (capacity.allocatedQuotaByte ?? 0) + (currentQuotaByte ?? 0);
};

// 사용자 할당량 상한 검증 — 현재 할당량 이하(유지·감소)는 서버도 통과시키므로 늘릴 때만 판정
export const validateUserAvailableQuota = (quotaByte, availableByte, currentQuotaByte, t) => {
    if (availableByte == null || quotaByte <= (currentQuotaByte ?? 0)) return;
    if (quotaByte > availableByte) {
        throw new Error(t('admin.userDialog.storageQuota.validateExceedsTenant'));
    }
};

// 조회한 용량(byte) → 입력칸 값·단위. null(키 없음)이면 빈칸, 0·GB 로 나누어떨어지면 GB, 아니면 MB 정수(반올림)
export const bytesToQuotaInput = quotaByte => {
    if (quotaByte == null) return { value: '', unit: 'GB' };
    if (quotaByte % UNIT_BYTES.GB === 0) {
        return { value: String(quotaByte / UNIT_BYTES.GB), unit: 'GB' };
    }
    return { value: String(Math.round(quotaByte / UNIT_BYTES.MB)), unit: 'MB' };
};

// 화면 표시용 라벨 — 1GB 이상 GB 소수 셋째 자리(끝자리 0 생략), 미만 MB 정수
export const formatQuotaLabel = quotaByte => {
    if (quotaByte == null) return '';
    if (quotaByte === 0 || quotaByte >= UNIT_BYTES.GB) {
        return `${Number((quotaByte / UNIT_BYTES.GB).toFixed(3))} GB`;
    }
    return `${Math.round(quotaByte / UNIT_BYTES.MB)} MB`;
};

// byte → GB 숫자(소수 셋째 자리, 끝자리 0 생략) — 단위는 문구에 고정된 경우용, 하한 표시는 ceil(실제보다 작게 보이지 않게)
export const bytesToGbNumber = (quotaByte, { ceil = false } = {}) =>
    ceil
        ? Math.ceil((quotaByte / UNIT_BYTES.GB) * 1000) / 1000
        : Number((quotaByte / UNIT_BYTES.GB).toFixed(3));

// 기준 값·단위 그대로인지 — 기준(origin)은 { bytes, value, unit }
export const isQuotaInputUnchanged = (value, unit, origin) =>
    !!origin && value === origin.value && unit === origin.unit;

// 저장할 byte — 조회 값·단위 그대로면 조회 byte(MB 반올림 표시로 값이 바뀌지 않게)
export const resolveQuotaBytes = (value, unit, origin) =>
    isQuotaInputUnchanged(value, unit, origin) && origin.bytes != null
        ? origin.bytes
        : convertSizeToBytes(value, unit);

// 단위 변경 시 입력값 — 환산하지 않고 빈칸, 조회한 단위로 돌아오면 조회 값 복원. origin 이 없으면(추가 화면) 항상 빈칸
export const quotaValueForUnit = (nextUnit, origin) =>
    nextUnit === origin?.unit ? origin.value : '';

// 용량 관련 서버 오류 코드 → { inline: 입력칸 문구, toast: 토스트 문구 }, 해당 없으면 null(호출부 기본 문구)
export const quotaErrorMessage = (errorCode, t) => {
    const pair = (inline, toast = inline) => ({ inline: t(inline), toast: t(toast) });
    switch (errorCode) {
        case 'QUOTA_004': // 스토리지(Ceph) 가용 용량 부족·확인 실패
            return pair(
                'superAdmin.tenantDialog.totalQuota.validateInsufficient',
                'superAdmin.tenantDialog.totalQuota.validateInsufficientToast'
            );
        case 'QUOTA_006': // 테넌트 총 용량을 사용자 할당량 합계보다 작게 하향
            return pair('superAdmin.tenantDialog.totalQuota.validateBelowAllocated');
        case 'QUOTA_007': // 사용자 할당량 합계가 테넌트 총 용량 초과(사용자 등록·수정)
            return pair('admin.userDialog.storageQuota.validateExceedsTenant');
        case 'QUOTA_008': // 사용자 할당량 0 이하
            return pair('userInfoValidation.validateStorageLimitNumber');
        case 'USER_014': // 사용자 할당량을 현재 사용량보다 작게
            return pair('userInfoValidation.validateStorageLimitExceedUsage');
        default:
            return null;
    }
};

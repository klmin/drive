import api from '@/utils/api';

/** 한 번에 읽는 테넌트 수 — 다음 페이지가 있으면 nextCursor 로 이어 읽는다 */
const TENANT_PAGE_SIZE = 1000;

/**
 * 슈퍼관리자 테넌트 전체 목록 — 선택 목록·필터용.
 * hasNext·nextCursor 를 따라 마지막 페이지까지 모두 읽는다. 중간 페이지가 실패하면 예외를 던진다.
 */
export const fetchAllSuperAdminTenants = async () => {
    const tenants = [];
    let cursorId;
    for (;;) {
        const params = { ps: TENANT_PAGE_SIZE };
        if (cursorId != null) params.ci = cursorId;
        const res = await api.Get('super-admin/tenants', { params });
        if (res?.status !== 200) throw new Error(`Tenant list fetch failed: ${res?.status}`);
        tenants.push(...(res?.data?.data ?? []));
        const next = res?.data?.nextCursor;
        // 다음 커서가 없거나 그대로면 무한 반복을 막기 위해 멈춘다
        if (!res?.data?.hasNext || next == null || next === cursorId) break;
        cursorId = next;
    }
    return tenants;
};

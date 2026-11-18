import { http, HttpResponse } from 'msw';
import dataset from './data/resources_500.json';

/**
 * 실제 API 경로
 * host는 상관없음 -> MSW는 브라우저에서 "요청 경로"를 인터셉트
 */
const LIST_PATH = '*/api/v1/resources';

// 파라미터명
const PS = 'ps'; // page size
const CI = 'ci'; // cursor id (resourceSeq)
const CV = 'cv'; // cursor value (resourceName)
const CT = 'ct'; // cursor type (resourceType)

const SB = 'sb'; // sort by
const SO = 'so'; // sort order

const ALL = dataset.items || [];

/** 정렬 키 선택*/
function getSortValue(item, sb) {
    switch (sb) {
        case 'name':
            return item.resourceName ?? '';
        case 'update':
            return item.resourceUpdateDate ?? '';
        case 'register':
            return item.resourceRegisterDate ?? '';
        case 'open':
            return item.recentUpdateDate ?? item.recentRegisterDate ?? '';
        default:
            // 기본 name
            return item.resourceName ?? '';
    }
}

function compare(a, b, sb, so) {
    const av = getSortValue(a, sb);
    const bv = getSortValue(b, sb);

    // 문자열 비교 (name/date 대부분 문자열이라 localeCompare로 충분)
    const dir = so === 'desc' ? -1 : 1;

    // 1차 정렬: sb
    const c1 = String(av).localeCompare(String(bv), 'ko', { numeric: true }) * dir;
    if (c1 !== 0) return c1;

    // 2차 정렬: resourceType (FILE/FOLDER)
    const c2 = String(a.resourceType).localeCompare(String(b.resourceType)) * dir;
    if (c2 !== 0) return c2;

    // 3차 정렬: resourceSeq (안정성)
    return (Number(a.resourceSeq) - Number(b.resourceSeq)) * dir;
}

/**
 * 커서(ci,cv,ct) 기준으로 "다음 페이지 시작 index" 찾기
 * - (ci,cv,ct)가 없으면 0
 * - 있으면 정렬된 리스트에서 해당 item 위치를 찾아 그 다음 index부터
 */
function findStartIndex(sorted, ci, cv, ct) {
    if (!ci && !cv && !ct) return 0;

    const ciNum = Number(ci);
    const idx = sorted.findIndex(it => {
        const okId = ci ? Number(it.resourceSeq) === ciNum : true;
        const okVal = cv ? String(it.resourceName) === String(cv) : true;
        const okType = ct ? String(it.resourceType) === String(ct) : true;
        return okId && okVal && okType;
    });

    return idx >= 0 ? idx + 1 : 0;
}

export const handlers = [
    http.get(LIST_PATH, ({ request }) => {
        const url = new URL(request.url);

        const ps = Number(url.searchParams.get(PS) || '20');

        const sb = url.searchParams.get(SB) || 'name';
        const so = url.searchParams.get(SO) || 'asc';

        const ci = url.searchParams.get(CI) || '';
        const cv = url.searchParams.get(CV) || '';
        const ct = url.searchParams.get(CT) || '';

        // 1) 정렬 반영된 전체 리스트 만들기
        const sorted = [...ALL].sort((a, b) => compare(a, b, sb, so));

        // 2) 커서 기준 시작점 찾기
        const start = findStartIndex(sorted, ci, cv, ct);

        // 3) slice
        const slice = sorted.slice(start, start + ps);

        // 4) nextCursor
        const last = slice[slice.length - 1];
        const nextCursor = last ? last.resourceSeq : null;
        const nextCursorValue = last ? last.resourceName : null;
        const nextCursorType = last ? last.resourceType : null;

        const hasNext = start + slice.length < sorted.length;

        return HttpResponse.json(
            {
                result: true,
                code: 200,
                message: 'OK',
                data: slice,
                pageSize: ps,
                nextCursor,
                nextCursorValue,
                nextCursorType,
                hasNext,
            },
            { status: 200 }
        );
    }),
];

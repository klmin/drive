import { useCallback } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';

/**
 * 중요문서함 추가/해제 공통 처리.
 *
 * 컨텍스트 메뉴 / 행 액션 아이콘 두 경로가 서로 다르게 동작하던 것을 하나로 모았다.
 * - patchItem/removeItem이 주어지면 목록 전체를 재조회하지 않고 해당 행만 갱신한다.
 *   → 스크롤 위치와 선택 상태가 유지되고, 변경된 행 하나만 리렌더된다.
 * - 주어지지 않으면(홈처럼 목록 구성이 함께 바뀌어야 하는 화면) 기존처럼 전체 재조회한다.
 *
 * 행만 갱신하는 경우 응답 실패를 무시하면 화면이 실제 상태와 달라지므로,
 * 성공한 항목만 반영한다.
 *
 * @param patchItem  (resourceSeq, partial) => void
 * @param removeItem (resourceSeq) => void
 * @param reload     (isNewSearch) => Promise|void  목록 전체 재조회
 */
export default function useStarredToggle({ patchItem, removeItem, reload }) {
    const router = useRouter();
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();

    // 중요문서함 화면에서는 해제 시 해당 행이 목록에서 빠져야 한다.
    const isStarredPage = router.pathname.includes('/starred');
    const canPatch = typeof patchItem === 'function' && typeof removeItem === 'function';

    /**
     * @param items   단일 item 또는 item 배열
     * @param options forceRemove: 추가/해제를 명시 지정(상단 액션바처럼 클릭한 아이콘이 방향을 정하는 경우)
     *                ss: sharedByUserSeq 오버라이드(상단 액션바는 router.query.ss를 우선 사용)
     */
    const toggleStarred = useCallback(
        async (items, options = {}) => {
            const { forceRemove, ss } = options;
            const list = (Array.isArray(items) ? items : [items]).filter(Boolean);
            if (!list.length) return;

            // 지정이 없으면: 전부 등록된 상태이거나 중요문서함 화면이면 '해제', 그 외에는 '추가'
            const isRemove =
                typeof forceRemove === 'boolean'
                    ? forceRemove
                    : list.every(item => item?.isStarred) || isStarredPage;
            const applied = [];

            for (const item of list) {
                if (isRemove) {
                    const res = await api.Delete(`starred/${item?.starredSeq}`, {});
                    if (res?.status !== 200) continue;
                    applied.push({ resourceSeq: item?.resourceSeq });
                } else {
                    if (item?.isStarred) continue; // 이미 등록된 항목은 건너뜀
                    const res = await api.Post(`starred`, {
                        ss: ss || item?.sharedByUserSeq || item?.userSeq || undefined,
                        rs: item?.resourceSeq,
                    });
                    if (res?.code !== 201) continue;
                    // 등록 직후 곧바로 해제할 수 있어야 하므로 생성된 starredSeq를 함께 반영
                    applied.push({
                        resourceSeq: item?.resourceSeq,
                        starredSeq: res?.data?.starredSeq ?? null,
                    });
                }
            }

            if (!applied.length) return;

            showSnackbar(
                isRemove ? t('removeToStarred.success') : t('addToStarred.success'),
                'success'
            );

            if (!canPatch) {
                await reload?.(true);
                return;
            }

            for (const { resourceSeq, starredSeq } of applied) {
                if (isRemove && isStarredPage) removeItem(resourceSeq);
                else if (isRemove) patchItem(resourceSeq, { isStarred: false, starredSeq: null });
                else patchItem(resourceSeq, { isStarred: true, starredSeq });
            }
        },
        [isStarredPage, canPatch, patchItem, removeItem, reload, showSnackbar, t]
    );

    return { toggleStarred };
}

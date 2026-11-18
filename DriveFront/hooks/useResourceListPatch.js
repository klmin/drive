import { useCallback, useMemo } from 'react';
import { getResourceDetail } from '@/apis/resourceApi';

/**
 * 목록 전체 재조회 없이 특정 행만 갱신/제거하기 위한 헬퍼.
 *
 * 전체 재조회(getData(true))는 커서 페이징 기본값(ps=20)으로 다시 받아오므로
 * 무한스크롤로 20건 이상 로드한 상태에서는 목록이 잘리고 스크롤 위치도 튄다.
 * 배열의 길이·순서를 유지하면 스크롤 위치가 보존되고,
 * 변경된 항목만 새 객체가 되므로 memo된 row 하나만 리렌더된다.
 *
 * 목록 state는 두 가지 형태가 있다(DriveTable의 flatData 규약과 동일).
 * - 배열: drive/starred/search/shared/trash
 * - 그룹 객체 `{ 그룹명: item[] }`: recent (날짜별 그룹)
 *
 * @param setList        목록 state setter (예: setResourceList)
 * @param folderScoped   현재 폴더 기준 목록인지 여부. 이동(move)처럼 위치만 바뀌는 경우
 *                       폴더 기준 목록(drive/shared/trash)에서는 행이 사라지고,
 *                       평면 목록(recent/starred/search)에서는 행이 남아 갱신만 필요하다.
 */
export default function useResourceListPatch(setList, { folderScoped = false } = {}) {
    /** 해당 resourceSeq 행의 일부 필드만 갱신 */
    const patchItem = useCallback(
        (resourceSeq, partial) => {
            setList(prev => {
                if (Array.isArray(prev)) {
                    const index = prev.findIndex(r => r?.resourceSeq === resourceSeq);
                    if (index === -1) return prev;

                    const next = [...prev];
                    next[index] = { ...next[index], ...partial };
                    return next;
                }

                // 그룹 객체: 해당 항목이 들어있는 그룹의 배열만 새로 만든다.
                // 나머지 그룹은 기존 참조를 유지해 불필요한 리렌더를 막는다.
                let changed = false;
                const next = {};
                for (const [group, items] of Object.entries(prev ?? {})) {
                    if (!Array.isArray(items)) {
                        next[group] = items;
                        continue;
                    }
                    const index = items.findIndex(r => r?.resourceSeq === resourceSeq);
                    if (index === -1) {
                        next[group] = items;
                        continue;
                    }
                    const copy = [...items];
                    copy[index] = { ...copy[index], ...partial };
                    next[group] = copy;
                    changed = true;
                }
                return changed ? next : prev;
            });
        },
        [setList]
    );

    /**
     * 해당 행을 목록에서 제거 (예: 중요문서함에서 해제, 휴지통 이동, 영구삭제)
     * @param resourceSeqs resourceSeq 하나 또는 배열
     */
    const removeItem = useCallback(
        resourceSeqs => {
            const targets = new Set(
                (Array.isArray(resourceSeqs) ? resourceSeqs : [resourceSeqs]).filter(
                    seq => seq !== undefined && seq !== null
                )
            );
            if (!targets.size) return;

            setList(prev => {
                if (Array.isArray(prev)) {
                    const next = prev.filter(r => !targets.has(r?.resourceSeq));
                    return next.length === prev.length ? prev : next;
                }

                let changed = false;
                const next = {};
                for (const [group, items] of Object.entries(prev ?? {})) {
                    if (!Array.isArray(items)) {
                        next[group] = items;
                        continue;
                    }
                    const filtered = items.filter(r => !targets.has(r?.resourceSeq));
                    if (filtered.length !== items.length) changed = true;
                    next[group] = filtered.length === items.length ? items : filtered;
                }
                return changed ? next : prev;
            });
        },
        [setList]
    );

    /**
     * 변경된 행 하나를 서버에서 다시 읽어 교체 (예: 이름 변경, 공유, 버전 복원)
     * 조회에 실패하면(권한 상실·삭제 등) 목록을 건드리지 않는다.
     */
    const refreshItem = useCallback(
        async item => {
            const resourceSeq = item?.resourceSeq ?? item;
            if (!resourceSeq) return;

            const data = await getResourceDetail(resourceSeq, {
                ss: item?.sharedByUserSeq || item?.userSeq,
            });
            if (!data) return;

            patchItem(resourceSeq, data);
        },
        [patchItem]
    );

    /**
     * 이동 완료 반영. 폴더 기준 목록에서는 목록에서 빠지고,
     * 평면 목록에서는 위치 정보만 바뀌므로 행을 다시 읽어 교체한다.
     */
    const moveItems = useCallback(
        async items => {
            const list = (Array.isArray(items) ? items : [items]).filter(Boolean);
            if (!list.length) return;

            if (folderScoped) {
                removeItem(list.map(it => it?.resourceSeq ?? it));
                return;
            }
            for (const it of list) await refreshItem(it);
        },
        [folderScoped, removeItem, refreshItem]
    );

    const listPatch = useMemo(
        () => ({ patch: patchItem, remove: removeItem, refresh: refreshItem, move: moveItems }),
        [patchItem, removeItem, refreshItem, moveItems]
    );

    return { patchItem, removeItem, refreshItem, moveItems, listPatch };
}

import { useEffect } from 'react';

/**
 * 목록이 갱신될 때 checkItems를 새 목록의 최신 객체로 다시 맞춘다.
 *
 * checkItems는 선택 당시의 item '객체 스냅샷'을 들고 있어서, 목록을 리로드하거나
 * 특정 행을 patch하면 낡은 값(isStarred/starredSeq 등)이 남는다.
 * 그 결과 상단 액션바(areAllItemsStarred)와 행의 아이콘 상태가 어긋났다.
 *
 * - 목록에 남아있는 항목: 새 객체로 교체 → 파생값(areAllItemsStarred 등)이 자동으로 맞음
 * - 목록에서 사라진 항목: 선택에서 제거 (예: 중요문서함에서 해제되어 행이 빠진 경우)
 *
 * @param list         현재 목록. 배열 또는 그룹 객체 `{ 그룹명: item[] }`(recent)
 * @param setCheckItems 선택 state setter
 */
export default function useSyncedSelection(list, setCheckItems) {
    useEffect(() => {
        setCheckItems(prev => {
            if (!prev?.length) return prev;

            // DriveTable의 flatData와 동일한 규약으로 평탄화
            const flat = Array.isArray(list) ? list : Object.values(list ?? {}).flat();
            const byId = new Map(flat.map(r => [r?.resourceSeq, r]));

            const next = [];
            for (const item of prev) {
                const fresh = byId.get(item.resourceSeq);
                if (fresh) next.push(fresh);
            }

            // 참조까지 모두 동일하면 prev를 그대로 반환.
            // 새 배열을 반환하면 이 effect가 다시 돌아 무한 루프가 된다.
            const same = next.length === prev.length && next.every((n, i) => n === prev[i]);
            return same ? prev : next;
        });
    }, [list, setCheckItems]);
}

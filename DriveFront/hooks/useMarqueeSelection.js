import { useEffect, useRef, useState } from 'react';

/**
 * 빈 영역(좌우 여백 포함) 드래그로 행을 사각형 선택하는 훅.
 *
 * - 행 위에서 시작하는 드래그(HTML5 native DnD = 이동)와 충돌하지 않도록,
 *   mousedown 대상이 행/인터랙티브 요소면 무시하고 빈 영역에서만 시작한다.
 * - 리스트가 가상화(react-virtuoso)되어 화면 밖 행이 DOM에 없으므로,
 *   보이는 행에서 "행 높이(pitch) + 인덱스" 매핑을 구해 사각형 Y밴드가 걸치는
 *   전체 인덱스 범위를 기하학적으로 계산한다. → 화면 밖 행도 정확히 선택됨.
 * - "닿으면 선택" 기준(Y밴드가 조금이라도 겹치면 선택). X는 좌우 여백에서
 *   시작할 수 있어야 하므로 판정에서 제외한다.
 * - 가장자리 근처로 끌면 자동 스크롤한다.
 */

const START_THRESHOLD = 5; // 이 이상 움직여야 marquee 시작(단순 클릭 구분)
const EDGE = 44; // 이 거리 안으로 들어오면 자동 스크롤
const SCROLL_STEP = 20; // 자동 스크롤 프레임당 이동량(px)

// 이 요소 위에서 시작한 드래그는 marquee로 보지 않음(+ 행 클래스는 런타임에 합쳐짐)
const IGNORE_BASE =
    'thead, button, a, input, textarea, select, label,' +
    ' [role="menuitem"], [role="menu"], .MuiModal-root, .MuiMenu-paper, [data-marquee-ignore]';

export default function useMarqueeSelection({
    scrollerRef,
    enabled,
    getItems,
    setCheckItems,
    rowClassName = 'drive-row',
    // uniform=false(그룹/펼침 등 행 높이 불균일)일 때는 기하 계산 대신
    // 보이는 행 DOM을 실제로 히트테스트해서 누적 선택한다.
    uniform = true,
    resolveIndex, // (virtuosoIndex) => item | null  (히트테스트 모드에서 필요)
    getKey = it => it?.resourceSeq, // 누적/정렬용 고유키
}) {
    const [rect, setRect] = useState(null); // 오버레이(뷰포트 좌표)

    // effect는 mount 시 1회만 등록하고, 최신 값은 ref로 읽는다.
    const enabledRef = useRef(enabled);
    const getItemsRef = useRef(getItems);
    const setItemsRef = useRef(setCheckItems);
    const rowClassRef = useRef(rowClassName);
    const uniformRef = useRef(uniform);
    const resolveIndexRef = useRef(resolveIndex);
    const getKeyRef = useRef(getKey);
    enabledRef.current = enabled;
    getItemsRef.current = getItems;
    setItemsRef.current = setCheckItems;
    rowClassRef.current = rowClassName;
    uniformRef.current = uniform;
    resolveIndexRef.current = resolveIndex;
    getKeyRef.current = getKey;

    const stRef = useRef(null); // 진행 중인 드래그 상태
    const lastRangeRef = useRef(''); // 마지막으로 적용한 선택 행 범위(중복 setState 방지)

    useEffect(() => {
        const getScroller = () => scrollerRef.current;

        // 선택 대상이 비어있음을 반영(범위가 바뀔 때만 setState)
        const clearSelection = () => {
            if (lastRangeRef.current === 'none') return;
            lastRangeRef.current = 'none';
            setItemsRef.current([]);
        };

        const computeSelection = () => {
            const st = stRef.current;
            const scroller = getScroller();
            if (!st || !scroller) return;

            const rows = scroller.querySelectorAll('tr.' + rowClassRef.current);
            const items = getItemsRef.current() || [];
            if (!rows.length || !items.length) {
                clearSelection();
                return;
            }

            const srect = scroller.getBoundingClientRect();
            const scrollTop = scroller.scrollTop;

            // 보이는 행 하나(+가능하면 둘)로 pitch(행 간격)와 0번 행의 top을 역산
            const first = rows[0];
            const firstIdx = Number(
                first.getAttribute('data-index') ?? first.getAttribute('data-item-index')
            );
            const firstRect = first.getBoundingClientRect();
            let pitch = firstRect.height || 44;
            if (rows.length > 1) {
                const second = rows[1];
                const secondIdx = Number(
                    second.getAttribute('data-index') ?? second.getAttribute('data-item-index')
                );
                const secondRect = second.getBoundingClientRect();
                if (Number.isFinite(secondIdx) && secondIdx !== firstIdx) {
                    pitch = (secondRect.top - firstRect.top) / (secondIdx - firstIdx);
                }
            }
            if (!pitch || !Number.isFinite(pitch)) pitch = firstRect.height || 44;

            const firstContentTop = firstRect.top - srect.top + scrollTop;
            const row0Top = firstContentTop - (Number.isFinite(firstIdx) ? firstIdx : 0) * pitch;

            const curContentY = st.lastClientY - srect.top + scrollTop;
            const top = Math.min(st.startContentY, curContentY);
            const bottom = Math.max(st.startContentY, curContentY);

            const listTop = row0Top;
            const listBottom = row0Top + items.length * pitch;
            if (bottom < listTop || top > listBottom) {
                clearSelection();
                return;
            }

            let i0 = Math.floor((top - row0Top) / pitch);
            let i1 = Math.floor((bottom - row0Top) / pitch);
            i0 = Math.max(0, Math.min(items.length - 1, i0));
            i1 = Math.max(0, Math.min(items.length - 1, i1));

            // 선택 행 범위가 그대로면 배열 생성/ setState 를 건너뜀(버벅임 방지)
            const rangeSig = i0 + ':' + i1;
            if (rangeSig === lastRangeRef.current) return;
            lastRangeRef.current = rangeSig;

            const selected = [];
            for (let i = i0; i <= i1; i++) {
                if (items[i]) selected.push(items[i]);
            }
            setItemsRef.current(selected);
        };

        // 그룹/펼침 등 행 높이가 불균일할 때: 보이는 행만 실제 rect로 히트테스트하고,
        // 드래그 중 누적(화면 밖으로 스크롤된 선택은 유지). 데이터 행(rowClassName)만
        // 순회하므로 날짜 그룹 헤더는 선택되지 않는다.
        const computeHitTest = () => {
            const st = stRef.current;
            const scroller = getScroller();
            if (!st || !scroller) return;

            const getKey = getKeyRef.current;
            const resolveIndex = resolveIndexRef.current;
            const set = st.keySet;

            const srect = scroller.getBoundingClientRect();
            const originY = st.startContentY - scroller.scrollTop + srect.top;
            const rectTop = Math.min(originY, st.lastClientY);
            const rectBot = Math.max(originY, st.lastClientY);

            const rows = scroller.querySelectorAll('tr.' + rowClassRef.current);
            rows.forEach(row => {
                const vIdx = Number(
                    row.getAttribute('data-index') ?? row.getAttribute('data-item-index')
                );
                const item = resolveIndex ? resolveIndex(vIdx) : null;
                if (!item) return;
                const key = getKey(item);
                const r = row.getBoundingClientRect();
                const hit = r.top <= rectBot && r.bottom >= rectTop; // Y밴드 교차(닿으면 선택)
                if (hit) set.add(key);
                else set.delete(key); // 보이는 행만 해제 가능(화면 밖은 유지)
            });

            const items = getItemsRef.current() || [];
            const selected = items.filter(it => set.has(getKey(it))); // 목록 순서 유지

            const sig =
                'h:' +
                selected.length +
                ':' +
                (selected.length
                    ? getKey(selected[0]) + '-' + getKey(selected[selected.length - 1])
                    : '');
            if (sig === lastRangeRef.current) return;
            lastRangeRef.current = sig;
            setItemsRef.current(selected);
        };

        const updateOverlay = () => {
            const st = stRef.current;
            const scroller = getScroller();
            if (!st || !scroller) return;
            const srect = scroller.getBoundingClientRect();
            // 시작점은 content에 고정 → 스크롤하면 뷰포트상 위치가 따라 이동(탐색기 동작)
            const originY = st.startContentY - scroller.scrollTop + srect.top;
            let top = Math.min(originY, st.lastClientY);
            let bottom = Math.max(originY, st.lastClientY);
            top = Math.max(top, srect.top);
            bottom = Math.min(bottom, srect.bottom);
            const left = Math.min(st.startClientX, st.lastClientX);
            const right = Math.max(st.startClientX, st.lastClientX);
            setRect({ left, top, width: right - left, height: Math.max(0, bottom - top) });
        };

        // 단일 rAF 루프: mousemove 여러 번 → 프레임당 1회로 코얼레싱.
        // 자동 스크롤 중(autoDir≠0)에는 스스로 다음 프레임을 예약해 연속 스크롤.
        const runFrame = () => {
            const st = stRef.current;
            if (!st) return;
            st.rafId = 0;
            if (st.autoDir !== 0) {
                const sc = getScroller();
                if (sc) sc.scrollTop += st.autoDir * SCROLL_STEP;
            }
            if (uniformRef.current) computeSelection();
            else computeHitTest();
            updateOverlay();
            if (st.autoDir !== 0) ensureRaf();
        };

        const ensureRaf = () => {
            const st = stRef.current;
            if (!st || st.rafId) return;
            st.rafId = requestAnimationFrame(runFrame);
        };

        const onMouseMove = e => {
            const st = stRef.current;
            if (!st) return;
            st.lastClientX = e.clientX;
            st.lastClientY = e.clientY;
            if (!st.moved) {
                if (
                    Math.hypot(e.clientX - st.startClientX, e.clientY - st.startClientY) <
                    START_THRESHOLD
                )
                    return;
                st.moved = true;
            }
            e.preventDefault();
            window.getSelection?.()?.removeAllRanges();

            const scroller = getScroller();
            if (scroller) {
                const srect = scroller.getBoundingClientRect();
                st.autoDir =
                    st.lastClientY > srect.bottom - EDGE
                        ? 1
                        : st.lastClientY < srect.top + EDGE
                          ? -1
                          : 0;
            }
            ensureRaf();
        };

        const onMouseUp = () => {
            const st = stRef.current;
            if (st?.rafId) cancelAnimationFrame(st.rafId);
            stRef.current = null;
            lastRangeRef.current = '';
            setRect(null);
            document.removeEventListener('mousemove', onMouseMove, true);
            document.removeEventListener('mouseup', onMouseUp, true);
        };

        const onMouseDown = e => {
            if (!enabledRef.current) return;
            if (e.button !== 0) return;
            const scroller = getScroller();
            if (!scroller) return;
            const target = e.target;
            if (target?.closest?.('.' + rowClassRef.current + ', ' + IGNORE_BASE)) return;

            const srect = scroller.getBoundingClientRect();
            // 테이블의 수직 밴드 안에서 시작한 경우만(위쪽 필터/경로 옆 여백은 제외)
            if (e.clientY < srect.top || e.clientY > srect.bottom) return;

            stRef.current = {
                startClientX: e.clientX,
                startClientY: e.clientY,
                startContentY: e.clientY - srect.top + scroller.scrollTop,
                lastClientX: e.clientX,
                lastClientY: e.clientY,
                moved: false,
                rafId: 0,
                autoDir: 0,
                keySet: new Set(), // 히트테스트 모드 누적 선택
            };
            lastRangeRef.current = '';
            document.addEventListener('mousemove', onMouseMove, true);
            document.addEventListener('mouseup', onMouseUp, true);
        };

        document.addEventListener('mousedown', onMouseDown, true);
        return () => {
            document.removeEventListener('mousedown', onMouseDown, true);
            document.removeEventListener('mousemove', onMouseMove, true);
            document.removeEventListener('mouseup', onMouseUp, true);
            const st = stRef.current;
            if (st?.rafId) cancelAnimationFrame(st.rafId);
        };
    }, [scrollerRef]);

    return { marqueeRect: rect };
}

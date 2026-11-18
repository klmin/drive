import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import {
    Box,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledContextMenu from '@/components/common/AdminContextMenu';
import { useRouter } from '@/hooks/useRouter';
import { useUserDelete } from '@/hooks/useUserDelete';
import { useLicense } from '@/provider/LicenseProvider';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import React from 'react';
import { PerfProfiler } from '@/tests/perf/PerfProfiler';
import { TableVirtuoso } from 'react-virtuoso';
import { UserRowCells } from './UserRowCells';
import UserLogTable from './UserLogTable';
import useDevice from '@/hooks/useDevice';
import useMarqueeSelection from '@/hooks/useMarqueeSelection';

const Root = styled('div')(({ theme }) => ({
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',
    borderTop: `1px solid ${palette.divider_8}`,
    overflowX: 'hidden',
    table: {
        overflowX: 'hidden',
        boxShadow: 'none',
        width: '100%',
        height: '100%',
    },

    '.table-section-label': {
        padding: '19px 8px 9px',
        ...typography.body6,
        color: palette.text_60,
        [theme.breakpoints.down('md')]: {
            height: 'auto',
            padding: '19px 8px 9px',
            borderBottom: 'none',
        },
    },

    tHead: {
        height: 30,
        borderRight: 'none',
        backgroundColor: 'white',
        borderBottom: `none !important`,
        zIndex: 99,
    },

    th: {
        padding: '0px 12px 0px 8px',
        backgroundColor: 'white',
        borderBottom: `none !important`,
        borderTop: `none !important`,
        [theme.breakpoints.down('md')]: {
            padding: '0px 6px',
        },
    },

    '& thead tr': {
        position: 'sticky',
        top: 0,
        zIndex: 99,
        backgroundColor: 'white',
        boxShadow: `0 3px 5px -2px ${palette.divider_12}`,
    },

    td: {
        height: 44,
        padding: '0px 12px 0px 8px',
        ...typography.body5,
        color: palette.text_80,
        cursor: 'pointer',
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        borderBottom: `1px solid ${palette.table_divider_nomal}`,
        [theme.breakpoints.down('md')]: {
            height: 72,
            padding: '8px 6px',
            borderBottom: 'none',
        },
    },

    '&:not(.is-touch-device) .user-row:hover .toggle-checkbox': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },
    '&:not(.is-touch-device) .user-row:hover .toggle-icon': {
        opacity: 0,
        visibility: 'hidden',
    },
    '&:not(.is-touch-device) .user-row:not(.is-checked):hover': {
        background: palette.table_bg_hover,
    },

    // hoveredRow state 제거 -> CSS hover로 대체
    // hover 여부에 따라 checkbox 표시
    /* 기본(= not checked & not hover): icon 보이고 checkbox 숨김 */
    '.user-row .toggle-wrap': {
        position: 'relative',
        width: '36px',
        height: '36px',
    },

    '.user-row .toggle-layer': {
        position: 'absolute',
        inset: 0 /* top/right/bottom/left: 0 */,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },

    '.user-row .toggle-checkbox': {
        opacity: 0,
        visibility: 'hidden',
        pointerEvents: 'none',
    },

    '.user-row .toggle-icon': {
        opacity: 1,
        visibility: 'visible',
    },

    /* hover 또는 checked: checkbox 보이고 icon 숨김 */
    '.user-row.is-checked .toggle-checkbox': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },

    '.user-row.is-checked .toggle-icon': {
        opacity: 0,
        visibility: 'hidden',
    },

    '.user-row .toggle-checkbox, .user-row .toggle-icon': {
        transition: 'opacity 0.12s ease',
    },
}));

const VirtuosoTableComponents = {
    Scroller: React.forwardRef((props, ref) => (
        <TableContainer
            {...props}
            ref={ref}
            data-testid="drive-table-container"
            sx={{
                overflowX: 'hidden',
                height: '100%',
            }}
        />
    )),
    Table: props => (
        <Table
            {...props}
            stickyHeader
            sx={{
                width: '100%',

                // table이 컨테이너 높이를 다 채우지 못하게
                height: 'auto !important',
                minHeight: 'unset !important',
            }}
        />
    ),
    TableHead: React.forwardRef((props, ref) => <TableHead {...props} ref={ref} />),
    TableBody: React.forwardRef((props, ref) => (
        <TableBody
            {...props}
            ref={ref}
            sx={{
                display: 'table-row-group',
                height: 'auto !important',
            }}
        />
    )),

    TableRow: rowProps => {
        const index = rowProps['data-index']; // Virtuoso가 넣어주는 현재 row index
        const ctx = rowProps.context; // TableVirtuoso context 접근
        const v = ctx?.virtuosoData?.[index]; // 현재 row의 데이터

        // group row는 이벤트 최소화
        // group 구분 row나 detail인 경우 TableRow Custom에서 제외
        if (!v || v.type === 'group' || v.type === 'detail') {
            return <TableRow {...rowProps} className="table-section-label" />;
        }

        const item = v.item;
        const checked = ctx.checkedSetRef.current.has(item.userSeq);

        return (
            <TableRow
                {...rowProps} // data-index/aria/virt style 유지
                className={`user-row ${checked ? 'is-checked' : ''}`}
                onMouseDown={ctx.onRowMouseDown}
                onClick={e => ctx.onRowClickCombined(e, item)}
                onDoubleClick={e => {
                    ctx.onRowClickCombined(e, item);
                }}
                onContextMenu={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    ctx.onContextMenuOpen(e, item);
                }}
                draggable
                onDragStart={e => ctx.onDragStart(e, item)}
                onDragOver={e => ctx.onDragOver(e, item)}
                onDrop={e => ctx.onDrop(e, item)}
                onDragLeave={ctx.onDragLeave}
                onDragEnd={ctx.onDragEnd}
                sx={{
                    cursor: 'pointer',
                    borderBottom: `1px solid ${palette.table_divider_nomal}`,
                    background: checked ? palette.table_bg_selected : undefined,
                }}
            />
        );
    },
};
VirtuosoTableComponents.Scroller.displayName = 'UserTableScroller';
VirtuosoTableComponents.TableHead.displayName = 'UserTableTableHead';
VirtuosoTableComponents.TableBody.displayName = 'UserTableTableBody';

export default function UserTable({
    data,
    getData,
    checkItems,
    setCheckItems,
    hasNext,
    cursorSearchCount,
    setCursorSearchCount,
    showRole,
    showLastLogin,
    showStatus,
    showRequestedDate,
    showScheduledDate,
    showDeletedBy,
    listPatch,
}) {
    const t = useTranslations();
    const user = useUser();
    const router = useRouter();

    // (가상화) Virtuoso의 스크롤/인덱스 제어용 ref
    const virtuosoRef = useRef(null);
    // 스크롤 컨테이너 DOM 직접 참조(marquee 선택용)
    const scrollerElRef = useRef(null);

    const [menuTargetRow, setMenuTargetRow] = useState('');

    // recent/일반 공통 플랫 데이터
    const flatData = useMemo(() => {
        return Array.isArray(data) ? data : Object.values(data).flat();
    }, [data]);

    const allCheck = useMemo(() => {
        if (!flatData?.length) return false;
        return checkItems.length === flatData.length;
    }, [checkItems.length, flatData.length]);

    const hasMultipleSelection = checkItems.length >= 2;

    // 단축키용
    const [anchorIndex, setAnchorIndex] = useState(null); // 기준점 shift 선택 시 사용
    const [activeIndex, setActiveIndex] = useState(null);
    const rootRef = useRef(null);

    const rowAnchorRefs = useRef({});

    const { isPc } = useDevice();
    // 최신 activeIndex/flatData를 raf 루프에서 읽을 수 있게 ref로 미러링
    const activeIndexRef = useRef(0);
    const flatDataRef = useRef([]);

    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });
    const isPendingDeletion = router.asPath.includes('pending-deletion');

    // UserRow 확장 상태
    const [expandedSet, setExpandedSet] = useState(() => new Set());

    const toggleExpand = useCallback(userSeq => {
        setExpandedSet(prev => {
            const next = new Set(prev);
            if (next.has(userSeq)) next.delete(userSeq);
            else next.add(userSeq);
            return next;
        });
    }, []);

    const handleClose = () => {
        setMenuState({ ...menuState, visible: false });
    };

    const handleContextMenu = (e, row) => {
        // console.log('Row right-clicked:', row);
        e.preventDefault();
        setMenuState({
            visible: true,
            top: e.clientY,
            left: e.clientX,
            selectedRow: row,
        });
    };

    const columns = [
        {
            field: 'name',
            headerName: 'common.label.userName',
            isMobile: true,
        },
        { field: 'userId', headerName: 'common.label.userId', align: 'left' },
        { field: 'role', headerName: 'common.label.role', align: 'left' },
        { field: 'lastLogin', headerName: 'common.label.lastLogin', align: 'center' },
        { field: 'status', headerName: 'common.label.status', align: 'center', isMobile: true },
        {
            field: 'requestedDate',
            headerName: 'AdminUserManage.requestedDate',
            align: 'left',
            isMobile: true,
        },
        {
            field: 'scheduledDate',
            headerName: 'AdminUserManage.scheduledDate',
            align: 'left',
            isMobile: true,
        },
        {
            field: 'deletedBy',
            headerName: 'AdminUserManage.deletedBy',
            align: 'left',
            isDeletedBy: true,
        },
        { field: 'options', headerName: '', align: 'center', isMobile: true },
    ];

    // handleToggleCheck는 함수 참조가 고정되어서 memo Row에 유리하도록 useCallback 사용?
    const handleToggleCheck = useCallback(
        user => {
            setCheckItems(prev => {
                const exists = (prev ?? []).some(i => i.userSeq === user.userSeq);
                return exists ? prev.filter(i => i.userSeq !== user.userSeq) : [...prev, user];
            });
        },
        [setCheckItems]
    );

    const { refreshLicense } = useLicense();

    const { handleDeleteAdmin, handleDeleteUser, handleCompletelyDeleteUser } = useUserDelete({
        t,
        setCheckItems,
        getData,
        refreshLicense,
        listPatch,
    });

    // checkItems 빠르게 조회하기 위한 Set
    const checkedSet = useMemo(
        () => new Set((checkItems ?? []).map(it => it.userSeq)),
        [checkItems]
    );
    const checkedSetRef = useRef(checkedSet);

    // userSeq → index 매핑
    const idIndexMap = useMemo(() => {
        const m = new Map();
        flatData.forEach((it, i) => m.set(it.userSeq, i));
        return m;
    }, [flatData]);

    const updateSelection = useCallback(
        nextItems => {
            setCheckItems(nextItems);
        },
        [setCheckItems]
    );

    const selectSingleByIndex = useCallback(
        idx => {
            const it = flatData[idx];
            if (it) {
                activeIndexRef.current = idx;
                updateSelection([it]);
                setActiveIndex(idx);
            }
        },
        [flatData, updateSelection]
    );

    const toggleOneByIndex = useCallback(
        idx => {
            const it = flatData[idx];
            if (!it) return;
            const exists = checkedSet.has(it.userSeq);
            updateSelection(
                exists ? checkItems.filter(x => x.userSeq !== it.userSeq) : [...checkItems, it]
            );
            setActiveIndex(idx);
        },
        [flatData, checkItems, updateSelection, checkedSet]
    );

    const selectRangeToIndex = useCallback(
        idx => {
            const anchor = anchorIndex ?? idx; // 기준점 없으면 현재 클릭으로
            const start = Math.min(anchor ?? idx, idx);
            const end = Math.max(anchor ?? idx, idx);

            // 새로 추가할 범위
            const rangeItems = flatData.slice(start, end + 1);

            // 기존 선택 + 새로 추가할 범위 (중복 없이)
            const safeSelected = Array.isArray(checkItems) ? checkItems : [];
            const existingIds = new Set(safeSelected.map(it => it.userSeq));
            for (const it of rangeItems) existingIds.add(it.userSeq);

            // Set → 배열로, flatData 순서 유지
            const merged = flatData.filter(it => existingIds.has(it.userSeq));

            updateSelection(merged);
            setActiveIndex(idx);
            setAnchorIndex(idx); // Shift 선택 후 기준점을 "마지막 클릭"으로 변경
        },
        [flatData, anchorIndex, checkItems, updateSelection, setActiveIndex, setAnchorIndex]
    );

    // 행 클릭 공통 핸들러 (Shift/Ctrl/Cmd 지원)
    const handleRowClickCombined = useCallback(
        (e, item) => {
            const idx = idIndexMap.get(item.userSeq) ?? 0;

            if (e.metaKey || e.ctrlKey) {
                // Cmd/Ctrl + 클릭: 개별 토글
                toggleOneByIndex(idx);
                setActiveIndex(idx);
                setAnchorIndex(idx);
                return;
            }
            if (e.shiftKey) {
                // Shift + 클릭: 구간 선택
                selectRangeToIndex(idx);
                return;
            }
            // 일반 클릭: 단일 선택
            selectSingleByIndex(idx);
            setActiveIndex(idx);
            setAnchorIndex(idx);
        },
        [idIndexMap, toggleOneByIndex, selectRangeToIndex, selectSingleByIndex]
    );

    // 입력 요소라면 단축키 무시
    const isTextEditingTarget = el => {
        const tag = (el?.tagName || '').toLowerCase();
        if (tag === 'textarea') return true;
        if (el?.isContentEditable) return true;
        if (tag !== 'input') return false;

        const type = (el.type || '').toLowerCase();
        // 텍스트 입력형만 true, checkbox, radio 등은 false
        return [
            'text',
            'search',
            'email',
            'url',
            'tel',
            'password',
            'number',
            'date',
            'datetime-local',
            'month',
            'time',
            'week',
        ].includes(type);
    };

    // Shift 범위 선택 시 텍스트 선택 방지
    const handleRowMouseDown = useCallback(e => {
        if (e.shiftKey && !isTextEditingTarget(e.target)) {
            e.preventDefault(); // 텍스트 선택 시작 자체 방지
            window.getSelection()?.removeAllRanges(); // Safari 등에서 이미 잡힌 텍스트 선택 해제
        }
    }, []);

    // 빈 공간 클릭 시 Root에 포커스 (= 목록 영역 포커스)
    const handleRootMouseDown = useCallback(
        e => {
            if (!isTextEditingTarget(e.target)) {
                rootRef.current?.focus?.();
            }
            // 테이블 안 빈 영역(행/헤더/컨트롤이 아닌 곳, 예: 짧은 목록의 하단 여백)
            // 클릭 시 선택 해제
            if (
                !e.target?.closest?.(
                    '.user-row, .table-section-label, thead, button, a, input, textarea, select, label, [role="menu"], [role="menuitem"], [data-marquee-ignore]'
                )
            ) {
                setCheckItems(prev => (prev.length ? [] : prev));
            }
        },
        [setCheckItems]
    );

    // 길게 누를 때 우리 쪽에서 간격을 제어(프레임마다 1스텝)
    const holdRef = useRef({
        active: false,
        dir: 0, // 1 = Down, -1 = Up
        rafId: 0,
        lastStepAt: 0,
        interval: 90, // 한 칸 이동 간격(ms): 70~100ms 사이로 취향 조정
    });

    // (가상화) "아이템 index" → "Virtuoso(렌더링) index" 매핑이 필요
    // - recent는 group row가 섞이므로, scrollToIndex에 그대로 item index를 넣으면 틀어짐
    const virtuosoData = useMemo(() => {
        const list = [];

        const pushRowWithDetail = item => {
            list.push({ type: 'row', item });
            if (expandedSet.has(item.userSeq)) {
                list.push({ type: 'detail', item }); // 확장용 추가 row (사용자 로그)
            }
        };

        if (Array.isArray(data)) {
            data.forEach(pushRowWithDetail);
            return list;
        }

        Object.entries(data).forEach(([groupTitle, items]) => {
            list.push({ type: 'group', groupTitle });
            (items ?? []).forEach(pushRowWithDetail);
        });

        return list;
    }, [data, expandedSet]);

    // 빈 영역(좌우 여백 포함) 드래그로 행을 사각형 선택.
    // - 펼친 상세 행이 없으면 균일 행 높이 기반 기하 계산(화면 밖 행도 즉시 선택)
    // - 펼친 상세 행(UserLogTable)이 있으면 히트테스트 모드. detail 행은
    //   데이터 행(.user-row)이 아니므로 선택에서 자연히 제외된다.
    const { marqueeRect } = useMarqueeSelection({
        scrollerRef: scrollerElRef,
        enabled: Array.isArray(data) && isPc,
        uniform: expandedSet.size === 0,
        getItems: () => flatDataRef.current,
        setCheckItems,
        rowClassName: 'user-row',
        resolveIndex: vIdx => {
            const v = virtuosoData[vIdx];
            return v && v.type === 'row' ? v.item : null;
        },
        getKey: it => it.userSeq,
    });

    const virtuosoIndexBySeq = useMemo(() => {
        // (가상화) userSeq → virtuoso index
        const m = new Map();
        virtuosoData.forEach((v, idx) => {
            if (v?.type === 'row' && v?.item?.userSeq) {
                m.set(v.item.userSeq, idx);
            }
        });
        return m;
    }, [virtuosoData]);

    // 선택 요소 즉시 갱싱
    const setSelectionByIndex = useCallback(
        idx => {
            const list = flatDataRef.current;
            const it = list[idx];
            if (!it) return;

            // 1) ref를 먼저 최신값으로 (반복 루프가 곧바로 다음 스텝 계산 가능)
            activeIndexRef.current = idx;

            // 2) 상태 동기 갱신
            setActiveIndex(idx);
            setCheckItems([it]); // selected 표시의 실제 근거 (TableRow selected, sx 배경 등)
        },
        [setCheckItems]
    );

    const scrollToItemIndex = useCallback(
        idx => {
            // (가상화) 선택된 "아이템 index"를 virtuoso index로 변환해 스크롤
            const list = flatDataRef.current;
            const it = list[idx];
            if (!it) return;

            const vIdx = virtuosoIndexBySeq.get(it.userSeq);
            if (vIdx == null) return;

            virtuosoRef.current?.scrollToIndex({
                index: vIdx,
                align: 'center',
                behavior: 'auto',
            });
        },
        [virtuosoIndexBySeq]
    );

    function stepMove(dir) {
        const list = flatDataRef.current;
        const last = activeIndexRef.current;

        const next = Math.max(0, Math.min(list.length - 1, last + dir));
        if (next === last) return;

        // 선택을 즉시 갱신
        setSelectionByIndex(next);

        // (가상화) DOM 기반 보정 대신 scrollToIndex 사용
        requestAnimationFrame(() => {
            scrollToItemIndex(next);
        });
    }

    function startHold(dir) {
        const h = holdRef.current;
        if (h.active && h.dir === dir) return;

        stopHold();
        h.active = true;
        h.dir = dir;
        h.lastStepAt = performance.now();

        // 즉시 1칸
        stepMove(dir);

        // raf 루프
        const loop = t => {
            if (!h.active) return;
            if (t - h.lastStepAt >= h.interval) {
                h.lastStepAt = t;
                stepMove(dir);
            }
            h.rafId = requestAnimationFrame(loop);
        };
        h.rafId = requestAnimationFrame(loop);
    }

    function stopHold() {
        const h = holdRef.current;
        h.active = false;
        if (h.rafId) cancelAnimationFrame(h.rafId);
        h.rafId = 0;
    }

    // 키보드 단축키
    const handleKeyDown = useCallback(
        async e => {
            if (isTextEditingTarget(e.target)) return;

            const withCmd = e.metaKey;
            const withCtrl = e.ctrlKey;
            const withCtrlOrCmd = withCmd || withCtrl;
            const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;

            if (withCtrlOrCmd && key === 'a') {
                // 전체 선택
                e.preventDefault();
                updateSelection(flatData);
                return;
            }
            if (withCtrlOrCmd && key === 'c') {
                // 복사 - 관리자 화면에서 동작x
                e.preventDefault();
                // 추후 필요 시 구현
                return;
            }
            if (withCtrlOrCmd && key === 'v') {
                // 붙여넣기 - 관리자 화면에서 동작x
                e.preventDefault();
                // 추후 필요 시 구현
                return;
            }

            switch (key) {
                case 'Escape':
                    updateSelection([]);
                    setActiveIndex(null);
                    setAnchorIndex(null);
                    break;
                case 'Delete':
                    // 보호 계정이 섞여 있으면 메뉴와 같이 삭제하지 않는다(서버도 USER_034 로 거부)
                    if (checkItems.length > 0 && !checkItems.some(it => it?.isProtected === true)) {
                        isPendingDeletion
                            ? await handleCompletelyDeleteUser(checkItems)
                            : checkItems.some(it => it?.roles?.includes('ADMIN'))
                              ? await handleDeleteAdmin(checkItems)
                              : await handleDeleteUser(checkItems);
                    }
                    break;
                case 'Enter':
                    if (checkItems.length > 0) {
                        // 추후 필요 시 구현
                    }
                    break;
                case 'F2':
                    if (checkItems.length === 1) {
                        // 추후 필요 시 구현
                    }
                    break;
                case 'ArrowUp': {
                    e.preventDefault();
                    if (e.repeat) return; // OS 오토리핏 무시
                    startHold(-1);
                    break;
                }
                case 'ArrowDown': {
                    e.preventDefault();
                    if (e.repeat) return; // OS 오토리핏 무시
                    startHold(1);
                    break;
                }
                default:
                    break;
            }
        },
        [
            flatData,
            checkItems,
            activeIndex,
            updateSelection,
            handleDeleteAdmin,
            handleDeleteUser,
            selectSingleByIndex,
        ]
    );

    const handleToggleAllCheck = useCallback(() => {
        if (allCheck) {
            setCheckItems([]);
        } else {
            // 현재 로드된 데이터만 전체 선택
            setCheckItems(flatData);
        }
    }, [allCheck, flatData, setCheckItems]);

    const onContextMenuOpen = useCallback(
        (e, row) => {
            if (!checkedSetRef.current.has(row.userSeq)) setCheckItems([row]);
            handleContextMenu(e, row);
        },
        [setCheckItems, handleContextMenu]
    );

    useEffect(() => {
        checkedSetRef.current = checkedSet;
    }, [checkedSet]);

    const loadingMoreRef = useRef(false);

    const tryLoadMore = useCallback(() => {
        if (!hasNext) return;
        if (loadingMoreRef.current) return;

        loadingMoreRef.current = true;
        setCursorSearchCount(prev => prev + 1);
    }, [hasNext, setCursorSearchCount]);

    const dataLen = useMemo(() => {
        if (Array.isArray(data)) return data.length;
        return Object.values(data ?? {}).flat().length;
    }, [data]);

    useEffect(() => {
        loadingMoreRef.current = false;
    }, [dataLen]);

    useEffect(() => {
        if (rowAnchorRefs.current?.[menuTargetRow]) {
            setRowMenuAnchorEl(rowAnchorRefs.current[menuTargetRow]);
        }
    }, [menuTargetRow]);

    useEffect(() => {
        rootRef.current?.focus?.();
    }, []);

    // 라우트 이동 시에도 자동 포커스
    useEffect(() => {
        rootRef.current?.focus?.();
    }, [router.asPath]);

    useEffect(() => {
        activeIndexRef.current = activeIndex ?? 0;
    }, [activeIndex]);

    useEffect(() => {
        flatDataRef.current = flatData;
    }, [flatData]);

    // checkedSet은 ref로 미러링해서 itemContent deps 줄이기
    useEffect(() => {
        checkedSetRef.current = checkedSet;
    }, [checkedSet]);

    const visibleColumnCount = useMemo(() => {
        return columns.filter(col => {
            if (col.field === 'role' && !showRole) return false;
            if (col.field === 'lastLogin' && !showLastLogin) return false;
            if (col.field === 'status' && !showStatus) return false;
            if (col.field === 'requestedDate' && !showRequestedDate) return false;
            if (col.field === 'scheduledDate' && !showScheduledDate) return false;
            if (col.field === 'deletedBy' && !showDeletedBy) return false;
            return true;
        }).length;
    }, [
        columns,
        showRole,
        showLastLogin,
        showStatus,
        showRequestedDate,
        showScheduledDate,
        showDeletedBy,
    ]);

    const detailColSpan = 1 + visibleColumnCount; // 1 = 체크박스 컬럼

    // itemContent를 useCallback으로 고정
    const virtuosoItemContent = useCallback(
        (index, v) => {
            if (v.type === 'detail') {
                return (
                    <TableCell
                        colSpan={detailColSpan}
                        sx={{
                            borderBottom: `1px solid ${palette.table_divider_nomal}`,
                        }}
                    >
                        <Box
                            sx={{
                                ml: 1, // 좌우 여백
                                mr: 0.5,
                                my: 2, // 위아래 여백
                                p: 2, // 내부 padding
                                border: `1px solid ${palette.divider_12}`, // 테두리
                                borderRadius: 2, //
                                backgroundColor: 'white', // 박스 배경
                            }}
                        >
                            {/* 로그 테이블 컴포넌트 */}
                            {expandedSet.has(v.item.userSeq) && (
                                <Box sx={{ px: 2, py: 1 }}>
                                    <UserLogTable userSeq={v.item.userSeq} />
                                </Box>
                            )}
                        </Box>
                    </TableCell>
                );
            }

            return (
                <UserRowCells
                    item={v.item}
                    checked={checkedSetRef.current.has(v.item.userSeq)}
                    showRole={showRole}
                    showLastLogin={showLastLogin}
                    showStatus={showStatus}
                    showRequestedDate={showRequestedDate}
                    showScheduledDate={showScheduledDate}
                    showDeletedBy={showDeletedBy}
                    onContextMenuOpen={onContextMenuOpen}
                    onToggleCheck={handleToggleCheck}
                    t={t}
                    hasMultipleSelection={hasMultipleSelection}
                    onToggleExpand={toggleExpand} // ✅ 추가
                    expanded={expandedSet.has(v.item.userSeq)} // ✅ 추가
                />
            );
        },
        [
            showRole,
            showLastLogin,
            showStatus,
            showRequestedDate,
            showScheduledDate,
            showDeletedBy,
            handleToggleCheck,
            handleRowClickCombined,
            onContextMenuOpen,
            t,
            hasMultipleSelection,
            toggleExpand,
            expandedSet,
        ]
    );

    /**
     * (가상화) fixedHeaderContent
     * - 기존 TableHead 렌더를 Virtuoso가 요구하는 콜백으로 이동
     * - stickyHeader는 Table 컴포넌트에서 유지됨
     */
    const fixedHeaderContent = useCallback(() => {
        return (
            <TableRow>
                <TableCell align={'left'} sx={{ p: '0px 4px 0px 8px !important', maxWidth: 36 }}>
                    <Stack direction="row" justifyContent={'center'} alignItems="center">
                        <div className="toggle-wrap">
                            <span className="toggle-layer toggle-checkbox" style={{ opacity: 1 }}>
                                <BaseTooltip title={t('tooltip.selectAll')}>
                                    <StyledCheckbox
                                        data-testid="drive-checkbox"
                                        checked={allCheck}
                                        onChange={handleToggleAllCheck}
                                    />
                                </BaseTooltip>
                            </span>
                        </div>
                    </Stack>
                </TableCell>

                {columns.map(col => {
                    if (col.field === 'role' && !showRole) return null;
                    if (col.field === 'lastLogin' && !showLastLogin) return null;
                    if (col.field === 'status' && !showStatus) return null;
                    if (col.field === 'requestedDate' && !showRequestedDate) return null;
                    if (col.field === 'scheduledDate' && !showScheduledDate) return null;
                    if (col.field === 'deletedBy' && !showDeletedBy) return null;

                    return (
                        <TableCell
                            key={col.field}
                            ref={el => {
                                if (el && menuTargetRow === col.field) {
                                    columnAnchorRefs.current[col.field] = el;
                                }
                            }}
                            align={col.align}
                            sx={{
                                // background: menuAnchorEl && col.isField && `${palette.btn_white_bg_hover} !important`,
                                width: 'auto',
                                display: {
                                    xs: col.isMobile ? 'table-cell' : 'none',
                                    md: col.isDeletedBy ? 'none' : 'table-cell',
                                    midLg: 'table-cell',
                                },
                            }}
                        >
                            <Box
                                display="flex"
                                alignItems="center"
                                justifyContent={col.align ? col.align : 'flex-start'}
                            >
                                <Box display="flex" alignItems="center">
                                    <Typography
                                        variant="body9"
                                        color="text_60"
                                        sx={{ whiteSpace: 'nowrap' }}
                                    >
                                        {col.headerName ? t(col.headerName) : ''}
                                    </Typography>
                                </Box>
                            </Box>
                        </TableCell>
                    );
                })}
            </TableRow>
        );
    }, [
        t,
        allCheck,
        handleToggleAllCheck,
        columns,
        showRole,
        showLastLogin,
        showStatus,
        showRequestedDate,
        showScheduledDate,
        showDeletedBy,
        menuTargetRow,
    ]);

    return (
        <PerfProfiler id="UserTable" enabled={process.env.NEXT_PUBLIC_PERF_MODE === '1'}>
            <Root
                ref={rootRef}
                tabIndex={0}
                className={!isPc ? 'is-touch-device' : ''}
                onKeyDown={e => handleKeyDown(e)}
                onKeyUp={e => {
                    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') stopHold();
                }}
                onBlur={stopHold}
                onMouseDown={handleRootMouseDown}
                style={{ outline: 'none' }}
            >
                {/* (가상화) TableContainer/Table/TableBody 직접 렌더 대신 TableVirtuoso 사용 */}
                <TableVirtuoso
                    computeItemKey={(index, v) => {
                        // v.type === 'group' ? `g:${v.groupTitle}` : `r:${v.item.userSeq}`
                        if (v.type === 'group') return `g:${v.groupTitle}`;
                        if (v.type === 'detail') return `d:${v.item.userSeq}`;
                        return `r:${v.item.userSeq}`;
                    }}
                    ref={virtuosoRef} // (가상화) scrollToIndex 사용을 위한 ref
                    scrollerRef={el => {
                        scrollerElRef.current = el;
                    }} // 스크롤 컨테이너 DOM 직접 참조(marquee 선택용)
                    data={virtuosoData} // (가상화) group row 포함한 렌더링 데이터
                    components={VirtuosoTableComponents} // (가상화) MUI Table 매핑
                    fixedHeaderContent={fixedHeaderContent} // (가상화) 헤더 고정 렌더
                    // (가상화) 실제 row 렌더는 TableRow 커스텀에서 처리하므로 itemContent는 비워둠
                    itemContent={virtuosoItemContent}
                    context={{
                        // row가 알아야 하는 것들
                        virtuosoData,
                        checkedSetRef,

                        // handlers
                        onRowMouseDown: handleRowMouseDown,
                        onRowClickCombined: handleRowClickCombined,
                        onContextMenuOpen,
                    }}
                    // (가상화) 무한스크롤: scroll 이벤트 대신 atBottomStateChange 사용
                    atBottomStateChange={isAtBottom => {
                        // 스크롤이 실제 바닥일 때만 호출
                        if (isAtBottom) tryLoadMore();
                    }}
                    increaseViewportBy={{ top: 600, bottom: 600 }} // overscan 옵션: 스크롤이 부드럽게 보이는 정도로 설정
                />

                {/* 빈 영역 드래그 선택(marquee) 오버레이 */}
                {marqueeRect && marqueeRect.width > 0 && marqueeRect.height > 0 && (
                    <Box
                        sx={{
                            position: 'fixed',
                            zIndex: 1250,
                            pointerEvents: 'none',
                            left: marqueeRect.left,
                            top: marqueeRect.top,
                            width: marqueeRect.width,
                            height: marqueeRect.height,
                            border: '1px solid #6b88ff',
                            backgroundColor: '#6b88ff14',
                            borderRadius: '2px',
                        }}
                    />
                )}

                <StyledContextMenu
                    menuState={menuState}
                    onClose={handleClose}
                    reload={getData}
                    listPatch={listPatch}
                    checkItems={checkItems}
                    setCheckItems={setCheckItems}
                    user={user}
                />
            </Root>
        </PerfProfiler>
    );
}

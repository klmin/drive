import {
    Box,
    IconButton,
    ListItemText,
    Menu,
    MenuItem,
    Stack,
    Table,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
    TableBody,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { TableVirtuoso } from 'react-virtuoso';
import palette from '@/theme/palette';
import Image from 'next/image';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import { useTranslations } from 'next-intl';
import typography from '@/theme/typography';
import { useDialog } from '@/provider/DialogProvider';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useUser } from '@/context/UserContext';
import StyledContextMenu from '@/components/common/ContextMenu';
import checkExists from '@/utils/checkExists';
import { ArrowFilledDown } from '@/icon/Arrow';
import { useResources } from '@/provider/ResourceProvider';
import UploadBox from '@/components/views/UploadBox';
import { buildSortedQuery } from '@/utils/queryString';
import { useClipboard } from '@/provider/ClipboardProvider';
import { copyTextToClipboard } from '@/utils/clipboard';
import {
    getResourceActionDisabled,
    hasMyDriveResource,
    hasSharedRootResource,
} from '@/utils/resourceUtils';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { useDrivePreview } from '@/hooks/useDrivePreview';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import { PerfProfiler } from '@/tests/perf/PerfProfiler';
import { DriveRowCells } from './DriveRowCells';
import useDevice from '@/hooks/useDevice';
import useMarqueeSelection from '@/hooks/useMarqueeSelection';
import useStarredToggle from '@/hooks/useStarredToggle';
import useTrashRestore from '@/hooks/useTrashRestore';

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
        backgroundColor: 'white !important',
        borderBottom: `none !important`,
        zIndex: 99,
    },

    th: {
        padding: '0px 12px 0px 8px',
        backgroundColor: 'white !important',
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

    // hover 액션 아이콘
    // useDevice(UA 기반)으로 isPc 판단 후 루트에 is-touch-device 클래스 제어
    '.drive-row .row-actions': {
        opacity: 0,
        visibility: 'hidden',
        pointerEvents: 'none',
        transition: 'opacity 0.12s ease',
    },
    // PC: hover 시 표시
    '&:not(.is-touch-device) .drive-row:hover .row-actions': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },
    '&:not(.is-touch-device) .drive-row:hover .toggle-checkbox': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },
    '&:not(.is-touch-device) .drive-row:hover .toggle-icon': {
        opacity: 0,
        visibility: 'hidden',
    },
    '&:not(.is-touch-device) .drive-row:not(.is-checked):hover': {
        background: palette.table_bg_hover,
    },
    // 태블릿/모바일: 선택된 행에 항상 표시
    '&.is-touch-device .drive-row.is-checked .row-actions': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },

    // hover/checked 시 checkbox 표시
    '.drive-row .toggle-wrap': {
        position: 'relative',
        width: '36px',
        height: '36px',
    },
    '.drive-row .toggle-layer': {
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    '.drive-row .toggle-checkbox': {
        opacity: 0,
        visibility: 'hidden',
        pointerEvents: 'none',
    },
    '.drive-row .toggle-icon': {
        opacity: 1,
        visibility: 'visible',
    },
    '.drive-row.is-checked .toggle-checkbox': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },
    '.drive-row.is-checked .toggle-icon': {
        opacity: 0,
        visibility: 'hidden',
    },
    '.drive-row .toggle-checkbox, .drive-row .toggle-icon': {
        transition: 'opacity 0.12s ease',
    },

    // 정렬 cell hover 시 아이콘 표시
    '.th-sortable .sort-icon': {
        opacity: 0,
        transition: 'opacity 0.15s ease-in-out',
    },

    '.th-sortable:hover .sort-icon': {
        opacity: 1,
    },

    // 정렬이 걸린 컬럼은 hover 아니어도 보이게 (현재 sortBy 기준)
    '.th-sortable.is-sorted .sort-icon': {
        opacity: 1,
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
        if (!v || v.type === 'group') {
            return <TableRow {...rowProps} className="table-section-label" />;
        }

        const item = v.item;
        const checked = ctx.checkedSetRef.current.has(item.resourceSeq);

        return (
            <TableRow
                {...rowProps} // data-index/aria/virt style 유지
                className={`drive-row ${checked ? 'is-checked' : ''}`}
                onMouseDown={ctx.onRowMouseDown}
                onClick={e => ctx.onRowClickCombined(e, item)}
                onDoubleClick={e => {
                    ctx.onRowClickCombined(e, item);
                    ctx.onPreview(item, { preferEdit: true });
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
                    outline:
                        ctx.dragOverTargetSeq === item.resourceSeq && item.resourceType !== 'FILE'
                            ? '2px dashed #6b88ff'
                            : 'none',
                    outlineOffset: ctx.dragOverTargetSeq === item.resourceSeq ? '-1px' : '0',
                    cursor: 'pointer',
                    borderBottom: `1px solid ${palette.table_divider_nomal}`,
                    borderRadius:
                        ctx.dragOverTargetSeq === item.resourceSeq && item.resourceType !== 'FILE'
                            ? '6px'
                            : '',
                    background:
                        ctx.dragOverTargetSeq === item.resourceSeq && item.resourceType !== 'FILE'
                            ? '#6b88ff14'
                            : ctx.dragOverTargetSeq === item.resourceSeq &&
                                item.resourceType === 'FILE'
                              ? palette.table_divider_hover
                              : checked
                                ? palette.table_bg_selected
                                : undefined,
                    opacity: ctx.draggedItems?.some(d => d.resourceSeq === item.resourceSeq)
                        ? 0.4
                        : 1,
                }}
            />
        );
    },
};
VirtuosoTableComponents.Scroller.displayName = 'DriveTableScroller';
VirtuosoTableComponents.TableHead.displayName = 'DriveTableTableHead';
VirtuosoTableComponents.TableBody.displayName = 'DriveTableTableBody';

export default function DriveTable({
    data,
    getData,
    drawerOpen,
    setDrawerOpen,
    checkItems,
    setCheckItems,
    selectedDrive,
    setSelectedDrive,
    showLocation,
    showDate,
    showLike,
    showShare,
    shouldShowStar,
    showDeleteUser,
    isShouldShare,
    hasNext,
    isDragActive,
    cursorSearchCount,
    setCursorSearchCount,
    scrollResetKey,
    shareType,
    patchItem,
    removeItem,
    listPatch,
}) {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const user = useUser();
    const { refreshResources } = useResources();

    // (가상화) Virtuoso의 스크롤/인덱스 제어용 ref
    const virtuosoRef = useRef(null);

    const [recentSeeMore, setRecentSeeMore] = useState(null);

    const [menuAnchorEl, setMenuAnchorEl] = useState(null);
    const [rowMenuAnchorEl, setRowMenuAnchorEl] = useState(null);
    const [menuTargetRow, setMenuTargetRow] = useState('');
    const [dateFilter, setDateFilter] = useState(router.query.sb === 'open' || 'update');

    const columnAnchorRefs = useRef({});
    // recent/일반 공통: "아이템만" flat
    const flatData = useMemo(
        () => (Array.isArray(data) ? data : Object.values(data).flat()),
        [data]
    );
    const allCheck = useMemo(() => {
        if (!flatData?.length) return false;
        return checkItems.length === flatData.length;
    }, [checkItems.length, flatData.length]);
    const rowAnchorRefs = useRef({});
    const [dragOverTargetSeq, setDragOverTargetSeq] = useState(null);

    const { isPc } = useDevice();
    const isTrash = router.pathname.includes('/trash');
    const isShared = router.pathname.includes('shared');
    const isDriveOrTrash = router.pathname.includes('/drive') || isTrash;
    const hasMultipleSelection = checkItems.length >= 2;

    const sortBy = () => {
        if (router.query.sb) return router.query.sb;
        return isTrash ? 'register' : 'name';
    };
    const sortOrder = () => {
        if (router.query.so) return router.query.so;
        return isTrash ? 'desc' : 'asc';
    };

    const [draggedItems, setDraggedItems] = useState([]);

    // 단축키용
    const [anchorIndex, setAnchorIndex] = useState(null);
    const [activeIndex, setActiveIndex] = useState(null);
    const rootRef = useRef(null);

    // 클립보드
    const { clipboard, setClipboard, getClipboard } = useClipboard();

    const activeIndexRef = useRef(0);
    const flatDataRef = useRef([]);

    /** 공유 버튼 활성화 여부 */
    const isSharedRoot = item =>
        (isShared && !router?.query?.pfs) || (!isShared && hasSharedRootResource([item]));
    const isMyDrive = item => hasMyDriveResource([item]);

    const disablePreview = item => !isTrash && !item?.canView;
    const disableDownload = item => !isTrash && !item?.canDownload;
    const disableShare = item =>
        !item?.canShare ||
        !(isMyDrive(item) || isSharedRoot(item) || (!isSharedRoot(item) && item?.shareSeq));

    const [hoveredSortField, setHoveredSortField] = useState(null);

    const getLocation = item => {
        if (item?.parentFolderName) return item.parentFolderName;
        return router.pathname.includes('/shared') || item?.resourceLocation === 'SHARE'
            ? t('common.label.shared')
            : t('common.label.myDrive');
    };

    const getLocationTooltip = item => {
        if (!item?.fullPath) return '';
        const root =
            router.pathname.includes('/shared') || item?.resourceLocation === 'SHARE'
                ? t('common.label.shared')
                : t('common.label.myDrive');
        return root + '/' + item.fullPath;
    };

    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const handleClose = () => {
        setMenuState({ ...menuState, visible: false });
    };

    const handleContextMenu = (e, row) => {
        e.preventDefault();
        setMenuState({
            visible: true,
            top: e.clientY,
            left: e.clientX,
            selectedRow: row,
        });
    };

    const pathColumnConfig = {
        '/drive': {
            field: dateFilter === 'update' ? 'update' : 'open',
            headerName: dateFilter === 'update' ? 'data.lastModified' : 'data.lastActivity',
            filter: [
                { name: 'data.lastModified', value: 'update' },
                { name: 'data.lastActivity', value: 'open' },
            ],
            isSort: true,
            isField: true,
            align: 'center',
        },
        '/recent': {
            field: 'open',
            headerName: 'data.lastActivity',
            isSort: false,
            isField: false,
            align: 'center',
        },
        '/starred': {
            field: 'update',
            headerName: 'data.lastModified',
            isSort: true,
            isField: false,
            align: 'center',
        },
        '/shared': {
            field: dateFilter === 'update' ? 'update' : 'share',
            headerName: dateFilter === 'update' ? 'data.lastModified' : 'data.lastActivity',
            filter: [
                { name: 'data.lastModified', value: 'update' },
                { name: 'data.lastActivity', value: 'share' },
            ],
            isSort: true,
            isField: true,
            align: 'center',
        },
        '/trash': {
            field: 'register',
            headerName: 'data.deleteDate',
            isSort: true,
            isField: false,
            align: 'center',
        },
        '/search': {
            field: 'update',
            headerName: 'data.lastModified',
            isSort: true,
            isField: false,
            align: 'center',
        },
    };

    const columns = [
        {
            field: 'name',
            headerName: 'data.name',
            isSort: !router.pathname.includes('/recent'),
            isMobile: true,
        },
        { field: '', headerName: '', align: 'center' },
        { field: 'owner', headerName: 'common.label.owner', spacing: true, align: 'left' },
        { field: 'deleteUser', headerName: 'data.deleteUser', spacing: true, align: 'left' },
        { ...pathColumnConfig[router.pathname], spacing: true },
        {
            field: 'size',
            headerName: 'common.label.fileSize',
            isSort: !router.pathname.includes('/recent'),
            align: 'left',
            isSize: true,
        },
        { field: 'location', headerName: 'data.location', isLocation: true },
        { field: 'options', headerName: '', isMobile: true, isOption: true },
    ];

    const handleToggleCheck = useCallback(
        item => {
            setCheckItems(prev => {
                const exists = (prev ?? []).some(i => i.resourceSeq === item.resourceSeq);
                return exists
                    ? prev.filter(i => i.resourceSeq !== item.resourceSeq)
                    : [...prev, item];
            });
        },
        [setCheckItems]
    );

    // 중요문서함 토글은 목록 전체 재조회 대신 해당 행만 갱신한다(스크롤 위치·선택 상태 유지).
    // patchItem/removeItem을 넘기지 않은 페이지에서는 훅이 reload로 폴백한다.
    const { toggleStarred } = useStarredToggle({ patchItem, removeItem, reload: getData });
    const handleStarred = useCallback(item => toggleStarred(item), [toggleStarred]);

    const { singleDownload } = useDriveDownload({ t });

    const handleMoveItem = async (draggedItems, dropTargetItem) => {
        if (draggedItems.some(i => i.resourceSeq === dropTargetItem.resourceSeq)) return;
        try {
            for (const item of draggedItems) {
                const moveFunc = async () => {
                    if (dropTargetItem.resourceType === 'FILE') return;
                    const res = await api.Post(
                        item?.resourceType === 'FILE'
                            ? `files/${item.resourceSeq}/move`
                            : `folders/${item.resourceSeq}/move`,
                        {
                            ts: dropTargetItem.userSeq,
                            ss:
                                dropTargetItem?.sharedByUserSeq ||
                                dropTargetItem?.userSeq ||
                                undefined,
                            pfs: dropTargetItem.resourceSeq,
                        }
                    );
                    if (res?.code === 200) {
                        showSnackbar(t('move.success'), 'success');
                        // 이동된 행만 반영한다(스크롤 위치 유지)
                        if (listPatch?.move) await listPatch.move(item);
                        else await getData({}, true);
                    } else if (res?.status === 423) {
                        showSnackbar(t('lock.locked'), 'error');
                    } else {
                        showSnackbar(t('move.fail.general'), 'error');
                    }
                };

                // 중복 검사는 원본이 아닌 드롭 대상 드라이브 기준으로 조회
                const exists = await checkExists(
                    {
                        resourceName: item.resourceName,
                        resourceType: item.resourceType,
                        ss: dropTargetItem?.sharedByUserSeq || dropTargetItem?.userSeq,
                    },
                    dropTargetItem.resourceSeq || router.query.pfs,
                    moveFunc
                );

                if (exists?.resourceSeq) {
                    openDialog('alert', {
                        title: t('duplicateDialog.text1', {
                            resourceName: item.resourceName,
                            resourceType: item.resourceType,
                        }),
                        description: t('duplicateDialog.text2'),
                        onClick: moveFunc,
                        buttonName: t('common.button.upload'),
                    });
                } else if (exists === 200) {
                    await moveFunc();
                    setCheckItems([]);
                }
            }
        } catch (err) {
            console.error('파일이동 실패:', err);
            showSnackbar(t('move.fail.general'), 'error');
        }
    };

    const handleDragStart = (e, item) => {
        if (!router.pathname.includes('/drive') && !router.pathname.includes('/shared')) {
            e.preventDefault();
            return;
        }
        if (e.dataTransfer.types.includes('Files')) return;

        e.stopPropagation();

        let dragData;
        const isChecked = checkedSet.has(item.resourceSeq);

        if (isChecked) {
            dragData = checkItems;
        } else {
            dragData = [item];
            setCheckItems([item]);
        }

        setDraggedItems(dragData);

        const ghost = createDragGhost(dragData.length);
        e.dataTransfer.setDragImage(ghost, 0, 0);

        e.dataTransfer.setData('application/json', JSON.stringify(dragData));
        e.dataTransfer.effectAllowed = 'move';
    };

    const createDragGhost = count => {
        const ghost = document.createElement('div');
        ghost.style.position = 'absolute';
        ghost.style.top = '-1000px';
        ghost.style.padding = '11px 16px';
        ghost.style.backgroundColor = palette.white;
        ghost.style.color = '#3a3d49';
        ghost.style.borderRadius = '8px';
        ghost.style.fontSize = '14px';
        ghost.style.outline = 'none';
        const countSpan = `<span style="color:${palette.text_tab_selected};font-weight:bold;">${count}</span>`;
        ghost.innerHTML = t('moveDialog.subTitleMultiple', { count: countSpan });
        document.body.appendChild(ghost);
        return ghost;
    };

    /** 드롭 가능 대상 — 폴더이면서 끌고 있는 항목이 아닌 것 */
    const isDropTarget = item =>
        item?.resourceType !== 'FILE' &&
        !draggedItems.some(d => d.resourceSeq === item?.resourceSeq);

    const handleDragOver = (e, item) => {
        if (!router.pathname.includes('/drive') && !router.pathname.includes('/shared')) {
            e.preventDefault();
            return;
        }
        if (e.dataTransfer.types.includes('Files')) return;
        e.preventDefault();
        if (!isDropTarget(item)) {
            // 상위 드롭존이 허용으로 덮지 않도록 여기서 멈춘다
            e.stopPropagation();
            e.dataTransfer.dropEffect = 'none';
            setDragOverTargetSeq(null);
            return;
        }
        setDragOverTargetSeq(item.resourceSeq);
    };

    const handleDragLeave = e => {
        if (e.dataTransfer.types.includes('Files')) return;
        e.preventDefault();
        setDragOverTargetSeq(null);
    };

    const handleDrop = (e, dropTargetItem) => {
        if (!router.pathname.includes('/drive') && !router.pathname.includes('/shared')) {
            e.preventDefault();
            return;
        }
        if (e.dataTransfer.types.includes('Files')) return;

        e.preventDefault();
        setDragOverTargetSeq(null);

        const draggedItemJson = e.dataTransfer.getData('application/json');
        if (draggedItemJson) {
            const droppedItems = JSON.parse(draggedItemJson);
            if (
                dropTargetItem.resourceType !== 'FILE' &&
                !droppedItems.some(d => d.resourceSeq === dropTargetItem.resourceSeq)
            ) {
                handleMoveItem(droppedItems, dropTargetItem);
            }
        }
    };

    const handleDragEnd = () => setDraggedItems([]);

    const { restoreItems } = useTrashRestore({
        onRestored: async seqs => {
            // 복원된 행만 목록에서 제거한다
            if (listPatch?.remove) listPatch.remove(seqs);
            else await getData(true);
            setCheckItems([]);
            refreshResources();
        },
    });

    const handleRestore = item => restoreItems(item);

    const handleCompletelyDeleteDrive = async items => {
        let successCount = 0;
        let failCount = 0;

        openDialog('alert', {
            title: t('hardDeleteDialog.text1'),
            description:
                items.length === 1
                    ? t('hardDeleteDialog.text2Single', { resourceName: items[0].resourceName })
                    : items.length > 1 && t('hardDeleteDialog.text2', { count: items.length }),
            buttonName: t('common.action.hardDelete'),
            cancelText: t('common.button.cancel'),
            onClick: async () => {
                // 성공한 항목만 목록에서 제거하기 위해 따로 모은다
                const deletedSeqs = [];

                for (let item of items) {
                    const res = await (item?.resourceType === 'FOLDER'
                        ? api.Delete(`trash/folders/${item?.resourceSeq}`, {})
                        : api.Delete(`trash/files/${item?.resourceSeq}`, {}));

                    if (res?.status === 200) {
                        successCount++;
                        deletedSeqs.push(item?.resourceSeq);
                    } else failCount++;
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(t('hardDelete.success'), 'success');
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t('hardDelete.partial', { success: successCount, fail: failCount }),
                        'warning'
                    );
                } else {
                    showSnackbar(t('hardDelete.fail'), 'error');
                }

                setCheckItems([]);
                if (listPatch?.remove) listPatch.remove(deletedSeqs);
                else await getData(true);
            },
        });
    };

    const handleDeleteDrive = async items => {
        let successCount = 0;
        let failCount = 0;
        let lockedCount = 0;

        openDialog('alert', {
            title: t('common.action.moveToTrash'),
            description: t('Trash.text'),
            onClick: async () => {
                const deletedSeqs = [];

                for (let item of items) {
                    const res = await (item?.resourceType === 'FOLDER'
                        ? api.Patch(`folders/${item?.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          })
                        : api.Patch(`files/${item?.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          }));

                    if (res?.status === 200) {
                        successCount++;
                        deletedSeqs.push(item?.resourceSeq);
                    } else if (res?.status === 423) {
                        lockedCount++;
                        failCount++;
                    } else {
                        failCount++;
                    }
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(t('moveToTrash.success'), 'success');
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t('moveToTrash.partial', {
                            success: successCount,
                            fail: failCount,
                            locked: lockedCount,
                        }),
                        'warning'
                    );
                } else {
                    if (lockedCount > 0) showSnackbar(t('lock.locked'), 'error');
                    else showSnackbar(t('moveToTrash.fail'), 'error');
                }

                setCheckItems([]);
                if (listPatch?.remove) listPatch.remove(deletedSeqs);
                else await getData(true);
            },
        });
    };

    const { handlePreview } = useDrivePreview({ t, handleRestore });

    const handleSort = async newSortBy => {
        const sb = newSortBy;
        let so = 'asc';
        if (sortBy() === newSortBy) so = sortOrder() === 'asc' || !sortOrder() ? 'desc' : 'asc';

        const root = router.pathname;
        const queryParams = buildSortedQuery(router.query, sb, so);
        router.push({ pathname: root, query: queryParams }, undefined, { shallow: true });
    };

    const handleRename = async item => {
        openDialog('fileName', { item, setCheckItems, reload: getData, listPatch });
    };

    const checkedSet = useMemo(
        () => new Set((checkItems ?? []).map(it => it.resourceSeq)),
        [checkItems]
    );
    const checkedSetRef = useRef(checkedSet);

    const idIndexMap = useMemo(() => {
        const m = new Map();
        flatData.forEach((it, i) => m.set(it.resourceSeq, i));
        return m;
    }, [flatData]);

    const updateSelection = useCallback(nextItems => setCheckItems(nextItems), [setCheckItems]);

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
            const exists = checkedSet.has(it.resourceSeq);
            updateSelection(
                exists
                    ? checkItems.filter(x => x.resourceSeq !== it.resourceSeq)
                    : [...checkItems, it]
            );
            setActiveIndex(idx);
        },
        [flatData, checkItems, updateSelection, checkedSet]
    );

    const selectRangeToIndex = useCallback(
        idx => {
            const anchor = anchorIndex ?? idx;
            const start = Math.min(anchor ?? idx, idx);
            const end = Math.max(anchor ?? idx, idx);

            const rangeItems = flatData.slice(start, end + 1);

            const safeSelected = Array.isArray(checkItems) ? checkItems : [];
            const existingIds = new Set(safeSelected.map(it => it.resourceSeq));
            for (const it of rangeItems) existingIds.add(it.resourceSeq);

            const merged = flatData.filter(it => existingIds.has(it.resourceSeq));

            updateSelection(merged);
            setActiveIndex(idx);
            setAnchorIndex(idx);
        },
        [flatData, anchorIndex, checkItems, updateSelection]
    );

    const handleRowClickCombined = useCallback(
        (e, item) => {
            const idx = idIndexMap.get(item.resourceSeq) ?? 0;

            if (e.metaKey || e.ctrlKey) {
                toggleOneByIndex(idx);
                setActiveIndex(idx);
                setAnchorIndex(idx);
                return;
            }
            if (e.shiftKey) {
                selectRangeToIndex(idx);
                return;
            }
            selectSingleByIndex(idx);
            setActiveIndex(idx);
            setAnchorIndex(idx);
        },
        [idIndexMap, toggleOneByIndex, selectRangeToIndex, selectSingleByIndex]
    );

    const onCopy = useCallback(async () => {
        if (!checkItems || checkItems.length === 0) return;

        const names = checkItems.map(it => it.resourceName).join('\n');
        const success = await copyTextToClipboard(names, t('shareClipboardPrompt.description'));

        if (success) {
            setClipboard({ mode: 'copy', items: [...checkItems] });
            showSnackbar(t('clipboard.copySuccess', { count: checkItems.length }), 'success');
        } else {
            showSnackbar(t('clipboard.copyFail'), 'error');
        }
    }, [checkItems, setClipboard, showSnackbar, t]);

    const isTextEditingTarget = el => {
        const tag = (el?.tagName || '').toLowerCase();
        if (tag === 'textarea') return true;
        if (el?.isContentEditable) return true;
        if (tag !== 'input') return false;

        const type = (el.type || '').toLowerCase();
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

    const handleRowMouseDown = useCallback(e => {
        if (e.shiftKey && !isTextEditingTarget(e.target)) {
            e.preventDefault();
            window.getSelection()?.removeAllRanges();
        }
    }, []);

    const handleRootMouseDown = useCallback(
        e => {
            if (!isTextEditingTarget(e.target)) rootRef.current?.focus?.();
            // 테이블 안 빈 영역(행/헤더/컨트롤이 아닌 곳, 예: 짧은 목록의 하단 여백)
            // 클릭 시 선택 해제
            if (
                !e.target?.closest?.(
                    '.drive-row, .table-section-label, thead, button, a, input, textarea, select, label, [role="menu"], [role="menuitem"], [data-marquee-ignore]'
                )
            ) {
                setCheckItems(prev => (prev.length ? [] : prev));
            }
        },
        [setCheckItems]
    );

    const holdRef = useRef({
        active: false,
        dir: 0,
        rafId: 0,
        lastStepAt: 0,
        interval: 90,
    });

    // (가상화) "아이템 index" → "Virtuoso(렌더링) index" 매핑이 필요
    // - recent는 group row가 섞이므로, scrollToIndex에 그대로 item index를 넣으면 틀어짐
    const virtuosoData = useMemo(() => {
        // (가상화) 화면 렌더링은 group + row를 한 리스트로
        if (Array.isArray(data)) {
            return data.map(item => ({ type: 'row', item }));
        }
        const list = [];
        Object.entries(data).forEach(([groupTitle, items]) => {
            list.push({ type: 'group', groupTitle });
            (items ?? []).forEach(item => list.push({ type: 'row', item }));
        });
        return list;
    }, [data]);

    const virtuosoIndexBySeq = useMemo(() => {
        // (가상화) resourceSeq → virtuoso index
        const m = new Map();
        virtuosoData.forEach((v, idx) => {
            if (v?.type === 'row' && v?.item?.resourceSeq) {
                m.set(v.item.resourceSeq, idx);
            }
        });
        return m;
    }, [virtuosoData]);

    const setSelectionByIndex = useCallback(
        idx => {
            const list = flatDataRef.current;
            const it = list[idx];
            if (!it) return;

            activeIndexRef.current = idx;
            setActiveIndex(idx);
            setCheckItems([it]);
        },
        [setCheckItems]
    );

    const scrollToItemIndex = useCallback(
        idx => {
            // (가상화) 선택된 "아이템 index"를 virtuoso index로 변환해 스크롤
            const list = flatDataRef.current;
            const it = list[idx];
            if (!it) return;

            const vIdx = virtuosoIndexBySeq.get(it.resourceSeq);
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

        activeIndexRef.current = next;
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

        stepMove(dir);

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

    const handleKeyDown = useCallback(
        async e => {
            if (isTextEditingTarget(e.target)) return;

            const withCmd = e.metaKey;
            const withCtrl = e.ctrlKey;
            const withCtrlOrCmd = withCmd || withCtrl;
            const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;
            // 메뉴와 같은 권한 기준 (휴지통 메뉴는 미리보기·영구삭제를 막지 않음)
            const disabled = getResourceActionDisabled(checkItems);

            if (withCtrlOrCmd && key === 'a') {
                e.preventDefault();
                updateSelection(flatData);
                return;
            }
            if (withCtrlOrCmd && key === 'c') {
                e.preventDefault();
                if (disabled.disableCopy) return;
                await onCopy();
                return;
            }
            if (withCtrlOrCmd && key === 'v') {
                e.preventDefault();
                return;
            }

            switch (key) {
                case 'Escape':
                    updateSelection([]);
                    setActiveIndex(null);
                    setAnchorIndex(null);
                    break;
                case 'Delete':
                    if (checkItems.length === 0) break;
                    if (isTrash) await handleCompletelyDeleteDrive(checkItems);
                    else if (!disabled.disableDelete) await handleDeleteDrive(checkItems);
                    break;
                case 'Enter':
                    if (checkItems.length === 0) break;
                    if (isTrash || !disabled.disablePreview)
                        handlePreview(checkItems[0], { preferEdit: true });
                    break;
                case 'F2':
                    if (checkItems.length === 1 && !isTrash && !disabled.disableRename) {
                        handleRename(checkItems[0]);
                    }
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    if (e.repeat) return;
                    startHold(-1);
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    if (e.repeat) return;
                    startHold(1);
                    break;
                default:
                    break;
            }
        },
        [flatData, checkItems, updateSelection, handleDeleteDrive, handlePreview, onCopy, isTrash]
    );

    const myProfileSrc = useMemo(() => {
        if (!isDriveOrTrash) return;
        const seq = user?.userSeq;
        if (!seq) return '';
        return api.getProfileImage(seq);
    }, [user?.userSeq, isDriveOrTrash]);

    const ownerProfileSrcMap = useMemo(() => {
        const m = new Map();
        if (isDriveOrTrash) return m;

        for (const it of flatData) {
            const ownerSeq = it?.ownerSeq;
            if (!ownerSeq) continue;
            if (!m.has(ownerSeq)) m.set(ownerSeq, api.getProfileImage(ownerSeq));
        }
        return m;
    }, [flatData, isDriveOrTrash]);

    const deleteUserProfileSrcMap = useMemo(() => {
        const m = new Map();
        if (!showDeleteUser) return m;

        for (const it of flatData) {
            const seq = it?.deleteUserSeq;
            if (!seq) continue;
            if (!m.has(seq)) m.set(seq, api.getProfileImage(seq));
        }
        return m;
    }, [flatData, showDeleteUser]);

    const getDeleteUserProfileSrc = useCallback(
        item => {
            if (!showDeleteUser) return '';
            const seq = item?.deleteUserSeq;
            return (seq && deleteUserProfileSrcMap.get(seq)) || '';
        },
        [showDeleteUser, deleteUserProfileSrcMap]
    );

    const getOwnerProfileSrc = useCallback(
        item => {
            if (isDriveOrTrash) return myProfileSrc;
            const ownerSeq = item?.ownerSeq;
            return (ownerSeq && ownerProfileSrcMap.get(ownerSeq)) || '';
        },
        [isDriveOrTrash, myProfileSrc, ownerProfileSrcMap]
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
            if (!checkedSetRef.current.has(row.resourceSeq)) setCheckItems([row]);
            handleContextMenu(e, row);
        },
        [setCheckItems, handleContextMenu]
    );

    useEffect(() => {
        checkedSetRef.current = checkedSet;
    }, [checkedSet]);

    // 추가 로딩 중복 호출 방지 락
    // tryLoadMore 호출 시 true로 설정되고, 새 데이터가 도착하면 false로 해제됨
    const loadingMoreRef = useRef(false);
    // TableVirtuoso의 스크롤 컨테이너 DOM 엘리먼트를 직접 참조.
    // VirtuosoHandle의 getScrollElement()는 지원되지 않을 수 있으므로 scrollerRef prop으로 직접 획득
    const scrollerElRef = useRef(null);

    // 빈 영역(좌우 여백 포함) 드래그로 행을 사각형 선택.
    // - 행 위에서 시작하는 드래그(=이동)와 충돌하지 않음(시작 대상으로 분기)
    // - 배열 데이터: 균일 행 높이 기반 기하 계산(화면 밖 행도 즉시 선택)
    // - 그룹 데이터(recent 등): 행 높이 불균일 → 히트테스트 모드. 날짜 그룹 헤더는
    //   데이터 행(.drive-row)이 아니므로 자연히 선택에서 제외됨.
    const { marqueeRect } = useMarqueeSelection({
        scrollerRef: scrollerElRef,
        enabled: isPc,
        uniform: Array.isArray(data),
        getItems: () => flatDataRef.current,
        setCheckItems,
        resolveIndex: vIdx => {
            const v = virtuosoData[vIdx];
            return v && v.type === 'row' ? v.item : null;
        },
        getKey: it => it.resourceSeq,
    });

    // 스크롤이 바닥에 닿거나 화면이 채워지지 않을 때 추가 데이터를 요청.
    // loadingMoreRef로 중복 호출을 방지하고, 부모의 cursorSearchCount를 증가시켜 요청을 트리거함.
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

    // [리스트가 화면을 채우지 못할 때 자동 추가 로딩]
    //
    // atBottomStateChange는 isAtBottom 값이 '변경'될 때만 콜백 받음
    // 예) 20개 로드 → 화면 미충족 → isAtBottom = true → 콜백 → 추가 요청 → 10개 더 로드
    //     30개인데도 화면 미충족 → isAtBottom 여전히 true → true→true라 추가 로드x → 영원히 미충족
    //
    // 이를 보완하기 위해 data가 바뀔 때마다 스크롤 컨테이너를 직접 측정하여,
    // 콜백이 오지 않더라도 화면 미충족 상태면 스스로 추가 요청하도록 수정함
    //
    // dataLen 대신 data 레퍼런스 감지로 변경
    // 서브쿼리 이동 등 아이템 수가 동일해도 데이터가 교체되는 경우에 대응하기 위함
    // (setResourceList(newData)는 항상 새 배열을 생성)
    useEffect(() => {
        loadingMoreRef.current = false; // 새 데이터 도착 시 락 해제

        const raf = requestAnimationFrame(() => {
            // RAF 실행 전에 atBottomStateChange가 먼저 tryLoadMore를 호출했다면
            // loadingMoreRef가 true이므로 중복 요청 방지
            if (loadingMoreRef.current) return;

            // scrollHeight <= clientHeight: 리스트가 컨테이너 높이를 채우지 못한 상태
            const scrollEl = scrollerElRef.current;
            if (scrollEl && scrollEl.scrollHeight <= scrollEl.clientHeight) {
                tryLoadMore();
            }
        });
        return () => cancelAnimationFrame(raf);
    }, [data]);

    /** 목록이 1페이지부터 다시 채워지면 스크롤도 맨 위로 되돌린다(행 단위 갱신 시에는 유지) */
    useEffect(() => {
        if (!scrollResetKey) return;
        if (scrollerElRef.current) scrollerElRef.current.scrollTop = 0;
    }, [scrollResetKey]);

    useEffect(() => {
        if (rowAnchorRefs.current?.[menuTargetRow]) {
            setRowMenuAnchorEl(rowAnchorRefs.current[menuTargetRow]);
        }
    }, [menuTargetRow]);

    useEffect(() => {
        if (drawerOpen) handleClose();
    }, [drawerOpen]);

    useEffect(() => {
        handleClose();
        setCheckItems(prev => (prev?.length ? [] : prev));
    }, [router.asPath]);

    useEffect(() => {
        rootRef.current?.focus?.();
    }, []);

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

    // itemContent를 useCallback으로 고정
    const virtuosoItemContent = useCallback(
        (index, v) => {
            if (v.type === 'group') {
                return (
                    <TableCell colSpan={10}>
                        <Typography variant="body6">{v.groupTitle}</Typography>
                    </TableCell>
                );
            }

            return (
                <DriveRowCells
                    item={v.item}
                    checked={checkedSetRef.current.has(v.item.resourceSeq)} // ref 사용
                    drawerOpen={drawerOpen}
                    showLocation={showLocation}
                    showDeleteUser={showDeleteUser}
                    showLike={showLike}
                    showShare={showShare}
                    shouldShowStar={shouldShowStar}
                    isShouldShare={isShouldShare}
                    onToggleCheck={handleToggleCheck}
                    onRowClickCombined={handleRowClickCombined}
                    onContextMenuOpen={onContextMenuOpen}
                    onPreview={handlePreview}
                    onStar={handleStarred}
                    onRestore={handleRestore}
                    onHardDelete={handleCompletelyDeleteDrive}
                    onDownload={singleDownload}
                    openDialog={openDialog}
                    hasMultipleSelection={hasMultipleSelection}
                    disablePreview={disablePreview}
                    disableDownload={disableDownload}
                    disableShare={disableShare}
                    t={t}
                    getData={getData}
                    listPatch={listPatch}
                    getOwnerProfileSrc={getOwnerProfileSrc}
                    getDeleteUserProfileSrc={getDeleteUserProfileSrc}
                    getLocation={getLocation}
                    getLocationTooltip={getLocationTooltip}
                />
            );
        },
        [
            drawerOpen,
            showLocation,
            showDeleteUser,
            showLike,
            showShare,
            shouldShowStar,
            isShouldShare,
            handleToggleCheck,
            handleRowClickCombined,
            onContextMenuOpen,
            handlePreview,
            handleStarred,
            handleRestore,
            handleCompletelyDeleteDrive,
            singleDownload,
            openDialog,
            hasMultipleSelection,
            disablePreview,
            disableDownload,
            disableShare,
            t,
            listPatch,
            getOwnerProfileSrc,
            getDeleteUserProfileSrc,
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
                <TableCell align={'left'} sx={{ p: '0px 4px 0px 8px !important' }}>
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
                    if (col.field === 'location' && !showLocation) return null;
                    if (col.field === 'deleteUser' && !showDeleteUser) return null;

                    return (
                        <TableCell
                            key={col.field}
                            className={col.isSort ? 'th-sortable' : undefined}
                            ref={el => {
                                if (el && menuTargetRow === col.field) {
                                    columnAnchorRefs.current[col.field] = el;
                                }
                            }}
                            align={col.align}
                            sx={{
                                background:
                                    menuAnchorEl && col.isField
                                        ? `${palette.btn_white_bg_hover} !important`
                                        : undefined,
                                width: col.isField ? 127 : col.field === 'owner' ? 0 : 'auto',
                                display: {
                                    xs: col.isMobile ? 'table-cell' : 'none',
                                    md: col.isSize ? 'none' : 'table-cell',
                                    lg: col.isSize && drawerOpen ? 'none' : 'table-cell',
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

                                    <IconButton
                                        className="sort-icon"
                                        size="small"
                                        disabled={!col.isSort}
                                        onClick={
                                            col.isSort ? () => handleSort(col.field) : undefined
                                        }
                                        sx={{
                                            opacity:
                                                col.isSort &&
                                                (hoveredSortField === col.field ||
                                                    sortBy() === col.field)
                                                    ? 1
                                                    : 0,
                                            transition: 'opacity 0.15s ease-in-out',
                                            pointerEvents: col.isSort ? 'auto' : 'none',
                                        }}
                                    >
                                        {col.isSort &&
                                            (sortBy() === col.field ? (
                                                sortOrder() === 'desc' ? (
                                                    <Image
                                                        src="/drive/chevron-chevron-stroke-12-down.svg"
                                                        alt="down"
                                                        width={12}
                                                        height={12}
                                                    />
                                                ) : (
                                                    <Image
                                                        src="/drive/chevron-chevron-stroke-12-up.svg"
                                                        alt="up"
                                                        width={12}
                                                        height={12}
                                                    />
                                                )
                                            ) : (
                                                <Image
                                                    src="/drive/chevron-chevron-stroke-12-up.svg"
                                                    alt="up"
                                                    width={12}
                                                    height={12}
                                                />
                                            ))}
                                    </IconButton>

                                    {col.isField && (
                                        <ArrowFilledDown
                                            opacity={'.6'}
                                            onClick={e => {
                                                e.stopPropagation();
                                                const parentCell =
                                                    e.currentTarget.closest('th, td');
                                                if (parentCell) setMenuAnchorEl(parentCell);
                                            }}
                                        />
                                    )}
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
        showLocation,
        showDeleteUser,
        hoveredSortField,
        menuAnchorEl,
        drawerOpen,
        menuTargetRow,
        sortBy,
        sortOrder,
    ]);

    return (
        <PerfProfiler id="DriveTable" enabled={process.env.NEXT_PUBLIC_PERF_MODE === '1'}>
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
                <UploadBox
                    isDragActive={
                        router.pathname.includes('shared') && !router.query.pfs
                            ? false
                            : isDragActive
                    }
                />

                {/* (가상화) TableContainer/Table/TableBody 직접 렌더 대신 TableVirtuoso 사용 */}
                <TableVirtuoso
                    computeItemKey={(index, v) =>
                        v.type === 'group' ? `g:${v.groupTitle}` : `r:${v.item.resourceSeq}`
                    }
                    ref={virtuosoRef} // (가상화) scrollToIndex 사용을 위한 ref
                    scrollerRef={el => {
                        scrollerElRef.current = el;
                    }} // 스크롤 컨테이너 DOM 직접 참조
                    data={virtuosoData} // (가상화) group row 포함한 렌더링 데이터
                    components={VirtuosoTableComponents} // (가상화) MUI Table 매핑
                    fixedHeaderContent={fixedHeaderContent} // (가상화) 헤더 고정 렌더
                    // (가상화) 실제 row 렌더는 TableRow 커스텀에서 처리하므로 itemContent는 비워둠
                    itemContent={virtuosoItemContent}
                    context={{
                        // row가 알아야 하는 것들
                        virtuosoData,
                        checkedSetRef,
                        dragOverTargetSeq,
                        draggedItems,

                        // handlers
                        onRowMouseDown: handleRowMouseDown,
                        onRowClickCombined: handleRowClickCombined,
                        onPreview: handlePreview,
                        onContextMenuOpen,
                        onDragStart: handleDragStart,
                        onDragOver: handleDragOver,
                        onDrop: handleDrop,
                        onDragLeave: handleDragLeave,
                        onDragEnd: handleDragEnd,
                    }}
                    // (가상화) 무한스크롤: 사용자가 스크롤을 맨 아래까지 내렸을 때 추가 로딩.
                    // 맨 아래 도달 여부가 변경될 때만 콜백을 받으므로, 리스트가 화면을 채우지 못하는 경우는
                    // 위의 useEffect([data]) RAF에서 보완.
                    atBottomStateChange={isAtBottom => {
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

                <Menu
                    open={Boolean(menuAnchorEl)}
                    anchorEl={menuAnchorEl}
                    onClose={() => setMenuAnchorEl(null)}
                    selected={dateFilter}
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
                    transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                    PaperProps={{
                        elevation: 2,
                        sx: {
                            mt: 2,
                            minWidth: 179,
                            borderRadius: 1,
                            boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                            border: `1px solid ${palette.control_border_normal}`,
                            backgroundColor: palette.btn_white_bg_normal,
                        },
                    }}
                >
                    {pathColumnConfig?.[router.pathname]?.filter?.map(item => (
                        <MenuItem
                            key={item.name}
                            onClick={() => {
                                setDateFilter(item.value);
                                getData(true);
                                setMenuAnchorEl(null);
                                handleSort(item.value);
                            }}
                            sx={{ gap: 2.5, pr: 12, pl: 3 }}
                        >
                            {item.value === dateFilter ? (
                                <Image
                                    src={'/common/icon-ic-check.svg'}
                                    alt="check"
                                    width={20}
                                    height={20}
                                />
                            ) : (
                                <Box sx={{ width: 20 }} />
                            )}

                            <ListItemText primary={t(item.name)} />
                        </MenuItem>
                    ))}
                </Menu>

                <StyledContextMenu
                    menuState={menuState}
                    onClose={handleClose}
                    reload={getData}
                    checkItems={checkItems}
                    setCheckItems={setCheckItems}
                    setDrawerOpen={setDrawerOpen}
                    setSelectedDrive={setSelectedDrive}
                    user={user}
                    patchItem={patchItem}
                    removeItem={removeItem}
                    listPatch={listPatch}
                />
            </Root>
        </PerfProfiler>
    );
}

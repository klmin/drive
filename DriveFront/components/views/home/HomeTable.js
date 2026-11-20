import {
    IconButton,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow,
    Typography,
    Menu,
    MenuItem,
    ListItemIcon,
    ListItemText,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { getFileByFileType } from '@/constants/FILES';
import palette from '@/theme/palette';
import Image from 'next/image';
import React, { useState, useMemo, useRef, useCallback, useEffect } from 'react';
import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import { useTranslations } from 'next-intl';
import { DateFilter } from '@/constants/FILTER';
import typography from '@/theme/typography';
import { useDialog } from '@/provider/DialogProvider';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { useUser } from '@/context/UserContext';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import StyledContextMenu from '@/components/common/ContextMenu';
import { useClipboard } from '@/provider/ClipboardProvider';
import { copyTextToClipboard } from '@/utils/clipboard';
import {
    getResourceActionDisabled,
    hasMyDriveResource,
    hasSharedRootResource,
} from '@/utils/resourceUtils';
import useDevice from '@/hooks/useDevice';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { useDrivePreview } from '@/hooks/useDrivePreview';
import checkDevice from '@/utils/etc';
import { ResourceActionButtons } from '@/components/common/resource/ResourceActionButtons';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import { HomeRow } from './HomeRow';
import useStarredToggle from '@/hooks/useStarredToggle';

const Root = styled('div')(({ theme }) => ({
    overflowX: 'hidden',
    table: {
        boxShadow: 'none',
        width: '100%',
        height: '100%',
        overflowX: 'hidden',
        tableLayout: 'fixed',
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
        boxShadow: `0 1px 6px 0 ${palette.divider_12}`,
        borderRight: 'none',
        backgroundColor: 'white',
    },

    th: {
        padding: '0px 8px 0px 12px',
        backgroundColor: 'white',
        borderBottom: 'none',
        [theme.breakpoints.down('md')]: {
            padding: '0px 6px',
        },
    },

    td: {
        height: 44,
        padding: '0px 12px 0px 8px',
        ...typography.body5,
        color: palette.text_80,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        cursor: 'pointer',
        borderBottom: `1px solid ${palette.table_divider_nomal}`,
        [theme.breakpoints.down('md')]: {
            height: 54,
            padding: '8px 6px',
        },
    },

    // hoveredRow state 제거 -> CSS hover로 대체
    // hover 여부에 따라 액션 아이콘 표시
    '.home-row .row-actions': {
        opacity: 0,
        visibility: 'hidden',
        pointerEvents: 'none',
        transition: 'opacity 0.12s ease',
    },

    // useDevice로 isPc 판단 후 루트에 is-touch-device 클래스 제어
    // PC: hover 시 표시
    '&:not(.is-touch-device) .home-row:hover .row-actions': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },
    '&:not(.is-touch-device) .home-row:hover .toggle-checkbox': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },
    '&:not(.is-touch-device) .home-row:hover .toggle-icon': {
        opacity: 0,
        visibility: 'hidden',
    },
    '&:not(.is-touch-device) .home-row:not(.is-checked):hover': {
        background: palette.table_bg_hover,
    },

    // 태블릿/모바일: 선택된 행에 항상 표시
    '&.is-touch-device .home-row.is-checked .row-actions': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },

    // hoveredRow state 제거 -> CSS hover로 대체
    // hover 여부에 따라 checkbox 표시
    /* 기본(= not checked & not hover): icon 보이고 checkbox 숨김 */
    '.home-row .toggle-wrap': {
        position: 'relative',
        width: '36px',
        height: '36px',
    },

    '.home-row .toggle-layer': {
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },

    '.home-row .toggle-checkbox': {
        opacity: 0,
        visibility: 'hidden',
        pointerEvents: 'none',
    },

    '.home-row .toggle-icon': {
        opacity: 1,
        visibility: 'visible',
    },

    /* hover 또는 checked: checkbox 보이고 icon 숨김 */
    '.home-row.is-checked .toggle-checkbox': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },

    '.home-row.is-checked .toggle-icon': {
        opacity: 0,
        visibility: 'hidden',
    },

    '.home-row .toggle-checkbox, .home-row .toggle-icon': {
        transition: 'opacity 0.12s ease',
    },
}));

export default function HomeTable({
    data,
    checkItems,
    setCheckItems,
    drawerOpen,
    setDrawerOpen,
    showLike,
    showShare,
    getData,
}) {
    const t = useTranslations();
    const user = useUser();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const device = checkDevice();

    const [recentSeeMore, setRecentSeeMore] = useState(null);

    const [menuAnchorEl, setMenuAnchorEl] = useState(null);
    const [menuTargetColumn, setMenuTargetColumn] = useState('');
    const [dateFilter, setDateFilter] = useState('lastModified');

    const [hoveredRow, setHoveredRow] = useState(null);
    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const columnAnchorRefs = useRef({});

    // 단축키용
    const [anchorIndex, setAnchorIndex] = useState(null); // 기준점 shift 선택 시 사용
    const [activeIndex, setActiveIndex] = useState(null);
    const rootRef = useRef(null);

    // 클립보드
    const { setClipboard } = useClipboard();

    const isShared = router.asPath.includes('shared');
    /** 공유 버튼 활성화 여부 */
    const { isPc } = useDevice();
    const isSharedRoot = item =>
        (isShared && !router?.query?.pfs) || (!isShared && hasSharedRootResource([item]));
    const isMyDrive = item => hasMyDriveResource([item]);

    // 액션 아이콘 Enabled 처리 조건
    const disablePreview = item => !item?.canView;
    const disableDownload = item => !item?.canDownload;
    const disableShare = item =>
        !item?.canShare ||
        !(isMyDrive(item) || isSharedRoot(item) || (!isSharedRoot(item) && item?.shareSeq));

    // checkItems 빠르게 조회하기 위한 Set
    const checkedSet = useMemo(() => {
        return new Set((checkItems ?? []).map(it => it.resourceSeq));
    }, [checkItems]);

    const hasMultipleSelection = checkItems?.length > 1;

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

    const handleCheckItemList = item => {
        setCheckItems(prev =>
            prev.some(i => i.resourceSeq === item.resourceSeq)
                ? prev.filter(i => i.resourceSeq !== item.resourceSeq)
                : [...prev, item]
        );
    };

    const { singleDownload } = useDriveDownload({ t });

    // 홈은 별 추가 시 아래 '중요문서함' 목록에도 행이 생겨야 하는데(8건 제한/정렬 때문에
    // 프론트가 계산할 수 없음) 목록 구성 자체가 바뀌므로 전체 재조회를 유지한다.
    // 낡아진 checkItems는 페이지의 useSyncedSelection이 새 목록 기준으로 다시 맞춘다.
    const { toggleStarred } = useStarredToggle({ reload: getData });
    const handleStarred = useCallback(item => toggleStarred(item), [toggleStarred]);

    const { handlePreview } = useDrivePreview({ t, confirmRestore: false });

    const handleDeleteDrive = async items => {
        let successCount = 0;
        let failCount = 0;
        let lockedCount = 0;

        openDialog('alert', {
            title: t('common.action.moveToTrash'),
            description: t('Trash.text'),
            onClick: async () => {
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
                    } else if (res?.code === 423) {
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
                    ); // 일부 성공/실패
                } else {
                    if (lockedCount > 0) {
                        showSnackbar(t('lock.locked'), 'error');
                    } else {
                        showSnackbar(t('moveToTrash.fail'), 'error');
                    }
                }

                setCheckItems([]);
                await getData(true);
            },
        });
    };

    const handleRename = async item => {
        openDialog('fileName', {
            item: item,
            setCheckItems,
            reload: getData,
        });
    };

    // recent/일반 공통 플랫 데이터
    const flatData = useMemo(() => {
        return Array.isArray(data) ? data : Object.values(data).flat();
    }, [data]);

    // resourceSeq → index 매핑
    const idIndexMap = useMemo(() => {
        const m = new Map();
        flatData.forEach((it, i) => m.set(it.resourceSeq, i));
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
        [flatData, checkItems, updateSelection]
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
            const existingIds = new Set(safeSelected.map(it => it.resourceSeq));
            for (const it of rangeItems) existingIds.add(it.resourceSeq);

            // Set → 배열로, flatData 순서 유지
            const merged = flatData.filter(it => existingIds.has(it.resourceSeq));

            updateSelection(merged);
            setActiveIndex(idx);
            setAnchorIndex(idx); // Shift 선택 후 기준점을 "마지막 클릭"으로 변경
        },
        [flatData, anchorIndex, checkItems, updateSelection, setActiveIndex, setAnchorIndex]
    );

    // 행 클릭 공통 핸들러 (Shift/Ctrl/Cmd 지원)
    const handleRowClickCombined = useCallback(
        (e, item) => {
            const idx = idIndexMap.get(item.resourceSeq) ?? 0;

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

    // 복사
    const onCopy = useCallback(async () => {
        if (!checkItems || checkItems.length === 0) return;

        const names = checkItems.map(it => it.resourceName).join('\n');
        // 파일명 텍스트를 시스템 클립보드에도 복사
        // 복사 먼저 수행하도록 처리
        const success = await copyTextToClipboard(names, t('shareClipboardPrompt.description'));

        if (success) {
            setClipboard({ mode: 'copy', items: [...checkItems] });
            showSnackbar(t('clipboard.copySuccess', { count: checkItems.length }), 'success');
        } else {
            showSnackbar(t('clipboard.copyFail'), 'error');
        }
    }, [checkItems, setClipboard, showSnackbar, t]);

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
    const handleRootMouseDown = useCallback(e => {
        if (!isTextEditingTarget(e.target)) {
            rootRef.current?.focus?.();
        }
    }, []);

    // 키보드 단축키
    const handleKeyDown = useCallback(
        async e => {
            if (isTextEditingTarget(e.target)) return;

            const withCmd = e.metaKey;
            const withCtrl = e.ctrlKey;
            const withCtrlOrCmd = withCmd || withCtrl;
            const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;
            // 메뉴와 같은 권한 기준
            const disabled = getResourceActionDisabled(checkItems);

            if (withCtrlOrCmd && key === 'a') {
                // 전체 선택
                e.preventDefault();
                updateSelection(flatData);
                return;
            }
            if (withCtrlOrCmd && key === 'c') {
                // 복사
                e.preventDefault();
                if (disabled.disableCopy) return;
                await onCopy();
                return;
            }
            if (withCtrlOrCmd && key === 'v') {
                // 붙여넣기 - 홈화면에서 동작x
                return;
            }

            switch (key) {
                case 'Escape':
                    updateSelection([]);
                    setActiveIndex(null);
                    setAnchorIndex(null);
                    break;
                case 'Delete':
                    if (checkItems.length > 0 && !disabled.disableDelete) {
                        await handleDeleteDrive(checkItems);
                    }
                    break;
                case 'Enter':
                    if (checkItems.length > 0 && !disabled.disablePreview) {
                        handlePreview(checkItems[0], { preferEdit: true });
                    }
                    break;
                case 'F2':
                    if (checkItems.length === 1 && !disabled.disableRename) {
                        handleRename(checkItems[0]);
                    }
                    break;
                case 'ArrowUp': {
                    e.preventDefault();
                    const next = Math.max(0, activeIndex - 1);
                    selectSingleByIndex(next);
                    break;
                }
                case 'ArrowDown': {
                    e.preventDefault();
                    const next = Math.min(flatData.length - 1, activeIndex + 1);
                    selectSingleByIndex(next);
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
            handleDeleteDrive,
            handlePreview,
            selectSingleByIndex,
        ]
    );

    useEffect(() => {
        rootRef.current?.focus?.();
    }, []);

    // 라우트 이동 시에도 자동 포커스
    useEffect(() => {
        rootRef.current?.focus?.();
    }, [router.asPath]);

    return (
        <Root
            ref={rootRef}
            tabIndex={0}
            className={!isPc ? 'is-touch-device' : ''}
            onKeyDown={e => handleKeyDown(e)}
            onMouseDown={handleRootMouseDown}
            style={{ outline: 'none' }}
        >
            <TableContainer sx={{ maxHeight: '100%' }}>
                <Table stickyHeader>
                    <TableBody>
                        {data?.map((item, index) => {
                            const checked = checkedSet.has(item.resourceSeq);

                            return (
                                <HomeRow
                                    key={item.resourceSeq ?? index}
                                    item={item}
                                    index={index}
                                    checked={checked}
                                    drawerOpen={drawerOpen}
                                    showLike={showLike || false}
                                    showShare={showShare || false}
                                    showLocation={false /* 홈은 location 컬럼 없으면 false */}
                                    palette={palette}
                                    getFileByFileType={getFileByFileType}
                                    getLocation={null}
                                    getLocationTooltip={null}
                                    api={api}
                                    Image={Image}
                                    Stack={Stack}
                                    TableRow={TableRow}
                                    TableCell={TableCell}
                                    Typography={Typography}
                                    IconButton={IconButton}
                                    StyledCheckbox={StyledCheckbox}
                                    BaseTooltip={BaseTooltip}
                                    ProfileImage={null /* 홈은 소유자 컬럼 없으면 null */}
                                    ResourceActionButtons={ResourceActionButtons}
                                    disablePreview={disablePreview}
                                    disableDownload={disableDownload}
                                    disableShare={disableShare}
                                    onRowMouseDown={handleRowMouseDown}
                                    onRowClickCombined={handleRowClickCombined}
                                    onPreview={handlePreview}
                                    onToggleCheck={
                                        handleCheckItemList /* 홈은 checkItems 배열 토글 함수 */
                                    }
                                    onContextMenuOpen={(e, row) => {
                                        // 기존 로직 유지: 우클릭 시 단일 선택 맞추고 메뉴 열기
                                        if (!checkItems.includes(row)) {
                                            setCheckItems([row]);
                                        }
                                        handleContextMenu(e, row);
                                    }}
                                    onStar={handleStarred}
                                    onDownload={singleDownload}
                                    openDialog={openDialog}
                                    getData={getData}
                                    hasMultipleSelection={hasMultipleSelection}
                                    t={t}
                                />
                            );
                        })}
                    </TableBody>
                </Table>
                <Menu
                    open={!!menuAnchorEl}
                    anchorEl={columnAnchorRefs.current?.[menuTargetColumn] || null}
                    anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'left',
                    }}
                    transformOrigin={{
                        vertical: 'top',
                        horizontal: 'left',
                    }}
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
                    selected={dateFilter}
                    onClose={() => setMenuAnchorEl(null)}
                >
                    {DateFilter.map(item => (
                        <MenuItem
                            key={item.name}
                            onClick={() => {
                                setDateFilter(item.value);
                                setMenuAnchorEl(null);
                            }}
                        >
                            <ListItemIcon>
                                {item.value === dateFilter && (
                                    <Image
                                        src={'/common/icon-ic-check.svg'}
                                        alt="check"
                                        width={20}
                                        height={20}
                                    />
                                )}
                            </ListItemIcon>

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
                    user={user}
                />
            </TableContainer>
        </Root>
    );
}

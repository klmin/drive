import {
    Box,
    IconButton,
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
import { TableVirtuoso } from 'react-virtuoso';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import Image from 'next/image';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { useDialog } from '@/provider/DialogProvider';
import useOfficeViewer from '@/hooks/useOfficeViewer';
import { buildSortedQuery } from '@/utils/queryString';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import useDevice from '@/hooks/useDevice';
import { ShareRowCells } from './ShareRowCells';
import ShareContextMenu from '@/components/common/ShareContextMenu';
import { useUser } from '@/context/UserContext';

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

    '& thead tr': {
        position: 'sticky',
        top: 0,
        zIndex: 99,
        backgroundColor: 'white',
        boxShadow: `0 3px 5px -2px ${palette.divider_12}`,
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
    '.drive-row .row-actions': {
        opacity: 0,
        visibility: 'hidden',
        pointerEvents: 'none',
        transition: 'opacity 0.12s ease',
    },
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
    '&.is-touch-device .drive-row.is-checked .row-actions': {
        opacity: 1,
        visibility: 'visible',
        pointerEvents: 'auto',
    },

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

    '.th-sortable .sort-icon': {
        opacity: 0,
        transition: 'opacity 0.15s ease-in-out',
    },
    '.th-sortable:hover .sort-icon': { opacity: 1 },
    '.th-sortable.is-sorted .sort-icon': { opacity: 1 },
}));

const VirtuosoTableComponents = {
    Scroller: React.forwardRef((props, ref) => (
        <TableContainer
            {...props}
            ref={ref}
            data-testid="drive-table-container"
            sx={{ overflowX: 'hidden', height: '100%' }}
        />
    )),
    Table: props => (
        <Table
            {...props}
            stickyHeader
            sx={{ width: '100%', height: 'auto !important', minHeight: 'unset !important' }}
        />
    ),
    TableHead: React.forwardRef((props, ref) => <TableHead {...props} ref={ref} />),
    TableBody: React.forwardRef((props, ref) => (
        <TableBody
            {...props}
            ref={ref}
            sx={{ display: 'table-row-group', height: 'auto !important' }}
        />
    )),
    TableRow: rowProps => {
        const index = rowProps['data-index'];
        const ctx = rowProps.context;
        const item = ctx?.data?.[index];

        if (!item) return <TableRow {...rowProps} />;

        const checked = ctx.checkedSetRef.current.has(item.resourceSeq);

        return (
            <TableRow
                {...rowProps}
                className={`drive-row ${checked ? 'is-checked' : ''}`}
                onClick={e => ctx.onRowClick(e, item)}
                onDoubleClick={() => ctx.onPreview(item, { preferEdit: true })}
                onContextMenu={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    ctx.onContextMenuOpen(e, item);
                }}
                sx={{
                    cursor: 'pointer',
                    borderBottom: `1px solid ${palette.table_divider_nomal}`,
                    background: checked ? palette.table_bg_selected : undefined,
                }}
            />
        );
    },
};
VirtuosoTableComponents.Scroller.displayName = 'ShareFolderTableScroller';
VirtuosoTableComponents.TableHead.displayName = 'ShareFolderTableTableHead';
VirtuosoTableComponents.TableBody.displayName = 'ShareFolderTableTableBody';

export default function ShareFolderTable({
    data,
    hasNext,
    cursorSearchCount,
    setCursorSearchCount,
    onFolderClick,
}) {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { isOfficeViewerTarget, openOfficeViewer } = useOfficeViewer();
    const { isPc } = useDevice();
    const user = useUser();

    const virtuosoRef = useRef(null);
    const scrollerElRef = useRef(null);
    const loadingMoreRef = useRef(false);

    const [checkItems, setCheckItems] = useState([]);
    const [hoveredSortField, setHoveredSortField] = useState(null);

    const checkedSet = useMemo(
        () => new Set((checkItems ?? []).map(it => it.resourceSeq)),
        [checkItems]
    );
    const checkedSetRef = useRef(checkedSet);

    useEffect(() => {
        checkedSetRef.current = checkedSet;
    }, [checkedSet]);

    const allCheck = useMemo(
        () => !!data?.length && checkItems.length === data.length,
        [checkItems.length, data?.length]
    );

    const sortBy = router.query.sb ?? 'name';
    const sortOrder = router.query.so ?? 'asc';

    const handleToggleCheck = useCallback(item => {
        setCheckItems(prev => {
            const exists = prev.some(i => i.resourceSeq === item.resourceSeq);
            return exists ? prev.filter(i => i.resourceSeq !== item.resourceSeq) : [...prev, item];
        });
    }, []);

    const handleToggleAllCheck = useCallback(() => {
        setCheckItems(allCheck ? [] : (data ?? []));
    }, [allCheck, data]);

    /** preferEdit(이름 클릭·더블클릭)면 편집 권한 있는 http 오피스는 에디터로 */
    const handlePreview = useCallback(
        (item, { preferEdit = false } = {}) => {
            if (item.resourceType === 'FOLDER') {
                onFolderClick?.(item);
            } else if (isOfficeViewerTarget(item)) {
                // http TFO 오피스는 다이얼로그 대신 새 창
                openOfficeViewer(item, { preferEdit });
            } else {
                openDialog('preview', {
                    previewItem: item,
                    user: user,
                    preferEdit,
                });
            }
        },
        [onFolderClick, openDialog, isOfficeViewerTarget, openOfficeViewer]
    );

    const handleRowClick = useCallback(
        (e, item) => {
            if (e.metaKey || e.ctrlKey || e.shiftKey) {
                handleToggleCheck(item);
                return;
            }
            setCheckItems([item]);
        },
        [handleToggleCheck]
    );

    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const handleCloseMenu = useCallback(
        () => setMenuState(prev => ({ ...prev, visible: false })),
        []
    );

    const onContextMenuOpen = useCallback((e, row) => {
        if (!checkedSetRef.current.has(row.resourceSeq)) setCheckItems([row]);
        setMenuState({ visible: true, top: e.clientY, left: e.clientX, selectedRow: row });
    }, []);

    const { singleDownload } = useDriveDownload({ t });

    const ownerProfileSrcMap = useMemo(() => {
        const m = new Map();
        for (const it of data ?? []) {
            const seq = it?.ownerSeq;
            if (seq && !m.has(seq)) m.set(seq, api.getProfileImage(seq));
        }
        return m;
    }, [data]);

    const getOwnerProfileSrc = useCallback(
        item => ownerProfileSrcMap.get(item?.ownerSeq) || '',
        [ownerProfileSrcMap]
    );

    const getDeleteUserProfileSrc = useCallback(() => '', []);

    const handleSort = useCallback(
        field => {
            const nextOrder = sortBy === field ? (sortOrder === 'asc' ? 'desc' : 'asc') : 'asc';
            const query = buildSortedQuery(router.query, field, nextOrder);
            router.push({ pathname: router.pathname, query }, undefined, { shallow: true });
        },
        [router, sortBy, sortOrder]
    );

    const columns = [
        { field: 'name', headerName: 'data.name', isSort: true, isMobile: true },
        { field: '', headerName: '', align: 'center' },
        { field: 'owner', headerName: 'common.label.owner', spacing: true, align: 'left' },
        {
            field: 'update',
            headerName: 'data.lastModified',
            isSort: true,
            spacing: true,
            align: 'center',
        },
        {
            field: 'size',
            headerName: 'common.label.fileSize',
            isSort: true,
            align: 'left',
            isSize: true,
        },
        { field: 'options', headerName: '', isMobile: true, isOption: true },
    ];

    const fixedHeaderContent = useCallback(
        () => (
            <TableRow>
                <TableCell align="left" sx={{ p: '0px 4px 0px 8px !important' }}>
                    <Stack direction="row" justifyContent="center" alignItems="center">
                        <div className="toggle-wrap">
                            <span className="toggle-layer toggle-checkbox" style={{ opacity: 1 }}>
                                <BaseTooltip title={t('tooltip.selectAll')}>
                                    <StyledCheckbox
                                        checked={allCheck}
                                        onChange={handleToggleAllCheck}
                                    />
                                </BaseTooltip>
                            </span>
                        </div>
                    </Stack>
                </TableCell>

                {columns.map(col => (
                    <TableCell
                        key={col.field}
                        className={
                            col.isSort
                                ? `th-sortable${sortBy === col.field ? ' is-sorted' : ''}`
                                : undefined
                        }
                        align={col.align}
                        sx={{
                            display: {
                                xs: col.isMobile ? 'table-cell' : 'none',
                                md: col.isSize ? 'none' : 'table-cell',
                                lg: 'table-cell',
                            },
                        }}
                    >
                        <Box
                            display="flex"
                            alignItems="center"
                            justifyContent={col.align ?? 'flex-start'}
                        >
                            <Box display="flex" alignItems="center">
                                <Typography
                                    variant="body9"
                                    color="text_60"
                                    sx={{ whiteSpace: 'nowrap' }}
                                >
                                    {col.headerName ? t(col.headerName) : ''}
                                </Typography>
                                {col.isSort && (
                                    <IconButton
                                        className="sort-icon"
                                        size="small"
                                        onClick={() => handleSort(col.field)}
                                        sx={{
                                            opacity:
                                                hoveredSortField === col.field ||
                                                sortBy === col.field
                                                    ? 1
                                                    : 0,
                                            transition: 'opacity 0.15s ease-in-out',
                                        }}
                                    >
                                        {sortBy === col.field ? (
                                            sortOrder === 'desc' ? (
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
                                        )}
                                    </IconButton>
                                )}
                            </Box>
                        </Box>
                    </TableCell>
                ))}
            </TableRow>
        ),
        [
            t,
            allCheck,
            handleToggleAllCheck,
            columns,
            hoveredSortField,
            sortBy,
            sortOrder,
            handleSort,
        ]
    );

    const virtuosoItemContent = useCallback(
        (index, item) => (
            <ShareRowCells
                item={item}
                checked={checkedSetRef.current.has(item.resourceSeq)}
                drawerOpen={false}
                showLocation={false}
                showDeleteUser={false}
                onToggleCheck={handleToggleCheck}
                onContextMenuOpen={onContextMenuOpen}
                onPreview={handlePreview}
                onDownload={singleDownload}
                hasMultipleSelection={checkItems.length >= 2}
                disablePreview={() => false}
                disableDownload={item => !item?.canDownload}
                t={t}
                getOwnerProfileSrc={getOwnerProfileSrc}
                getDeleteUserProfileSrc={getDeleteUserProfileSrc}
            />
        ),
        [
            handleToggleCheck,
            onContextMenuOpen,
            handlePreview,
            singleDownload,
            checkItems.length,
            t,
            getOwnerProfileSrc,
            getDeleteUserProfileSrc,
        ]
    );

    const tryLoadMore = useCallback(() => {
        if (!hasNext || loadingMoreRef.current) return;
        loadingMoreRef.current = true;
        setCursorSearchCount(prev => prev + 1);
    }, [hasNext, setCursorSearchCount]);

    useEffect(() => {
        loadingMoreRef.current = false;
        const raf = requestAnimationFrame(() => {
            if (loadingMoreRef.current) return;
            const el = scrollerElRef.current;
            if (el && el.scrollHeight <= el.clientHeight) tryLoadMore();
        });
        return () => cancelAnimationFrame(raf);
    }, [data]);

    useEffect(() => {
        setCheckItems([]);
    }, [router.asPath]);

    const context = useMemo(
        () => ({
            data,
            checkedSetRef,
            onRowClick: handleRowClick,
            onPreview: handlePreview,
            onContextMenuOpen,
        }),
        [data, handleRowClick, handlePreview, onContextMenuOpen]
    );

    return (
        <Root className={!isPc ? 'is-touch-device' : ''}>
            <ShareContextMenu
                menuState={menuState}
                onClose={handleCloseMenu}
                checkItems={checkItems}
                setCheckItems={setCheckItems}
            />
            <TableVirtuoso
                ref={virtuosoRef}
                scrollerRef={el => {
                    scrollerElRef.current = el;
                }}
                data={data}
                computeItemKey={(_, item) => item.resourceSeq}
                components={VirtuosoTableComponents}
                fixedHeaderContent={fixedHeaderContent}
                itemContent={virtuosoItemContent}
                context={context}
                atBottomStateChange={isAtBottom => {
                    if (isAtBottom) tryLoadMore();
                }}
                increaseViewportBy={{ top: 600, bottom: 600 }}
            />
        </Root>
    );
}

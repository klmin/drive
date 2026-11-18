import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
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
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { TenantRowCells } from './TenantRowCells';
import TenantContextMenu from '../../../../common/TenantContextMenu';

// tableLayout: fixed 에서 컬럼 너비는 width 만 적용됨 (minWidth/maxWidth 브라우저 무시)
// width: 반응형 비율(%) or 고정(px)
const COLUMNS = [
    {
        field: 'name',
        headerKey: 'superAdmin.tenantManagement.table.name',
        align: 'left',
        width: '15%', // 테이블 너비의 15%
    },
    {
        field: 'domain',
        headerKey: 'superAdmin.tenantManagement.table.domain',
        align: 'left',
        width: '25%', // 테이블 너비의 25%
    },
    {
        field: 'tenantType',
        headerKey: 'superAdmin.tenantManagement.table.tenantType',
        align: 'left',
        width: 120,
    },
    {
        field: 'userMode',
        headerKey: 'superAdmin.tenantManagement.table.userMode',
        align: 'left',
        width: 112,
    },
    {
        field: 'status',
        headerKey: 'superAdmin.tenantManagement.table.licenseStatus',
        align: 'left',
        width: 148,
    },
    {
        field: 'detail',
        headerKey: 'superAdmin.tenantManagement.table.detail',
        align: 'left',
        width: 136,
    },
    {
        field: 'office',
        headerKey: 'superAdmin.tenantManagement.table.office',
        align: 'left',
        width: 140,
    },
    {
        field: 'createdAt',
        headerKey: 'superAdmin.tenantManagement.table.createdAt',
        align: 'left',
        width: 160,
    },
    {
        field: 'tenantAdmin',
        headerKey: '',
        align: 'right',
        // width 미지정(auto) → 테이블 여유 공간을 이 컬럼이 모두 흡수해
        // 나머지 fixed 컬럼들이 지정 너비를 정확히 유지함
    },
    {
        field: 'option',
        headerKey: '',
        align: 'center',
        width: 48,
        style: { paddingLeft: 0, paddingRight: '8px' },
    },
];

// 고정 컬럼(px)의 합계
// tenantAdmin은 width 미지정(auto)이므로 합산에서 제외되나,
// 바로가기 버튼이 보여야 할 최소 너비(40px)는 별도로 더함
const TENANTADMIN_MIN_PX = 40;
const FIXED_PX_TOTAL =
    COLUMNS.reduce((sum, col) => (typeof col.width === 'number' ? sum + col.width : sum), 0) +
    TENANTADMIN_MIN_PX;
// 비율 컬럼(%)의 합계 (0~1 사이 소수)
const PERCENT_TOTAL = COLUMNS.reduce((sum, col) => {
    if (typeof col.width === 'string' && col.width.endsWith('%')) {
        return sum + parseFloat(col.width) / 100;
    }
    return sum;
}, 0);
// 테이블 최소 너비:
// 비율 컬럼이 지정된 %를 유지하면서 고정 컬럼이 모두 보이려면,
// 나머지 (1 - PERCENT_TOTAL) 비율이 FIXED_PX_TOTAL 이상이어야 함.
// → TABLE_MIN_WIDTH = FIXED_PX_TOTAL / (1 - PERCENT_TOTAL)
// 이 너비 미만에서는 Root의 overflowX: auto 로 가로 스크롤 발생.
const TABLE_MIN_WIDTH = Math.ceil(FIXED_PX_TOTAL / (1 - PERCENT_TOTAL));

const Root = styled('div')(() => ({
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',
    overflowX: 'auto',

    table: {
        width: '100%',
        minWidth: TABLE_MIN_WIDTH, // 비율 컬럼 % 유지 + 고정 컬럼 모두 표시 가능한 최소 너비
        boxShadow: 'none',
        tableLayout: 'fixed',
    },

    '& thead tr': {
        position: 'sticky',
        top: 0,
        zIndex: 99,
        backgroundColor: palette.white,
        boxShadow: `0px 1px 6px ${palette.btn_border}`,
    },

    th: {
        height: 30,
        padding: '0px 8px',
        backgroundColor: palette.white,
        borderBottom: 'none !important',
        borderTop: `1px solid ${palette.divider_8} !important`,
        borderRight: 'none !important',
        borderLeft: 'none !important',
        boxShadow: 'none',
        fontSize: 12,
        fontWeight: 400,
        lineHeight: '16px',
        color: palette.text_60,
        whiteSpace: 'nowrap',
    },

    td: {
        height: 44,
        fontSize: 13,
        color: palette.text_80,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
    },
}));

/* ── 가상화 컴포넌트 ── */
const VirtuosoTableComponents = {
    Scroller: React.forwardRef((props, ref) => (
        <TableContainer {...props} ref={ref} sx={{ overflowX: 'auto', height: '100%' }} />
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
        const index = rowProps['data-index']; // Virtuoso가 넣어주는 현재 row index
        const ctx = rowProps.context; // TableVirtuoso context 접근
        const item = ctx?.data?.[index]; // 현재 row의 데이터

        return (
            <TableRow
                {...rowProps}
                onContextMenu={e => {
                    if (!item) return;
                    e.preventDefault();
                    e.stopPropagation();
                    ctx.onContextMenuOpen(e, item);
                }}
                sx={{ '&:hover': { background: palette.table_bg_hover } }}
            />
        );
    },
};
VirtuosoTableComponents.Scroller.displayName = 'TenantTableScroller';
VirtuosoTableComponents.TableHead.displayName = 'TenantTableTableHead';
VirtuosoTableComponents.TableBody.displayName = 'TenantTableTableBody';

/* ── 메인 컴포넌트 ── */
export default function TenantTable({
    data = [],
    reload,
    hasNext,
    setCursorSearchCount,
    listPatch,
    hasBaseDomain,
}) {
    const t = useTranslations();
    const virtuosoRef = useRef(null);
    const loadingMoreRef = useRef(false);

    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const handleContextMenu = useCallback((e, row) => {
        e.preventDefault();
        setMenuState({ visible: true, top: e.clientY, left: e.clientX, selectedRow: row });
    }, []);

    const handleMenuClose = useCallback(() => {
        setMenuState(prev => ({ ...prev, visible: false }));
    }, []);

    const onContextMenuOpen = useCallback(
        (e, row) => {
            handleContextMenu(e, row);
        },
        [handleContextMenu]
    );

    const tryLoadMore = useCallback(() => {
        if (!hasNext) return;
        if (loadingMoreRef.current) return;

        loadingMoreRef.current = true;
        setCursorSearchCount(prev => prev + 1);
    }, [hasNext, setCursorSearchCount]);

    const rootRef = useRef(null);

    // atBottomStateChange는 isAtBottom 값이 '바뀔 때만' 호출됨.
    // 수정 등으로 목록이 1페이지로 리셋될 때, 리셋 전후 모두 화면을 못 채우는 상태(isAtBottom === true)면
    // 값이 안 바뀌어 콜백이 발생하지 않고 추가 로드가 누락됨 → data 변경 시마다 직접 채움 여부를 확인
    useEffect(() => {
        const raf = requestAnimationFrame(() => {
            const container = rootRef.current?.querySelector('.MuiTableContainer-root');
            if (!container) return;
            if (hasNext && container.scrollHeight <= container.clientHeight) {
                tryLoadMore();
            }
        });
        return () => cancelAnimationFrame(raf);
    }, [data, hasNext, tryLoadMore]);

    const dataLen = useMemo(() => {
        if (Array.isArray(data)) return data.length;
        return Object.values(data ?? {}).flat().length;
    }, [data]);

    useEffect(() => {
        loadingMoreRef.current = false;
    }, [dataLen]);

    /* 헤더 */
    const fixedHeaderContent = useCallback(
        () => (
            <TableRow>
                {COLUMNS.map(col => (
                    <TableCell
                        key={col.field}
                        align={col.align}
                        sx={{ width: col.width }}
                        style={col.style}
                    >
                        {col.headerKey ? t(col.headerKey) : ''}
                    </TableCell>
                ))}
            </TableRow>
        ),
        [t]
    );

    /* 행 */
    const itemContent = useCallback(
        (index, item) => (
            <TenantRowCells
                item={item}
                onContextMenuOpen={onContextMenuOpen}
                adminLinkDisabled={!hasBaseDomain}
            />
        ),
        [onContextMenuOpen, hasBaseDomain]
    );

    return (
        <Root ref={rootRef}>
            <TableVirtuoso
                ref={virtuosoRef}
                data={data}
                computeItemKey={(_, item) => item.tenantSeq}
                components={VirtuosoTableComponents}
                fixedHeaderContent={fixedHeaderContent}
                itemContent={itemContent}
                context={{ onContextMenuOpen, data }}
                style={{ height: '100%' }}
                // (가상화) 무한스크롤: scroll 이벤트 대신 atBottomStateChange 사용
                atBottomStateChange={isAtBottom => {
                    // 스크롤이 실제 바닥일 때만 호출
                    if (isAtBottom) tryLoadMore();
                }}
                increaseViewportBy={{ top: 600, bottom: 600 }} // overscan 옵션: 스크롤이 부드럽게 보이는 정도로 설정
            />
            {data.length === 0 && (
                <Stack
                    alignItems="center"
                    justifyContent="center"
                    sx={{
                        position: 'absolute',
                        top: '44px', // 헤더 높이만큼 offset
                        left: 0,
                        right: 0,
                        bottom: 0,
                        pointerEvents: 'none',
                    }}
                >
                    <Typography variant="body5" color="text_40">
                        {t('adminApiKeyTable.empty')}
                    </Typography>
                </Stack>
            )}
            <TenantContextMenu
                menuState={menuState}
                onClose={handleMenuClose}
                reload={reload}
                listPatch={listPatch}
                item={menuState.selectedRow}
            />
        </Root>
    );
}

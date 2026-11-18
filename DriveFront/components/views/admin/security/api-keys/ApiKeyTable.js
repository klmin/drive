import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { TableVirtuoso } from 'react-virtuoso';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import ApiKeyContextMenu from '@/components/common/ApiKeyContextMenu';
import EllipsisText from '@/components/common/tooltip/EllipsisText';
import { ApiKeyRowCells } from './ApiKeyRowCells';

// 시안: 헤더 30 + 그 아래로 그림자가 깔릴 여백 6
const HEAD_INNER_HEIGHT = 30;
export const HEAD_HEIGHT = 36;

// Key 이름·용도(가변 컬럼)는 남는 폭을 나눠 갖고, 좁아지면 이 폭까지만 줄어든다.
// 나머지는 고정 폭 — table-layout: fixed라 남는 폭은 가변 컬럼에만 돌아간다.
const FLEX_MIN_WIDTH = 200;

/** 관리자 API Key 관리 — 테넌트 전체 키라 역할·사용자 계정을 함께 보여준다 */
export const ADMIN_API_KEY_COLUMNS = [
    { field: 'keyName', labelKey: 'adminApiKeyTable.keyName', width: 'auto' },
    { field: 'keyPurpose', labelKey: 'adminApiKeyTable.purpose', width: 'auto' },
    { field: 'roleType', labelKey: 'admin.apiKey.table.role', width: 110 },
    { field: 'userAccount', labelKey: 'admin.apiKey.table.userAccount', width: 200 },
    { field: 'scopes', labelKey: 'admin.apiKey.table.scope', width: 110 },
    { field: 'status', labelKey: 'adminApiKeyTable.status', width: 120 },
    { field: 'registerDate', labelKey: 'adminApiKeyTable.createdAt', width: 120 },
    { field: 'lastUsedDate', labelKey: 'adminApiKeyTable.lastUsedAt', width: 120 },
    { field: 'expireDate', labelKey: 'admin.apiKey.table.expireDate', width: 120 },
    { field: 'maskedApiKey', labelKey: 'adminApiKeyTable.key', width: 200 },
    { field: 'actions', labelKey: '', width: 32 },
];

/** 슈퍼관리자 — 테넌트 무관 전체 키라 역할 앞에 테넌트명을 둔다 */
export const SUPER_ADMIN_API_KEY_COLUMNS = ADMIN_API_KEY_COLUMNS.flatMap(col =>
    col.field === 'roleType'
        ? [
              {
                  field: 'tenantName',
                  labelKey: 'superAdmin.tenantManagement.table.name',
                  width: 140,
              },
              col,
          ]
        : [col]
);

/** 사용자 설정의 본인 발급 키 — 본인 키만 보이므로 역할·사용자 계정 컬럼이 없다 */
export const MY_API_KEY_COLUMNS = ADMIN_API_KEY_COLUMNS.filter(
    col => col.field !== 'roleType' && col.field !== 'userAccount'
);

// 고정 폭 합 + 가변 컬럼 최소 폭 — 이보다 좁아지면 테이블이 가로 스크롤된다
const tableMinWidth = columns =>
    columns.reduce((sum, col) => sum + (col.width === 'auto' ? FLEX_MIN_WIDTH : col.width), 0);

const Root = styled('div')(() => ({
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',

    table: {
        boxShadow: 'none',
    },

    // 고정 컬럼은 제 배경(흰색)이 있어 행 hover 색이 가려진다 — 같은 색을 위에 한 겹 덧칠한다
    '& tbody tr:hover td:first-of-type': {
        backgroundImage: `linear-gradient(${palette.table_bg_hover}, ${palette.table_bg_hover})`,
    },

    // 가로로 밀려 있을 때만 고정 컬럼 오른쪽에 경계를 준다
    '&.scrolled-x': {
        'th:first-of-type, td:first-of-type': {
            boxShadow: `2px 0 4px ${palette.divider_8}`,
            // 고정 컬럼 오른쪽만 그림자 표시
            clipPath: 'inset(0 -6px 0 0)',
        },
    },
}));

const headCellSx = {
    height: HEAD_HEIGHT,
    p: 0,
    border: 'none',
    verticalAlign: 'top',
    // 스크롤된 행이 그림자 여백으로 비치지 않게 셀 전체를 불투명하게 둔다
    backgroundColor: palette.white,
};

// 첫 컬럼(Key 이름)은 가로 스크롤에도 제자리에 둔다. 헤더 칸은 세로·가로 양쪽으로
// 고정돼 다른 헤더 칸(MUI stickyHeader가 z-index 2)보다 위에 있어야 한다.
const stickyHeadCellSx = {
    ...headCellSx,
    position: 'sticky',
    left: 0,
    zIndex: 3,
};

const headLabelSx = {
    display: 'flex',
    alignItems: 'center',
    height: HEAD_INNER_HEIGHT,
    px: 2,
    backgroundColor: palette.white,
    borderTop: `1px solid ${palette.divider_8}`,
    boxShadow: `0px 1px 6px ${palette.divider_12}`,
    // 그림자 좌우 번짐 차단 (위 -1px는 borderTop 보존용)
    clipPath: 'inset(-1px 0 -6px 0)',
};

/* ── 가상화 컴포넌트 ── */
const VirtuosoTableComponents = {
    Scroller: React.forwardRef((props, ref) => (
        <TableContainer {...props} ref={ref} sx={{ overflow: 'auto', height: '100%' }} />
    )),
    Table: ({ context, ...props }) => (
        <Table
            {...props}
            stickyHeader
            sx={{
                width: '100%',
                minWidth: context?.tableMinWidth,
                tableLayout: 'fixed',
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
            sx={{ display: 'table-row-group', height: 'auto !important' }}
        />
    )),
    TableRow: rowProps => {
        const index = rowProps['data-index']; // Virtuoso가 넣어주는 현재 row index
        const ctx = rowProps.context;
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
VirtuosoTableComponents.Scroller.displayName = 'ApiKeyTableScroller';
VirtuosoTableComponents.TableHead.displayName = 'ApiKeyTableTableHead';
VirtuosoTableComponents.TableBody.displayName = 'ApiKeyTableTableBody';

/* ── 메인 컴포넌트 ── */
export default function ApiKeyTable({
    data = [],
    reload,
    hasNext,
    setCursorSearchCount,
    listResetToken = 0,
    listPatch,
    columns = ADMIN_API_KEY_COLUMNS,
    basePath,
}) {
    const t = useTranslations();
    const virtuosoRef = useRef(null);
    const loadingMoreRef = useRef(false);
    // TableVirtuoso의 스크롤 컨테이너 DOM — 가로로 밀렸는지 보려고 직접 참조한다
    const [scrollerEl, setScrollerEl] = useState(null);
    const [scrolledX, setScrolledX] = useState(false);
    const minWidth = useMemo(() => tableMinWidth(columns), [columns]);

    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const onContextMenuOpen = useCallback((e, row) => {
        e.preventDefault();
        setMenuState({ visible: true, top: e.clientY, left: e.clientX, selectedRow: row });
    }, []);

    const handleMenuClose = useCallback(() => {
        setMenuState(prev => ({ ...prev, visible: false }));
    }, []);

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

    // 목록을 1페이지로 다시 읽으면 스크롤을 맨 위로 되돌린다
    useEffect(() => {
        if (!listResetToken) return;

        loadingMoreRef.current = false;
        virtuosoRef.current?.scrollToIndex({ index: 0 });
    }, [listResetToken]);

    useEffect(() => {
        if (!scrollerEl) return;

        const handleScroll = () => setScrolledX(scrollerEl.scrollLeft > 0);

        handleScroll();
        scrollerEl.addEventListener('scroll', handleScroll, { passive: true });
        return () => scrollerEl.removeEventListener('scroll', handleScroll);
    }, [scrollerEl]);

    /* 헤더 */
    const fixedHeaderContent = useCallback(
        () => (
            <TableRow>
                {columns.map((col, index) => (
                    <TableCell
                        key={col.field}
                        sx={{ ...(index === 0 ? stickyHeadCellSx : headCellSx), width: col.width }}
                    >
                        <Box sx={headLabelSx}>
                            <EllipsisText variant="body9" color="text_60">
                                {col.labelKey ? t(col.labelKey) : ''}
                            </EllipsisText>
                        </Box>
                    </TableCell>
                ))}
            </TableRow>
        ),
        [t, columns]
    );

    /* 행 */
    const itemContent = useCallback(
        (index, item) => (
            <ApiKeyRowCells item={item} columns={columns} onContextMenuOpen={onContextMenuOpen} />
        ),
        [columns, onContextMenuOpen]
    );

    return (
        <Root className={scrolledX ? 'scrolled-x' : undefined}>
            <TableVirtuoso
                ref={virtuosoRef}
                scrollerRef={setScrollerEl}
                data={data}
                computeItemKey={(_, item) => item.apiKeySeq}
                components={VirtuosoTableComponents}
                fixedHeaderContent={fixedHeaderContent}
                itemContent={itemContent}
                context={{ onContextMenuOpen, data, tableMinWidth: minWidth }}
                style={{ height: '100%' }}
                // (가상화) 무한스크롤: scroll 이벤트 대신 atBottomStateChange 사용
                atBottomStateChange={isAtBottom => {
                    // 바닥에 닿는 순간만 통과 (이탈 시에도 호출됨)
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
                        top: `${HEAD_HEIGHT}px`, // 헤더 높이만큼 offset
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
            <ApiKeyContextMenu
                menuState={menuState}
                onClose={handleMenuClose}
                reload={reload}
                listPatch={listPatch}
                item={menuState.selectedRow}
                basePath={basePath}
            />
        </Root>
    );
}

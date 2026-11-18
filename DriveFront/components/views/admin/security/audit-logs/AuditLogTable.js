/** 감사 로그 목록 테이블 + 하단 페이지네이션. */
import React, { useCallback, useRef, useState } from 'react';
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
import Image from 'next/image';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import auditLogTokens from './auditLogTokens';
import {
    badgeColorOf,
    formatEventLabel,
    formatOccurredAt,
    formatOutcome,
    formatResourceName,
    formatResourceSub,
    formatTarget,
} from './auditLogFormat';

// 타겟만 가변 컬럼(최소 110px), 나머지는 고정 폭
const COLUMNS = [
    { field: 'actor', labelKey: 'admin.auditLog.table.user', width: 140 },
    { field: 'event', labelKey: 'admin.auditLog.table.event', width: 410 },
    { field: 'outcome', labelKey: 'admin.auditLog.filter.result.label', width: 92 },
    { field: 'resource', labelKey: 'admin.auditLog.table.resource', width: 160 },
    { field: 'target', labelKey: 'admin.auditLog.table.target', width: 'auto' },
    { field: 'channel', labelKey: 'admin.auditLog.table.channel', width: 100 },
    { field: 'clientIp', labelKey: 'admin.auditLog.table.ip', width: 145 },
    { field: 'occurredAt', labelKey: 'admin.auditLog.table.timestamp', width: 160 },
];

const TARGET_MIN_WIDTH = 110;

// 카드는 남는 높이를 채우되 한 페이지 분량까지만 늘어난다. 더 좁으면 목록 안에서 스크롤한다.
const HEAD_HEIGHT = 30; // 관리자 사용자 목록(UserTable)과 같은 헤더 높이
const LOADING_HEIGHT = 200;
const ROW_HEIGHT = 68;
const FOOTER_HEIGHT = 48; // 시안: 상하 패딩 8 + 버튼 32

const CARD_BORDER = 0;
const SCROLLBAR_SIZE = 6;
// 가로 스크롤바가 먹는 높이만큼 여유를 둔다. 미리 빼두지 않으면 가로 스크롤이 생길 때
// 높이가 그만큼 모자라 세로 스크롤까지 따라 생긴다. 배율 반올림 오차도 이 여유로 흡수된다.
const CARD_HEIGHT_SLACK = SCROLLBAR_SIZE;

/** 행 n개를 다 보여주는 카드 높이 — 헤더·푸터·위아래 테두리 포함 */
const cardHeightFor = rows =>
    HEAD_HEIGHT + ROW_HEIGHT * rows + FOOTER_HEIGHT + CARD_BORDER * 2 + CARD_HEIGHT_SLACK;

// 고정 폭 합 + 타겟 최소 폭 — 이보다 좁아지면 테이블이 가로 스크롤된다
const TABLE_MIN_WIDTH =
    COLUMNS.reduce((sum, col) => sum + (col.width === 'auto' ? 0 : col.width), 0) +
    TARGET_MIN_WIDTH;

// 페이지 번호는 5개씩 블록(1~5 → 6~10)으로 끊고 좌우 화살표는 블록 단위로 이동한다
const PAGE_BLOCK = 5;

/** pageIndex가 속한 블록의 첫 페이지 번호 */
const blockStart = pageIndex => Math.floor((pageIndex - 1) / PAGE_BLOCK) * PAGE_BLOCK + 1;

const pageNumbers = (pageIndex, totalPages) => {
    const start = blockStart(pageIndex);
    const end = Math.min(totalPages, start + PAGE_BLOCK - 1);
    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
};

const headCellSx = {
    // 관리자 사용자 목록(UserTable)과 같은 헤더 박스 모델 — 높이 30 / 본문과 같은 좌우 20 패딩
    height: HEAD_HEIGHT,
    padding: '0px 20px',
    verticalAlign: 'middle',
    backgroundColor: palette.white,
    whiteSpace: 'nowrap',
    // 관리자 사용자 목록과 같은 헤더 상단 구분선
    borderTop: `1px solid ${palette.divider_8}`,
    // 구분선 대신 행 아래로 떨어지는 그림자로 본문과 나눈다
    borderBottom: 'none',
    boxShadow: `0 3px 5px -2px ${palette.divider_12}`,
};

const bodyCellSx = {
    height: ROW_HEIGHT,
    py: 3,
    px: 5,
    // 지정 안 하면 MUI 기본 구분선(#e0e0e0)이 그려져 시안보다 진해진다
    borderBottom: `1px solid ${auditLogTokens.dividerFaint}`,
    verticalAlign: 'middle',
};

// 커스텀 variant는 Typography가 span으로 렌더돼 ellipsis가 안 먹는다 — 블록으로 고정한다
const ellipsisSx = {
    display: 'block',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
};

// 감사 로그 화면 전용 툴팁 시안 — 전역 MuiTooltip(반투명 배경)과 달리 불투명 배경 + 그림자를 쓴다
const auditTooltipSx = {
    backgroundColor: `${auditLogTokens.buttonSecondaryBg} !important`,
    opacity: '1 !important',
    boxShadow: '0px 2px 4px rgba(0, 0, 0, 0.15)',
    fontWeight: 400,
};

/**
 * 말줄임된 셀 텍스트. 실제로 잘린 경우에만 툴팁으로 전체 값을 보여준다.
 * 판정은 hover 시점에 한다 — 마운트 직후엔 열 폭·웹폰트가 안 잡혀 잘못 측정된다.
 */
function EllipsisText({ text, variant, color }) {
    const textRef = useRef(null);
    const [truncated, setTruncated] = useState(false);

    const measure = useCallback(() => {
        const el = textRef.current;
        if (!el) return;
        setTruncated(el.scrollWidth > el.clientWidth);
    }, []);

    return (
        <BaseTooltip title={truncated ? text : ''} slotProps={{ tooltip: { sx: auditTooltipSx } }}>
            <Typography
                ref={textRef}
                variant={variant}
                onMouseEnter={measure}
                sx={{ color, ...ellipsisSx }}
            >
                {text}
            </Typography>
        </BaseTooltip>
    );
}

/** 폭이 값의 최대 길이를 넘어 말줄임이 없는 셀 — 툴팁·측정 없이 그대로 렌더한다 */
function PlainText({ text, variant, color }) {
    return (
        <Typography variant={variant} sx={{ color, whiteSpace: 'nowrap' }}>
            {text}
        </Typography>
    );
}

/** 이벤트 배지 */
function EventBadge({ item, t }) {
    const color = badgeColorOf(item.eventCategory);
    return (
        <Box
            sx={{
                display: 'inline-flex',
                alignItems: 'center',
                height: 22,
                px: '10px',
                borderRadius: '100px',
                backgroundColor: color.bg,
                border: `1px solid ${color.border}`,
                maxWidth: '100%',
            }}
        >
            <PlainText text={formatEventLabel(item, t)} variant="body9_10" color={color.text} />
        </Box>
    );
}

/**
 * 이름/경로처럼 2줄로 쌓이는 셀.
 * 툴팁은 셀 단위로 하나만 띄우고, 두 줄 중 하나라도 잘리면 양쪽 전체 값을 함께 보여준다.
 */
function StackedCell({ primary, secondary }) {
    const primaryRef = useRef(null);
    const secondaryRef = useRef(null);
    const [truncated, setTruncated] = useState(false);

    const measure = useCallback(() => {
        const overflowed = el => !!el && el.scrollWidth > el.clientWidth;
        setTruncated(overflowed(primaryRef.current) || overflowed(secondaryRef.current));
    }, []);

    const primaryText = primary || '-';

    return (
        <BaseTooltip
            title={
                truncated ? (
                    <>
                        {primaryText}
                        {secondary && (
                            <>
                                <br />
                                {secondary}
                            </>
                        )}
                    </>
                ) : (
                    ''
                )
            }
            slotProps={{ tooltip: { sx: auditTooltipSx } }}
        >
            <Stack gap={1} onMouseEnter={measure} sx={{ minWidth: 0 }}>
                <Typography
                    ref={primaryRef}
                    variant="body7_tight"
                    sx={{ color: auditLogTokens.textSecondary, ...ellipsisSx }}
                >
                    {primaryText}
                </Typography>
                {secondary && (
                    <Typography
                        ref={secondaryRef}
                        variant="caption"
                        sx={{ color: auditLogTokens.textDisabled, ...ellipsisSx }}
                    >
                        {secondary}
                    </Typography>
                )}
            </Stack>
        </BaseTooltip>
    );
}

function Pagination({ pageIndex, totalPages, onMovePage, disabled }) {
    const buttonSx = active => ({
        width: 32,
        height: 32,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '4px',
        borderRadius: '6px',
        border: 'none',
        cursor: disabled ? 'default' : 'pointer',
        backgroundColor: active ? auditLogTokens.badge.waiting.bg : 'transparent',
        color: active ? auditLogTokens.badge.waiting.text : auditLogTokens.textHint,
        '&:hover': {
            backgroundColor: active ? auditLogTokens.badge.waiting.bg : palette.btn_white_bg_hover,
        },
        '&:disabled': { opacity: 0.4, cursor: 'default' },
    });

    const start = blockStart(pageIndex);
    const prevBlockPage = start - PAGE_BLOCK;
    const nextBlockPage = start + PAGE_BLOCK;

    return (
        <Stack direction="row" alignItems="center" gap="1px">
            <Box
                component="button"
                type="button"
                onClick={() => onMovePage(prevBlockPage)}
                disabled={disabled || prevBlockPage < 1}
                sx={buttonSx(false)}
            >
                <Image src="/admin/common/icon-ic-arrow-back.svg" alt="" width={20} height={20} />
            </Box>
            {pageNumbers(pageIndex, totalPages).map(page => (
                <Box
                    key={page}
                    component="button"
                    type="button"
                    onClick={() => onMovePage(page)}
                    disabled={disabled}
                    sx={buttonSx(page === pageIndex)}
                >
                    <Typography variant={page === pageIndex ? 'body6_tight' : 'body7_tight'}>
                        {page}
                    </Typography>
                </Box>
            ))}
            <Box
                component="button"
                type="button"
                onClick={() => onMovePage(nextBlockPage)}
                disabled={disabled || nextBlockPage > totalPages}
                sx={buttonSx(false)}
            >
                <Image
                    src="/admin/common/icon-ic-arrow-forward.svg"
                    alt=""
                    width={20}
                    height={20}
                />
            </Box>
        </Stack>
    );
}

export default function AuditLogTable({
    items,
    pageIndex,
    pageSize,
    totalCount,
    loading,
    onMovePage,
}) {
    const t = useTranslations();

    const totalPages = totalCount == null ? null : Math.max(1, Math.ceil(totalCount / pageSize));
    const cardHeight = cardHeightFor(pageSize);

    return (
        <Box
            sx={{
                width: '100%',
                // flex 자식 기본 min-width:auto면 내용 폭만큼 버텨 페이지가 가로로 밀린다
                minWidth: 0,
                // 디자인 가이드: 남는 높이를 채우되 한 페이지(헤더 + pageSize행)가 상한이다.
                // 화면이 그보다 낮으면 카드가 줄고 목록만 안에서 세로 스크롤한다.
                // 결과가 pageSize보다 적으면 남는 자리는 빈 여백으로 두고 푸터는 맨 아래.
                flex: 1,
                minHeight: 0,
                maxHeight: cardHeight,
                display: 'flex',
                flexDirection: 'column',
                border: 'none',
                boxShadow: auditLogTokens.buttonShadow,
                backgroundColor: palette.white,
                overflow: 'hidden',
                position: 'relative',
            }}
        >
            <TableContainer
                sx={{
                    flex: 1,
                    minHeight: 0,
                    overflow: 'auto',
                    // 전역 스크롤바는 트랙에 좌우 4px 패딩이 있어 가장자리에서 떠 보인다.
                    // 목록 안에서는 패딩 없이 6px로 가장자리에 붙인다.
                    '&::-webkit-scrollbar': { width: SCROLLBAR_SIZE, height: SCROLLBAR_SIZE },
                    '&::-webkit-scrollbar:horizontal': { height: SCROLLBAR_SIZE, padding: 0 },
                    // 스크롤바 레인은 부모 배경을 가려 스티키 헤더 옆이 흰색으로 뚫려 보인다.
                    // 레인에도 헤더 배경·구분선을 같은 높이로 그려 이어지게 한다.
                    '&::-webkit-scrollbar:vertical': {
                        width: SCROLLBAR_SIZE,
                        padding: 0,
                        backgroundImage: `linear-gradient(to bottom, ${palette.white} 0 ${HEAD_HEIGHT}px, transparent ${HEAD_HEIGHT}px)`,
                    },
                }}
            >
                <Table stickyHeader sx={{ minWidth: TABLE_MIN_WIDTH, tableLayout: 'fixed' }}>
                    <TableHead>
                        <TableRow>
                            {COLUMNS.map(col => (
                                <TableCell key={col.field} sx={{ ...headCellSx, width: col.width }}>
                                    <Box
                                        display="flex"
                                        alignItems="center"
                                        justifyContent="flex-start"
                                    >
                                        <Typography
                                            variant="body9"
                                            color="text_60"
                                            sx={{ whiteSpace: 'nowrap' }}
                                        >
                                            {t(col.labelKey)}
                                        </Typography>
                                    </Box>
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody
                        // 마지막 행 구분선은 푸터 위 구분선과 맞닿아 2px로 진해진다
                        sx={{ '& tr:last-of-type td': { borderBottom: 'none' } }}
                    >
                        {/* 결과가 없으면 안내 문구 없이 빈 목록으로 둔다 */}
                        {!items?.length
                            ? loading && (
                                  <TableRow>
                                      {/* 자리만 잡는다 — 문구는 카드 기준 오버레이(아래) */}
                                      <TableCell
                                          colSpan={COLUMNS.length}
                                          sx={{ ...bodyCellSx, height: LOADING_HEIGHT }}
                                      />
                                  </TableRow>
                              )
                            : items.map((item, index) => {
                                  const target = formatTarget(item, t);
                                  return (
                                      <TableRow key={item.eventId ?? index}>
                                          <TableCell sx={bodyCellSx}>
                                              <StackedCell
                                                  primary={item.actorName}
                                                  secondary={item.actorEmail}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <EventBadge item={item} t={t} />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <PlainText
                                                  text={formatOutcome(item, t)}
                                                  variant="body7_tight"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <StackedCell
                                                  primary={formatResourceName(item, t)}
                                                  secondary={formatResourceSub(item, t)}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <EllipsisText
                                                  text={target || '-'}
                                                  variant="body5"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <PlainText
                                                  text={item.channel || '-'}
                                                  variant="body7_tight"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <PlainText
                                                  text={item.clientIp || '-'}
                                                  variant="body7_tight"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <PlainText
                                                  text={formatOccurredAt(item.occurredAt)}
                                                  variant="body7_tight"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                      </TableRow>
                                  );
                              })}
                    </TableBody>
                </Table>
            </TableContainer>

            {!items?.length && loading && (
                <Stack
                    alignItems="center"
                    justifyContent="center"
                    sx={{
                        position: 'absolute',
                        // 헤더 아래 ~ 푸터 위 목록 영역의 정중앙
                        top: HEAD_HEIGHT,
                        bottom: FOOTER_HEIGHT,
                        left: 0,
                        right: 0,
                        pointerEvents: 'none',
                    }}
                >
                    {/* 관리자 사용자 목록과 같은 로딩 표시 */}
                    <Typography variant="body1" color="text_60">
                        Loading...
                    </Typography>
                </Stack>
            )}

            <Stack
                direction="row"
                alignItems="center"
                justifyContent="center"
                gap="12px"
                sx={{
                    // 시안: 상하 패딩 8 + 버튼 32 = 48. 위아래 모두 선으로 구분한다.
                    height: FOOTER_HEIGHT,
                    flexShrink: 0,
                    pl: '8px',
                    pr: '12px',
                    backgroundColor: palette.white,
                    borderTop: `1px solid ${auditLogTokens.footerBorder}`,
                    borderBottom: `1px solid ${auditLogTokens.footerBorder}`,
                    overflow: 'hidden',
                }}
            >
                {totalPages != null && (
                    <Pagination
                        pageIndex={pageIndex}
                        totalPages={totalPages}
                        onMovePage={onMovePage}
                        disabled={loading}
                    />
                )}
            </Stack>
        </Box>
    );
}

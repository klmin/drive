/** 감사 로그 목록 테이블 + 하단 페이지네이션. */
import React from 'react';
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
import KeyboardArrowLeftIcon from '@mui/icons-material/KeyboardArrowLeft';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import auditLogTokens from './auditLogTokens';
import AuditLogEllipsisText from './AuditLogEllipsisText';
import {
    badgeColorOf,
    formatEventLabel,
    formatOccurredAt,
    formatResourceName,
    formatResourceSub,
    formatTarget,
} from './auditLogFormat';

// 타겟만 가변 컬럼(최소 260px), 나머지는 고정 폭
const COLUMNS = [
    { field: 'actor', labelKey: 'admin.auditLog.table.user', width: 240 },
    { field: 'event', labelKey: 'admin.auditLog.table.event', width: 210 },
    { field: 'resource', labelKey: 'admin.auditLog.table.resource', width: 240 },
    { field: 'target', labelKey: 'admin.auditLog.table.target', width: 'auto' },
    { field: 'channel', labelKey: 'admin.auditLog.table.channel', width: 100 },
    { field: 'clientIp', labelKey: 'admin.auditLog.table.ip', width: 110 },
    { field: 'occurredAt', labelKey: 'admin.auditLog.table.timestamp', width: 150 },
];

const TARGET_MIN_WIDTH = 260;

// 카드는 남는 높이를 채우되 한 페이지 분량까지만 늘어난다. 더 좁으면 목록 안에서 스크롤한다.
const HEAD_HEIGHT = 37; // 시안: 상하 패딩 10 + 줄높이 16 + 구분선 1
const ROW_HEIGHT = 68;
const FOOTER_HEIGHT = 48; // 시안: 상하 패딩 8 + 버튼 32

const CARD_BORDER = 1;
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
    // 시안 박스 모델 그대로 — 패딩 10 / 줄높이 16 / 구분선 1.
    // 남는 1px(구분선 자리)이 위아래로 나뉘어 글자가 반 픽셀 밀리지 않게 위 정렬로 둔다.
    // height까지 못박아 배율에 따라 패딩·줄높이가 따로 반올림돼도 높이가 안 흔들린다.
    height: HEAD_HEIGHT,
    py: '10px',
    px: 5,
    verticalAlign: 'top',
    backgroundColor: auditLogTokens.backgroundPaper,
    whiteSpace: 'nowrap',
    // 스티키 헤더 셀은 border·box-shadow가 화면에 안 그려져 1px 요소로 직접 깐다
    borderBottom: 'none',
    '&::after': {
        content: '""',
        position: 'absolute',
        left: 0,
        right: 0,
        bottom: 0,
        height: '1px',
        backgroundColor: auditLogTokens.dividerSubtle,
    },
};

const bodyCellSx = {
    height: ROW_HEIGHT,
    py: 3,
    px: 5,
    // 지정 안 하면 MUI 기본 구분선(#e0e0e0)이 그려져 시안보다 진해진다
    borderBottom: `1px solid ${auditLogTokens.dividerFaint}`,
    verticalAlign: 'middle',
};

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
            <AuditLogEllipsisText
                text={formatEventLabel(item, t)}
                variant="body9_10"
                color={color.text}
            />
        </Box>
    );
}

/** 이름/경로처럼 2줄로 쌓이는 셀 */
function StackedCell({ primary, secondary }) {
    return (
        <Stack gap={1} sx={{ minWidth: 0 }}>
            <AuditLogEllipsisText
                text={primary || '-'}
                variant="body7_tight"
                color={auditLogTokens.textSecondary}
            />
            {secondary && (
                <AuditLogEllipsisText
                    text={secondary}
                    variant="caption"
                    color={auditLogTokens.textDisabled}
                />
            )}
        </Stack>
    );
}

function Pagination({ pageIndex, totalPages, onMovePage, disabled }) {
    const buttonSx = active => ({
        width: 32,
        height: 32,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
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
                <KeyboardArrowLeftIcon sx={{ fontSize: 20 }} />
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
                <KeyboardArrowRightIcon sx={{ fontSize: 20 }} />
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
                // 디자인 가이드: 남는 높이를 채우되 한 페이지(헤더 + pageSize행)가 상한이고,
                // 화면이 그보다 낮아도 카드를 줄이지 않는다 — 그때는 페이지 전체가 스크롤된다.
                // 결과가 pageSize보다 적으면 남는 자리는 빈 여백으로 두고 푸터는 맨 아래.
                flex: 1,
                minHeight: cardHeight,
                maxHeight: cardHeight,
                display: 'flex',
                flexDirection: 'column',
                borderRadius: '12px',
                // inset 그림자는 자식보다 아래에 깔려 불투명한 헤더 셀이 덮어버린다. 실제 테두리로 둔다.
                border: `${CARD_BORDER}px solid ${auditLogTokens.dividerSubtle}`,
                boxShadow: auditLogTokens.buttonShadow,
                backgroundColor: palette.white,
                overflow: 'hidden',
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
                        backgroundImage: `linear-gradient(to bottom, ${auditLogTokens.backgroundPaper} 0 ${HEAD_HEIGHT - 1}px, ${auditLogTokens.dividerSubtle} ${HEAD_HEIGHT - 1}px ${HEAD_HEIGHT}px, transparent ${HEAD_HEIGHT}px)`,
                    },
                }}
            >
                <Table stickyHeader sx={{ minWidth: TABLE_MIN_WIDTH, tableLayout: 'fixed' }}>
                    <TableHead>
                        <TableRow>
                            {COLUMNS.map(col => (
                                <TableCell key={col.field} sx={{ ...headCellSx, width: col.width }}>
                                    <Typography
                                        variant="body9_10"
                                        sx={{
                                            // 블록이라야 셀 strut과 베이스라인이 안 겹쳐 줄 상자가 16px로 잡힌다
                                            display: 'block',
                                            color: auditLogTokens.textDisabled,
                                        }}
                                    >
                                        {t(col.labelKey)}
                                    </Typography>
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
                                      <TableCell
                                          colSpan={COLUMNS.length}
                                          sx={{ ...bodyCellSx, height: 200 }}
                                      >
                                          <Stack alignItems="center">
                                              {/* 관리자 사용자 목록과 같은 로딩 표시 */}
                                              <Typography variant="body1" color="text_60">
                                                  Loading...
                                              </Typography>
                                          </Stack>
                                      </TableCell>
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
                                              <StackedCell
                                                  primary={formatResourceName(item, t)}
                                                  secondary={formatResourceSub(item, t)}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <AuditLogEllipsisText
                                                  text={target || '-'}
                                                  variant="body5"
                                                  color={auditLogTokens.textSecondary}
                                                  lines={2}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <AuditLogEllipsisText
                                                  text={item.channel || '-'}
                                                  variant="body7_tight"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <AuditLogEllipsisText
                                                  text={item.clientIp || '-'}
                                                  variant="body7_tight"
                                                  color={auditLogTokens.textSecondary}
                                              />
                                          </TableCell>
                                          <TableCell sx={bodyCellSx}>
                                              <AuditLogEllipsisText
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

            <Stack
                direction="row"
                alignItems="center"
                justifyContent="flex-end"
                sx={{
                    // 시안: 상하 패딩 8 + 버튼 32 = 48. border를 실제 테두리로 쌓으면
                    // 그 1px만큼 패딩이 깎여 위아래가 비대칭으로 좁아진다 — 헤더처럼
                    // 구분선은 레이아웃에 안 잡히는 절대배치 오버레이로 그린다.
                    position: 'relative',
                    height: FOOTER_HEIGHT,
                    flexShrink: 0,
                    pt: 2,
                    pb: 2,
                    px: 5,
                    '&::before': {
                        content: '""',
                        position: 'absolute',
                        left: 0,
                        right: 0,
                        top: 0,
                        height: '1px',
                        backgroundColor: auditLogTokens.dividerSubtle,
                    },
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

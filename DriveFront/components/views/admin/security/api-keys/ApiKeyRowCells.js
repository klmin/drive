import React from 'react';
import Image from 'next/image';
import { Box, IconButton, Stack, TableCell } from '@mui/material';
import { useTranslations } from 'next-intl';
import dayjs from 'dayjs';
import palette from '@/theme/palette';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import EllipsisText from '@/components/common/tooltip/EllipsisText';
import MaskedValue from '@/components/common/MaskedValue';
import { isMaskedValue } from '@/utils/masking';

export const ROW_HEIGHT = 68;

/** 상태 배지 색 — 시안 값. palette의 라이선스 배지와 값이 겹쳐도 용도가 달라 재사용하지 않았다. */
const STATUS_BADGE = {
    active: { bg: '#EDFAF4', border: '#C8F0E0', text: '#10A870' },
    inactive: { bg: '#FFFFFF', border: '#DEE1E8', text: '#747C8B' },
    expired: { bg: '#EFF1F5', border: '#DCDFE5', text: '#727B8C' },
};

const ROLE_LABEL_KEY = {
    SUPER_ADMIN: 'common.label.superAdmin',
    ADMIN: 'common.label.admin',
    USER: 'common.label.user',
};

const cellSx = {
    height: ROW_HEIGHT,
    py: 0,
    pl: 2,
    pr: 8,
    borderBottom: `1px solid ${palette.table_divider_nomal}`,
    verticalAlign: 'middle',
};

// 첫 컬럼(Key 이름)은 가로 스크롤에도 제자리에 둔다 — 배경이 비치면 안 되므로 불투명하게
const stickyCellSx = {
    ...cellSx,
    position: 'sticky',
    left: 0,
    zIndex: 1,
    backgroundColor: palette.white,
};

// 더보기 컬럼은 32px — 버튼(아이콘 20 + 패딩 6)이 칸을 그대로 채운다
const actionCellSx = {
    ...cellSx,
    px: 0,
    textAlign: 'center',
};

/** 시안에 시각은 없다 — 날짜만 */
/** 숨긴 키 값 자리에 블러로 깔아 두는 글자 */
const API_KEY_DUMMY = '************************';

const formatDate = value => (value ? dayjs(value).format('YYYY. M. D') : '-');

/** scope 조합 → 발급 화면과 같은 라벨. 쓰기 권한이 하나라도 있으면 전체 권한이다. */
const scopeLabelKey = scopes => {
    if (!scopes?.length) return null;
    return scopes.some(scope => scope.endsWith(':write'))
        ? 'admin.apiKey.badge.fullAccess'
        : 'admin.apiKey.badge.readOnly';
};

/** 상태 배지 — 만료가 활성/비활성보다 앞선다 */
function StatusBadge({ item, t }) {
    const isExpired = item.isExpired === true;
    const isActive = item.status === 'ACTIVE';

    let color = STATUS_BADGE.inactive;
    let label = t('common.status.disabled');
    if (isExpired) {
        color = STATUS_BADGE.expired;
        label = t('common.status.expired');
    } else if (isActive) {
        color = STATUS_BADGE.active;
        label = t('common.status.enabled');
    }

    return (
        <Box
            sx={{
                display: 'inline-flex',
                alignItems: 'center',
                px: 2,
                py: '2px',
                borderRadius: '100px',
                backgroundColor: color.bg,
                // border는 배지 높이에 2px 더해지므로 안쪽 테두리로 그린다
                outline: `1px solid ${color.border}`,
                outlineOffset: '-1px',
                maxWidth: '100%',
            }}
        >
            <EllipsisText variant="body9_10" sx={{ color: color.text }}>
                {label}
            </EllipsisText>
        </Box>
    );
}

/** 보호 키 배지 — 관리자·사용자 경로에서는 만료일·활성화·비활성화·재발급이 거부된다(API_KEY_PROTECTED) */
function ProtectedBadge({ t }) {
    const color = STATUS_BADGE.inactive;
    return (
        <BaseTooltip title={t('admin.apiKey.badge.protectedTooltip')}>
            <Box
                sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    flexShrink: 0,
                    px: 2,
                    py: '2px',
                    borderRadius: '100px',
                    backgroundColor: color.bg,
                    outline: `1px solid ${color.border}`,
                    outlineOffset: '-1px',
                }}
            >
                <EllipsisText variant="body9_10" sx={{ color: color.text }}>
                    {t('admin.apiKey.badge.protected')}
                </EllipsisText>
            </Box>
        </BaseTooltip>
    );
}

/* ── 행 셀 ── */
/** 컬럼 field → 셀. 테이블 COLUMNS의 field와 짝을 이룬다. */
const CELL_RENDERERS = {
    // Key 이름 — 가로 스크롤 시 고정. 보호 키(슈퍼관리자 발급)는 이름 옆에 배지
    keyName: ({ item, t }) => (
        <TableCell sx={stickyCellSx}>
            <Stack direction="row" alignItems="center" gap={2} sx={{ minWidth: 0 }}>
                <EllipsisText variant="body7" color="text_80">
                    {item.keyName}
                </EllipsisText>
                {item.isProtected && <ProtectedBadge t={t} />}
            </Stack>
        </TableCell>
    ),

    keyPurpose: ({ item }) => (
        <TableCell sx={cellSx}>
            <EllipsisText variant="body9" color="text_60">
                {item.keyPurpose}
            </EllipsisText>
        </TableCell>
    ),

    // 테넌트명 — 슈퍼관리자 키는 소속 테넌트가 없어 '-'
    tenantName: ({ item }) => (
        <TableCell sx={cellSx}>
            <EllipsisText variant="body9" color="text_60">
                {item.tenantName || '-'}
            </EllipsisText>
        </TableCell>
    ),

    roleType: ({ item, t }) => (
        <TableCell sx={cellSx}>
            <EllipsisText variant="body9" color="text_60">
                {ROLE_LABEL_KEY[item.roleType] ? t(ROLE_LABEL_KEY[item.roleType]) : '-'}
            </EllipsisText>
        </TableCell>
    ),

    // 사용자 계정 — 관리자 키처럼 대상 계정이 없으면 '-'
    userAccount: ({ item }) => (
        <TableCell sx={cellSx}>
            {item.userId ? (
                <Stack gap={1} sx={{ minWidth: 0 }}>
                    <EllipsisText variant="body7" color="text_80">
                        {item.userName}
                    </EllipsisText>
                    <EllipsisText variant="caption" color="text_60">
                        {item.userId}
                    </EllipsisText>
                </Stack>
            ) : (
                <EllipsisText variant="body7" color="text_80">
                    -
                </EllipsisText>
            )}
        </TableCell>
    ),

    scopes: ({ item, t }) => {
        const scopeKey = scopeLabelKey(item.scopes);
        return (
            <TableCell sx={cellSx}>
                <EllipsisText variant={scopeKey ? 'body9_10' : 'body9'} color="text_60">
                    {scopeKey ? t(scopeKey) : '-'}
                </EllipsisText>
            </TableCell>
        );
    },

    status: ({ item, t }) => (
        <TableCell sx={cellSx}>
            <StatusBadge item={item} t={t} />
        </TableCell>
    ),

    registerDate: ({ item }) => (
        <TableCell sx={cellSx}>
            <EllipsisText variant="body9" color="text_60">
                {formatDate(item.registerDate)}
            </EllipsisText>
        </TableCell>
    ),

    lastUsedDate: ({ item }) => (
        <TableCell sx={cellSx}>
            <EllipsisText variant="body9" color="text_60">
                {formatDate(item.lastUsedDate)}
            </EllipsisText>
        </TableCell>
    ),

    // 만료일 — 이미 지난 키는 붉게
    expireDate: ({ item }) => (
        <TableCell sx={cellSx}>
            <EllipsisText
                variant="body9"
                sx={{ color: item.isExpired ? palette.status_error : palette.text_60 }}
            >
                {formatDate(item.expireDate)}
            </EllipsisText>
        </TableCell>
    ),

    // API Key (마스킹) — SYSTEM_MANAGED + SINGLE 은 서버가 숨긴 값(__MASKED__)을 내려 블러로 표시한다
    maskedApiKey: ({ item }) => (
        <TableCell sx={cellSx}>
            {isMaskedValue(item.maskedApiKey) ? (
                <MaskedValue value={item.maskedApiKey} dummy={API_KEY_DUMMY} variant="body9" />
            ) : (
                <EllipsisText variant="body9" color={item.maskedApiKey ? 'text_60' : 'text_40'}>
                    {item.maskedApiKey}
                </EllipsisText>
            )}
        </TableCell>
    ),

    // 옵션 메뉴 — id는 메뉴가 버튼 아래로 위치를 잡는 데 쓴다
    actions: ({ item, t, onContextMenuOpen }) => (
        <TableCell sx={actionCellSx}>
            <BaseTooltip title={t('tooltip.moreOptions')}>
                <IconButton
                    id="dots-button"
                    sx={{ p: '6px' }}
                    onClick={e => {
                        e.preventDefault();
                        e.stopPropagation();
                        onContextMenuOpen?.(e, item);
                    }}
                >
                    <Image
                        src="/common/icon-ic-dots-vertical.svg"
                        alt="dot"
                        id="dots"
                        width={20}
                        height={20}
                    />
                </IconButton>
            </BaseTooltip>
        </TableCell>
    ),
};

export const ApiKeyRowCells = React.memo(
    function ApiKeyRowCells({ item, columns, onContextMenuOpen }) {
        const t = useTranslations();

        return (
            <>
                {columns.map(col => {
                    const Cell = CELL_RENDERERS[col.field];
                    return (
                        <Cell
                            key={col.field}
                            item={item}
                            t={t}
                            onContextMenuOpen={onContextMenuOpen}
                        />
                    );
                })}
            </>
        );
    },
    (prev, next) => {
        // 변화가 있는 row만 리렌더 (전체 리렌더 x)
        return (
            prev.columns === next.columns &&
            prev.item?.apiKeySeq === next.item?.apiKeySeq &&
            prev.item?.status === next.item?.status &&
            prev.item?.expireDate === next.item?.expireDate &&
            // 만료 배지는 status보다 isExpired가 우선하므로 함께 비교한다
            prev.item?.isExpired === next.item?.isExpired &&
            prev.item?.maskedApiKey === next.item?.maskedApiKey &&
            prev.item?.isProtected === next.item?.isProtected
        );
    }
);

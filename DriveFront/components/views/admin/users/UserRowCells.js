import React from 'react';
import { TableCell, Stack, Typography, IconButton, Chip } from '@mui/material';
import Image from 'next/image';
import dayjs from 'dayjs';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';

import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import { getUserByUserType } from '@/constants/USERS';

const StatusChip = ({ t, status }) => {
    const isActive = status === 'ACTIVE';
    return (
        <Chip
            label={isActive ? t(`common.action.activate`) : t(`common.action.deactivate`)}
            color={isActive ? 'success' : 'default'}
            variant="outlined"
            sx={{
                borderRadius: 1,
                color: isActive ? 'green' : 'gray',
                borderColor: isActive ? 'green' : 'gray',
                width: 80,
                justifyContent: 'center',
            }}
        />
    );
};

/** 보호 계정 배지 — 테넌트 생성 때 만든 계정. 관리자 경로의 탈퇴·삭제·비활성·역할·비밀번호 변경은 서버가 USER_034 로 거부(유형 무관) */
const ProtectedUserBadge = ({ t }) => (
    <BaseTooltip title={t('admin.protectedUser.tooltip')}>
        <Chip
            label={t('admin.protectedUser.badge')}
            size="small"
            variant="outlined"
            sx={{
                flexShrink: 0,
                height: 20,
                borderRadius: '100px',
                color: '#747C8B',
                borderColor: '#DEE1E8',
                '& .MuiChip-label': { px: 2, fontSize: 11 },
            }}
        />
    </BaseTooltip>
);

export const UserRowCells = React.memo(
    function UserRowCells(props) {
        const {
            item,
            checked,

            // 표시 옵션
            showRole,
            showLastLogin,
            showStatus,
            showRequestedDate,
            showScheduledDate,
            showDeletedBy,
            onContextMenuOpen,
            onToggleCheck,

            t,
            hasMultipleSelection,
            onToggleExpand,
            expanded,
        } = props;

        return (
            <>
                {/* 체크박스 */}
                <TableCell
                    align={'left'}
                    sx={{
                        width: 36,
                        p: {
                            md: '0px 4px 0px 8px !important',
                            xs: '0 0 0 6px!important',
                        },
                    }}
                    onClick={e => {
                        e.stopPropagation();
                        onToggleCheck(item);
                    }}
                >
                    {/* md 이상 */}
                    <Stack
                        direction="row"
                        alignItems="center"
                        sx={{ display: { xs: 'none', md: 'flex' } }}
                        onClick={e => {
                            e.stopPropagation();
                            onToggleCheck(item);
                        }}
                    >
                        <div className="toggle-wrap">
                            <span className="toggle-layer toggle-checkbox">
                                <StyledCheckbox checked={checked} />
                            </span>
                            <span className="toggle-layer toggle-icon">
                                <Image
                                    src={getUserByUserType(item?.status)?.src}
                                    alt={getUserByUserType(item?.status)?.name}
                                    width={24}
                                    height={24}
                                />
                            </span>
                        </div>
                    </Stack>

                    {/* md 미만*/}
                    <Stack
                        direction="row"
                        justifyContent={'center'}
                        alignItems="center"
                        sx={{ display: { xs: 'flex', md: 'none' } }}
                        onClick={e => {
                            e.stopPropagation();
                            onToggleCheck(item);
                        }}
                    >
                        <StyledCheckbox checked={checked} />
                    </Stack>
                </TableCell>

                {/* UserName */}
                <TableCell
                    style={{ paddingRight: '2px' }}
                    sx={{
                        px: 2,
                        py: 2.5,
                        width: '100%',
                        minWidth: 0,
                        maxWidth: 50,
                    }}
                >
                    <Stack direction="row" alignItems="center" gap={3} sx={{ minWidth: 0 }}>
                        <Stack
                            direction="row"
                            alignItems="center"
                            sx={{
                                display: { xs: 'flex', md: 'none' },
                            }}
                        >
                            <Image
                                src={getUserByUserType(item?.status)?.src}
                                alt={getUserByUserType(item?.status)?.name}
                                width={24}
                                height={24}
                            />
                        </Stack>
                        <Stack direction={'column'} gap={1} sx={{ minWidth: 0 }}>
                            <Stack
                                direction="row"
                                alignItems={'center'}
                                gap={1}
                                sx={{ minWidth: 0, flex: 1 }}
                            >
                                {/** 유저 확장 (로그) */}
                                <IconButton
                                    size="small"
                                    onClick={e => {
                                        e.stopPropagation();
                                        onToggleExpand(item.userSeq);
                                    }}
                                    sx={{ p: 0.5 }}
                                >
                                    {expanded ? (
                                        <ExpandLess sx={{ fontSize: 16 }} />
                                    ) : (
                                        <ExpandMore sx={{ fontSize: 16 }} />
                                    )}
                                </IconButton>

                                <BaseTooltip
                                    key={item.userSeq}
                                    title={item?.userName}
                                    disableHoverListener={item.userName.length <= 30} // 추후 nameAreaWidth를 사용해 툴팁 표시하도록 수정 필요
                                >
                                    <Typography
                                        variant={'body5'}
                                        color={'text_80'}
                                        sx={{
                                            maxWidth: {
                                                xs: '100%',
                                                md: '100%',
                                                lg: '100%',
                                            },
                                            minWidth: 0, // 텍스트도 수축 허용
                                            flexShrink: 1,
                                            '&:hover': {
                                                textDecoration: 'underline',
                                            },
                                        }}
                                        noWrap
                                    >
                                        {item.userName}
                                    </Typography>
                                </BaseTooltip>
                                {item.isProtected && <ProtectedUserBadge t={t} />}
                            </Stack>
                        </Stack>
                    </Stack>
                </TableCell>

                {/* User ID */}
                <TableCell
                    style={{
                        width: 200,
                        minWidth: 200,
                        maxWidth: 200,
                        paddingLeft: 8,
                        paddingRight: 56,
                    }}
                    sx={{
                        display: { xs: 'none', md: 'table-cell' },
                    }}
                >
                    <Stack direction="row" alignItems="center" gap={3}>
                        <BaseTooltip
                            key={item.userSeq}
                            title={item?.userId}
                            disableHoverListener={(item?.userId?.length ?? 0) <= 23}
                        >
                            <Typography
                                variant="body9"
                                color="text_60"
                                noWrap
                                sx={{
                                    maxWidth: '100%',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                }}
                            >
                                {item?.userId}
                            </Typography>
                        </BaseTooltip>
                    </Stack>
                </TableCell>

                {/* Role — 보유 역할(관리자, 사용자) */}
                {showRole && (
                    <TableCell
                        style={{ paddingLeft: 8, paddingRight: 24, whiteSpace: 'nowrap' }}
                        sx={{ display: { xs: 'none', md: 'table-cell' } }}
                    >
                        <Typography variant="body9" color={'text_60'}>
                            {(item?.roles ?? [])
                                .map(role =>
                                    t(role === 'ADMIN' ? 'common.label.admin' : 'common.label.user')
                                )
                                .join(', ')}
                        </Typography>
                    </TableCell>
                )}

                {/* LastLogin */}
                {showLastLogin && (
                    <TableCell
                        style={{ paddingLeft: 32, paddingRight: 40 }}
                        sx={{ display: { xs: 'none', md: 'table-cell' } }}
                    >
                        <Typography
                            variant="body9"
                            color={'text_60'}
                            sx={{ fontVariantNumeric: 'tabular-nums' }}
                        >
                            {item?.lastLoginDate
                                ? dayjs(item?.lastLoginDate).format('YYYY. M. D. H:mm')
                                : ''}
                        </Typography>
                    </TableCell>
                )}

                {/* Status */}
                {showStatus && (
                    <TableCell
                        style={{
                            paddingLeft: 24,
                            paddingRight: 24,
                            width: 0,
                        }}
                        sx={{ display: { xs: 'table-cell', md: 'table-cell' } }}
                    >
                        <Stack direction={'row'} gap={2}>
                            <Typography variant="body9" color={'text_60'}>
                                <StatusChip t={t} status={item?.status} />
                            </Typography>
                        </Stack>
                    </TableCell>
                )}

                {/* requestedDate */}
                {showRequestedDate && (
                    <TableCell
                        style={{ paddingLeft: 8, paddingRight: 40 }}
                        sx={{ display: { xs: 'table-cell', md: 'table-cell' } }}
                    >
                        <Typography
                            variant="body9"
                            color={'text_60'}
                            sx={{ fontVariantNumeric: 'tabular-nums' }}
                        >
                            {item?.pendingDeleteDate
                                ? dayjs(item?.pendingDeleteDate).format('YYYY. M. D. H:mm')
                                : ''}
                        </Typography>
                    </TableCell>
                )}

                {/* scheduledDate */}
                {showScheduledDate && (
                    <TableCell
                        style={{ paddingLeft: 8, paddingRight: 40 }}
                        sx={{ display: { xs: 'table-cell', md: 'table-cell' } }}
                    >
                        <Typography
                            variant="body9"
                            color={'text_60'}
                            sx={{ fontVariantNumeric: 'tabular-nums' }}
                        >
                            {item?.deleteScheduleTime
                                ? dayjs(item?.deleteScheduleTime).format('YYYY. M. D.')
                                : ''}
                        </Typography>
                    </TableCell>
                )}

                {/* deletedBy */}
                {showDeletedBy && (
                    <TableCell
                        style={{
                            width: 200,
                            minWidth: 200,
                            maxWidth: 200,
                            paddingLeft: 8,
                            paddingRight: 56,
                        }}
                        sx={{ display: { xs: 'none', md: 'none', midLg: 'table-cell' } }}
                    >
                        <Stack direction="row" alignItems="center" gap={3}>
                            <BaseTooltip
                                key={item.userSeq}
                                title={item?.deleteUserId}
                                disableHoverListener={(item?.deleteUserId?.length ?? 0) <= 23}
                            >
                                <Typography
                                    variant="body9"
                                    color="text_60"
                                    noWrap
                                    sx={{
                                        maxWidth: '100%',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                    }}
                                >
                                    {item?.deleteUserId}
                                </Typography>
                            </BaseTooltip>
                        </Stack>
                    </TableCell>
                )}

                {/* 더보기 메뉴 */}
                <TableCell
                    sx={{
                        width: { xs: 40, md: 40 },
                        minWidth: { xs: 0, md: 40 },
                    }}
                >
                    <Stack
                        direction="row"
                        spacing={1}
                        alignItems="center"
                        justifyContent="flex-end"
                    >
                        <span
                            style={{
                                cursor: hasMultipleSelection ? 'default' : 'pointer',
                                display: 'inline-flex',
                            }}
                            onClick={e => e.stopPropagation()}
                        >
                            <BaseTooltip title={t('tooltip.moreOptions')}>
                                <IconButton
                                    onClick={e => {
                                        if (hasMultipleSelection) return;

                                        e.preventDefault();
                                        e.stopPropagation();
                                        onContextMenuOpen(e, item);
                                    }}
                                    id={'dots-button'}
                                    disabled={hasMultipleSelection}
                                >
                                    <Image
                                        src="/common/icon-ic-dots-vertical.svg"
                                        alt="dot"
                                        id={'dots'}
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        </span>
                    </Stack>
                </TableCell>
            </>
        );
    },
    (prev, next) => {
        // 변화가 있는 row만 리렌더 (전체 리렌더 x)
        // item은 변경 시 새 객체로 교체되므로 참조로 비교한다.
        // userSeq·status만 비교하면 이름·용량 같은 다른 필드 변경이 화면에 안 반영된다
        return (
            prev.item === next.item &&
            prev.checked === next.checked &&
            prev.showRole === next.showRole &&
            prev.showLastLogin === next.showLastLogin &&
            prev.showStatus === next.showStatus &&
            prev.showRequestedDate === next.showRequestedDate &&
            prev.showScheduledDate === next.showScheduledDate &&
            prev.showDeletedBy === next.showDeletedBy &&
            prev.menuTargetRow === next.menuTargetRow &&
            // 처음으로 hasMultipleSelection === true가 될 때, 렌더링 된 row '더보기' 메뉴 모두 비활성화 처리 필요
            prev.hasMultipleSelection === next.hasMultipleSelection &&
            prev.expanded === next.expanded
        );
    }
);

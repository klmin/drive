import React, { useRef, useState } from 'react';
import { Box, CircularProgress, IconButton, Stack, TableCell, Typography } from '@mui/material';
import StyledBadge from '@/components/common/button/StyledBadge';
import { useTranslations } from 'next-intl';
import dayjs from 'dayjs';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import Image from 'next/image';
import api from '@/utils/api';
import palette from '@/theme/palette';
import { getDaysLeft, getDaysOver, getLicenseStatus } from '@/utils/license';

/* ── 말줄임 감지 툴팁: scrollWidth > clientWidth 일 때만 툴팁 표시 ── */
const EllipsisTooltip = ({ title, variant, sx, children }) => {
    const ref = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    const handleMouseEnter = () => {
        if (ref.current) {
            setIsTruncated(ref.current.scrollWidth > ref.current.clientWidth);
        }
    };

    return (
        <BaseTooltip title={isTruncated ? title : ''}>
            {/* component="p": 커스텀 variant는 MUI variantMapping에 없어 <span>(인라인)으로 렌더링됨.
                인라인 요소는 clientWidth/scrollWidth가 0이라 말줄임 감지 불가 → 블록 요소로 강제 */}
            <Typography
                component="p"
                ref={ref}
                variant={variant}
                onMouseEnter={handleMouseEnter}
                sx={sx}
            >
                {children}
            </Typography>
        </BaseTooltip>
    );
};

const ellipsisSx = { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };

const LICENSE_STATUS_CONFIG = {
    VALID: {
        labelKey: 'superAdmin.tenantManagement.badge.valid',
        tooltipKey: 'superAdmin.tenantManagement.badgeTooltip.valid',
        bg: palette.badge_license_active_bg,
        outline: palette.badge_license_active_border,
        color: palette.badge_license_active_text,
        pill: true,
    },
    EXPIRING_SOON: {
        labelKey: 'adminLicense.status.expiring',
        tooltipKey: 'superAdmin.tenantManagement.badgeTooltip.expiring',
        bg: palette.badge_license_expiring_bg,
        outline: palette.badge_license_expiring_border,
        color: palette.badge_license_expiring_text,
        pill: true,
    },
    NOT_ISSUED: {
        labelKey: 'superAdmin.tenantManagement.badge.noLicense',
        tooltipKey: 'superAdmin.tenantManagement.badgeTooltip.noLicense',
        bg: palette.badge_license_not_issued_bg,
        outline: palette.badge_license_not_issued_border,
        color: palette.badge_license_not_issued_text,
        pill: true,
    },
    EXPIRED: {
        labelKey: 'superAdmin.tenantManagement.badge.expired',
        tooltipKey: 'superAdmin.tenantManagement.badgeTooltip.expired',
        bg: palette.badge_license_expired_bg,
        outline: palette.badge_license_expired_border,
        color: palette.badge_license_expired_text,
        pill: true,
    },
    SEAT_EXCEEDED: {
        labelKey: 'superAdmin.tenantManagement.badge.seatExceeded',
        tooltipKey: 'superAdmin.tenantManagement.badgeTooltip.seatExceeded',
        color: palette.text_60,
        pill: false,
    },
};

/* ── 라이선스 상태 셀 ── */
const StatusCell = ({ license }) => {
    const t = useTranslations();
    const status = getLicenseStatus(license);
    const cfg = LICENSE_STATUS_CONFIG[status];

    if (!cfg.pill) {
        // fontSize:12/fontWeight:500/lineHeight:16px → typography 미등록, sx 유지
        return (
            <BaseTooltip title={t(cfg.tooltipKey)} placement="right">
                <Box
                    sx={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        verticalAlign: 'middle',
                        gap: '4px',
                    }}
                >
                    <Image
                        src="/super-admin/tenants/icon-ic-seat-exceeded.svg"
                        alt="seat exceeded"
                        width={14}
                        height={14}
                        style={{ display: 'block' }}
                    />
                    <Typography
                        sx={{ fontSize: 12, fontWeight: 500, lineHeight: '16px', color: cfg.color }}
                    >
                        {t(cfg.labelKey)}
                    </Typography>
                </Box>
            </BaseTooltip>
        );
    }

    return (
        <BaseTooltip title={t(cfg.tooltipKey)} placement="right">
            <StyledBadge bg={cfg.bg} borderColor={cfg.outline} color={cfg.color}>
                {t(cfg.labelKey)}
            </StyledBadge>
        </BaseTooltip>
    );
};

/* ── 라이선스 상세 셀 ── */
const LicenseDetailCell = ({ license }) => {
    const t = useTranslations();
    const status = getLicenseStatus(license);

    if (status === 'NOT_ISSUED') {
        return (
            <Typography variant="body9" sx={{ color: palette.text_80 }}>
                -
            </Typography>
        );
    }

    let text;
    let color = palette.text_60;
    if (status === 'SEAT_EXCEEDED') {
        text = t('superAdmin.tenantManagement.detail.seatCount', {
            used: license.seat.current,
            total: license.seat.max,
        });
        color = palette.status_error;
    } else if (status === 'VALID' || status === 'EXPIRING_SOON') {
        text = t('superAdmin.tenantManagement.detail.daysLeft', {
            day: getDaysLeft(license.expiresOn),
        });
    } else {
        // EXPIRED
        text = t('superAdmin.tenantManagement.detail.daysOver', {
            day: getDaysOver(license.expiresOn),
        });
    }

    return (
        <EllipsisTooltip title={text} variant="body9" sx={{ color, ...ellipsisSx }}>
            {text}
        </EllipsisTooltip>
    );
};

const cellSx = {
    height: 44,
    py: 0,
    pl: '8px',
    pr: '32px',
    borderBottom: `1px solid ${palette.table_divider_nomal}`,
};

/* ── 행 셀 ── */
export const TenantRowCells = React.memo(
    function TenantRowCells({ item, onContextMenuOpen, adminLinkDisabled }) {
        const t = useTranslations();
        const [isNavigating, setIsNavigating] = useState(false);
        // 서버가 테넌트 방식(서브도메인/PATH)에 맞춰 만든 주소에서 scheme 만 떼고 표시
        const address = item.clientUrl?.replace(/^https?:\/\//, '') ?? '';
        const tenantTypeLabel = item.tenantType
            ? t(`superAdmin.tenantManagement.tenantType.${item.tenantType}`)
            : '-';
        const userModeLabel = item.userMode
            ? t(`superAdmin.tenantManagement.userMode.${item.userMode}`)
            : '-';
        const officeLabel =
            item.office?.connectionType === 'onPremise'
                ? t(
                      item.office.serviceManaged
                          ? 'superAdmin.tenantManagement.office.onPremiseManaged'
                          : 'superAdmin.tenantManagement.office.onPremise'
                  )
                : t('superAdmin.tenantManagement.office.none');
        const createdAtLabel = item?.registerDate
            ? dayjs(item.registerDate).format('YYYY. M. D. H:mm')
            : '';

        const handleAdminLink = async e => {
            e.stopPropagation();
            if (adminLinkDisabled || isNavigating) return;
            setIsNavigating(true);
            try {
                const res = await api.Post(`super-admin/tenants/${item.tenantSeq}/entry-key`);
                // 진입 URL 은 서버가 테넌트 주소 체계(서브도메인 / `/t/{slug}` 경로)를 반영해 완성해 준다
                const entryUrl = res?.data?.entryUrl;
                if (!entryUrl) throw new Error('entryUrl 없음');
                window.open(entryUrl, '_blank');
            } catch (err) {
                console.error('임시 키 발급 실패', err);
            } finally {
                setIsNavigating(false);
            }
        };

        return (
            <>
                {/* 테넌트명 */}
                <TableCell sx={{ ...cellSx }}>
                    <EllipsisTooltip
                        title={item.tenantName}
                        variant="body7"
                        sx={{
                            color: palette.text_80,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {item.tenantName}
                    </EllipsisTooltip>
                </TableCell>

                {/* 도메인 */}
                <TableCell sx={{ ...cellSx }}>
                    <EllipsisTooltip
                        title={address}
                        variant="body9"
                        sx={{
                            color: palette.text_60,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {address}
                    </EllipsisTooltip>
                </TableCell>

                {/* 유형 — 생성 후 바뀌지 않는다 */}
                <TableCell sx={{ ...cellSx }}>
                    <EllipsisTooltip
                        title={tenantTypeLabel}
                        variant="body9"
                        sx={{ color: palette.text_60, ...ellipsisSx }}
                    >
                        {tenantTypeLabel}
                    </EllipsisTooltip>
                </TableCell>

                {/* 사용자 모드 — SINGLE 은 계정 1개 고정(SYSTEM_MANAGED 만) */}
                <TableCell sx={{ ...cellSx }}>
                    <EllipsisTooltip
                        title={userModeLabel}
                        variant="body9"
                        sx={{ color: palette.text_60, ...ellipsisSx }}
                    >
                        {userModeLabel}
                    </EllipsisTooltip>
                </TableCell>

                {/* 라이선스 상태 */}
                <TableCell sx={{ ...cellSx }}>
                    <StatusCell license={item.license} />
                </TableCell>

                {/* 상세 */}
                <TableCell sx={{ ...cellSx }}>
                    <LicenseDetailCell license={item.license} />
                </TableCell>

                {/* Office 연결 — 연동 안 함 / On-premise(서비스 관리면 표시) */}
                <TableCell sx={{ ...cellSx }}>
                    <EllipsisTooltip
                        title={officeLabel}
                        variant="body9"
                        sx={{ color: palette.text_60, ...ellipsisSx }}
                    >
                        {officeLabel}
                    </EllipsisTooltip>
                </TableCell>

                {/* 생성일 */}
                <TableCell sx={{ ...cellSx }}>
                    <EllipsisTooltip
                        title={createdAtLabel}
                        variant="body9"
                        sx={{ color: palette.text_60, ...ellipsisSx }}
                    >
                        {createdAtLabel}
                    </EllipsisTooltip>
                </TableCell>

                {/* 어드민 바로가기 */}
                <TableCell
                    align="right"
                    onClick={handleAdminLink}
                    sx={{
                        ...cellSx,
                        pr: '8px',
                        cursor: adminLinkDisabled || isNavigating ? 'default' : 'pointer',
                    }}
                >
                    {isNavigating ? (
                        <CircularProgress size={16} />
                    ) : (
                        <BaseTooltip
                            title={t(
                                adminLinkDisabled
                                    ? 'superAdmin.tenantManagement.tooltip.adminLinkDisabled'
                                    : 'superAdmin.tenantManagement.tooltip.adminLink'
                            )}
                            placement="left"
                        >
                            <Box
                                sx={{
                                    width: 24,
                                    height: 24,
                                    bgcolor: palette.white,
                                    borderRadius: '3.43px',
                                    outline: `0.86px solid ${palette.btn_border}`,
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    verticalAlign: 'middle',
                                    opacity: adminLinkDisabled ? 0.4 : 1,
                                }}
                            >
                                <Image
                                    src="/super-admin/tenants/icon-ic-external-link.svg"
                                    alt="admin link"
                                    width={16}
                                    height={16}
                                />
                            </Box>
                        </BaseTooltip>
                    )}
                </TableCell>

                {/* 더보기 메뉴 */}
                <TableCell sx={{ ...cellSx, pl: 0, pr: '8px' }} align="center">
                    <span
                        style={{ cursor: 'pointer', display: 'inline-flex' }}
                        onClick={e => e.stopPropagation()}
                    >
                        <BaseTooltip title={t('tooltip.moreOptions')}>
                            <IconButton
                                onClick={e => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    onContextMenuOpen(e, item);
                                }}
                                id={'dots-button'}
                                sx={{ p: '6px', borderRadius: '32px' }}
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
                </TableCell>
            </>
        );
    },
    (prev, next) => {
        return (
            prev.item?.tenantSeq === next.item?.tenantSeq &&
            prev.item?.tenantName === next.item?.tenantName &&
            prev.item?.clientUrl === next.item?.clientUrl &&
            prev.item?.tenantType === next.item?.tenantType &&
            prev.item?.userMode === next.item?.userMode &&
            prev.item?.license === next.item?.license &&
            prev.item?.office?.connectionType === next.item?.office?.connectionType &&
            prev.item?.office?.serviceManaged === next.item?.office?.serviceManaged &&
            prev.adminLinkDisabled === next.adminLinkDisabled
        );
    }
);

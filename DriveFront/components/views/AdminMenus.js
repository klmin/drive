'use client';

import { useState } from 'react';
import { Box, ButtonBase, Collapse, Typography } from '@mui/material';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import useNavigate from '@/hooks/useNavigate';
import ArrowIcon from '@/components/common/icons/ArrowIcon';
import { ADMIN_MENU_PATHS } from '@/constants/routes';

/** 사이드바 메뉴 그룹 정의 */
const MENU_GROUPS = [
    {
        key: 'accounts',
        parentPath: 'accounts',
        labelKey: 'AdminSidebar.Accounts',
        iconSrc: '/admin/menu/icon-ic-group-accounts.svg',
        items: [
            { labelKey: 'AdminSidebar.AccountList', path: ADMIN_MENU_PATHS.USERS },
            { labelKey: 'AdminSidebar.pendingDeletion', path: ADMIN_MENU_PATHS.PENDING_DELETION },
        ],
    },
    {
        key: 'system-settings',
        parentPath: 'settings',
        labelKey: 'AdminSidebar.SystemSettings',
        iconSrc: '/admin/menu/icon-ic-group-system-settings.svg',
        items: [
            { labelKey: 'admin.sidebar.systemInfo', path: ADMIN_MENU_PATHS.SYSTEM_INFO },
            { labelKey: 'AdminSidebar.SMTP', path: ADMIN_MENU_PATHS.SMTP },
            { labelKey: 'AdminSidebar.License', path: ADMIN_MENU_PATHS.LICENSE },
            { labelKey: 'AdminSidebar.Office', path: ADMIN_MENU_PATHS.OFFICE },
            { labelKey: 'AdminSidebar.Storage', path: ADMIN_MENU_PATHS.STORAGE },
            { labelKey: 'AdminSidebar.Intellect', path: ADMIN_MENU_PATHS.INTELLECT },
        ],
    },
    {
        key: 'security',
        parentPath: 'security',
        labelKey: 'AdminSidebar.Security',
        iconSrc: '/admin/menu/icon-ic-group-security.svg',
        items: [
            { labelKey: 'AdminSidebar.Authentication', path: ADMIN_MENU_PATHS.AUTHENTICATION },
            { labelKey: 'AdminSidebar.ApiKeys', path: ADMIN_MENU_PATHS.API_KEYS },
            { labelKey: 'AdminSidebar.AccessControl', path: ADMIN_MENU_PATHS.ACCESS_CONTROL },
            { labelKey: 'admin.sidebar.auditLog', path: ADMIN_MENU_PATHS.AUDIT_LOGS },
        ],
    },
    {
        key: 'service',
        parentPath: 'service',
        labelKey: 'AdminSidebar.Service',
        iconSrc: '/admin/menu/icon-ic-group-service.svg',
        items: [
            { labelKey: 'AdminSidebar.Branding', path: ADMIN_MENU_PATHS.BRANDING, paidOnly: true },
            { labelKey: 'AdminSidebar.Feature', path: ADMIN_MENU_PATHS.SHARE },
        ],
    },
];

/** 메뉴 버튼 공통 — 키보드 포커스·Enter/Space 동작은 ButtonBase 가 맡는다 */
const MENU_BUTTON_SX = {
    width: '100%',
    justifyContent: 'flex-start',
    textAlign: 'left',
    borderRadius: '4px',
    '&.Mui-focusVisible': {
        outline: `2px solid ${palette.sidebar_text_secondary}`,
        outlineOffset: '-2px',
    },
};

/** 관리자 사이드바 메뉴 */
const AdminMenus = ({ paidWhiteLabel }) => {
    const t = useTranslations();
    const router = useRouter();
    const navigate = useNavigate();
    const pathname =
        router.asPath
            .split(/[?#]/)[0]
            .replace(/^\/admin\/?/, '')
            .replace(/\/+$/, '')
            .split('/')
            .filter(Boolean)
            .pop() || '';
    const [expanded, setExpanded] = useState({
        accounts: true,
        'system-settings': true,
        security: true,
        service: true,
    });

    const handleToggle = key => setExpanded(prev => ({ ...prev, [key]: !prev[key] }));

    return (
        <Box
            component="nav"
            sx={{
                pl: '12px',
                // 스크롤바 자리(6px)를 포함해 오른쪽도 12px
                pr: '6px',
                pt: '16px',
                pb: '8px',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px',
            }}
        >
            {MENU_GROUPS.map(({ key, parentPath, labelKey, iconSrc, items }) => {
                const visibleItems = items.filter(item => !item.paidOnly || paidWhiteLabel);
                const groupActive = visibleItems.some(item => item.path === pathname);
                const groupColor = groupActive ? palette.sidebar_text_primary : palette.text_muted;
                const groupIconColor = groupActive
                    ? palette.sidebar_text_primary
                    : palette.sidebar_text_disabled;
                const expandIconColor = groupActive
                    ? palette.sidebar_text_disabled
                    : palette.text_muted;

                return (
                    <Box key={key} sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        <ButtonBase
                            disableRipple
                            aria-expanded={expanded[key]}
                            onClick={() => handleToggle(key)}
                            sx={{
                                ...MENU_BUTTON_SX,
                                height: 36,
                                px: '12px',
                                py: '4px',
                                gap: '10px',
                                '&:hover': { backgroundColor: palette.sidebar_hover },
                            }}
                        >
                            <Box
                                sx={{
                                    width: 16,
                                    height: 16,
                                    flexShrink: 0,
                                    backgroundColor: groupIconColor,
                                    mask: `url(${iconSrc}) center / contain no-repeat`,
                                    WebkitMask: `url(${iconSrc}) center / contain no-repeat`,
                                }}
                            />
                            <Typography variant="body6_tight" sx={{ flex: 1 }} color={groupColor}>
                                {t(labelKey)}
                            </Typography>
                            <ArrowIcon
                                direction={expanded[key] ? 'up' : 'down'}
                                sx={{ width: 16, height: 16, color: expandIconColor }}
                            />
                        </ButtonBase>

                        <Collapse in={expanded[key]} timeout="auto" unmountOnExit>
                            <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                {visibleItems.map(({ labelKey: itemLabelKey, path }) => {
                                    const active = path === pathname;
                                    const itemColor = active
                                        ? palette.sidebar_text_primary
                                        : palette.text_muted;

                                    return (
                                        <ButtonBase
                                            key={path}
                                            disableRipple
                                            aria-current={active ? 'page' : undefined}
                                            onClick={() => navigate(`/admin/${parentPath}/${path}`)}
                                            sx={{
                                                ...MENU_BUTTON_SX,
                                                height: 32,
                                                pl: '17px',
                                                pr: '12px',
                                                gap: '16px',
                                                backgroundColor: active
                                                    ? palette.sidebar_primary
                                                    : 'transparent',
                                                '&:hover': {
                                                    backgroundColor: active
                                                        ? palette.sidebar_primary
                                                        : palette.sidebar_hover,
                                                },
                                            }}
                                        >
                                            <Box
                                                sx={{
                                                    width: 4,
                                                    height: 4,
                                                    borderRadius: '4px',
                                                    backgroundColor: itemColor,
                                                    flexShrink: 0,
                                                }}
                                            />
                                            <Typography
                                                variant="body6_tight"
                                                sx={{ flex: 1 }}
                                                color={itemColor}
                                            >
                                                {t(itemLabelKey)}
                                            </Typography>
                                        </ButtonBase>
                                    );
                                })}
                            </Box>
                        </Collapse>
                    </Box>
                );
            })}
        </Box>
    );
};

/** 현재 경로의 [그룹 라벨 키, 메뉴 라벨 키] — 메뉴에 없는 화면은 null */
export const findBreadcrumb = pathname => {
    for (const group of MENU_GROUPS) {
        const item = group.items.find(({ path }) => path === pathname);
        if (item) return [group.labelKey, item.labelKey];
    }
    return null;
};

export default AdminMenus;

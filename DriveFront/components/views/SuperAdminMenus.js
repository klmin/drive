'use client';

import { useState } from 'react';
import { Box, ButtonBase, Collapse, Typography } from '@mui/material';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import useNavigate from '@/hooks/useNavigate';
import ArrowIcon from '@/components/common/icons/ArrowIcon';
import { SUPERADMIN_MENU_PATHS } from '@/constants/routes';

/** 슈퍼관리자 사이드바 메뉴 그룹 정의 */
const MENU_GROUPS = [
    {
        key: 'tenants',
        parentPath: 'accounts',
        labelKey: 'superAdmin.sidebar.tenantManagement',
        iconSrc: '/super-admin/menu/icon-ic-group-tenants.svg',
        items: [
            { labelKey: 'superAdmin.sidebar.tenantList', path: SUPERADMIN_MENU_PATHS.TENANTS },
            {
                labelKey: 'superAdmin.sidebar.apiKeyManagement',
                path: SUPERADMIN_MENU_PATHS.API_KEYS,
            },
        ],
    },
    {
        key: 'system-settings',
        parentPath: 'settings',
        labelKey: 'AdminSidebar.SystemSettings',
        iconSrc: '/super-admin/menu/icon-ic-group-system-settings.svg',
        items: [{ labelKey: 'admin.sidebar.systemInfo', path: SUPERADMIN_MENU_PATHS.SYSTEM_INFO }],
    },
    {
        key: 'service',
        parentPath: 'service',
        labelKey: 'superAdmin.sidebar.featureManagement',
        iconSrc: '/super-admin/menu/icon-ic-group-service.svg',
        items: [
            { labelKey: 'superAdmin.sidebar.shareSetting', path: SUPERADMIN_MENU_PATHS.SHARE },
            {
                labelKey: 'superAdmin.sidebar.loginSetting',
                path: SUPERADMIN_MENU_PATHS.LOGIN_SETTINGS,
            },
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

/** 슈퍼관리자 사이드바 메뉴 */
const SuperAdminMenus = () => {
    const t = useTranslations();
    const router = useRouter();
    const navigate = useNavigate();
    const pathname =
        router.asPath
            .split(/[?#]/)[0]
            .replace(/^\/superadmin\/?/, '')
            .replace(/\/+$/, '')
            .split('/')
            .filter(Boolean)
            .pop() || '';
    const [expanded, setExpanded] = useState({
        tenants: true,
        'system-settings': true,
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
                const groupActive = items.some(item => item.path === pathname);
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
                                {items.map(({ labelKey: itemLabelKey, path }) => {
                                    const active = path === pathname;
                                    const itemColor = active
                                        ? palette.sidebar_text_primary
                                        : palette.text_muted;

                                    return (
                                        <ButtonBase
                                            key={path}
                                            disableRipple
                                            aria-current={active ? 'page' : undefined}
                                            onClick={() =>
                                                navigate(`/superadmin/${parentPath}/${path}`)
                                            }
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

export default SuperAdminMenus;

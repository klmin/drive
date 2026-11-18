import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
    Box,
    CssBaseline,
    Drawer,
    Fade,
    IconButton,
    Menu,
    MenuItem,
    Stack,
    Typography,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import Image from 'next/image';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { useDialog } from '@/provider/DialogProvider';
import { useUser } from '@/context/UserContext';
import api from '@/utils/api';
import ProfileImage from '@/components/common/input/ProfileImage';
import ArrowIcon from '@/components/common/icons/ArrowIcon';
import AdminMenus, { findBreadcrumb } from '@/components/views/AdminMenus';
import {
    clearAuth,
    getPasswordChangeRequired,
    isApiKeyLogin,
    removePasswordChangeRequired,
} from '@/utils/authTokens';
import { useLicense } from '@/provider/LicenseProvider';
import { ROUTES } from '@/constants/routes';
import { BaseTooltip } from '../tooltip/BaseTooltip';
import { tenantHref } from '@/utils/tenantPrefix';
import LicenseWatermark from '@/components/common/LicenseWatermark';
import PlaygroundBanner from '@/components/common/PlaygroundBanner';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { isPoweredByPlan } from '@/utils/license';

const SIDEBAR_WIDTH = 260;
// 캔버스 바깥 여백·사이드바와 본문 카드 사이 간격
const CANVAS_GAP = 12;
const CANVAS_BACKGROUND = '#EEF0F3';
const CARD_RADIUS = '16px';

const PROFILE_MENU_SHADOW =
    '0px 4px 16px rgba(0, 0, 0, 0.08), 0px 2px 6px -1px rgba(0, 0, 0, 0.12)';
const PROFILE_MENU_ITEM_SX = {
    px: '16px',
    py: '8px',
    gap: '8px',
    // 테마 MenuItem 의 선택 배경이 !important 라 같이 올려야 덮인다
    '&.Mui-selected': { background: `${palette.menu_item_selected_bg} !important` },
};

/** 프로필 메뉴 아이콘 — mask 로 그려 선택 상태 색을 따른다 */
const ProfileMenuIcon = ({ src, color }) => (
    <Box
        sx={{
            width: 16,
            height: 16,
            flexShrink: 0,
            backgroundColor: color,
            mask: `url(${src}) center / contain no-repeat`,
            WebkitMask: `url(${src}) center / contain no-repeat`,
        }}
    />
);

/** keepMounted 메뉴는 MenuList autoFocus 가 첫 마운트에만 돌아, 열릴 때 목록에 직접 포커스를 준다 */
const focusProfileMenuList = paper => paper.querySelector('[role="menu"]')?.focus();

// 스크롤은 각 페이지 컨테이너가 맡는다 — 기존 레이아웃에서 body(overflow: hidden)가 하던 역할
const Main = styled('main')({
    flex: 1,
    minHeight: 0,
    overflow: 'hidden',
});

// 배너 상태별 i18n 키
// titleKey 파라미터: SEAT_EXCEEDED → {usedSeats, totalSeats}, 나머지 → {expireDate, dDay}
const BANNER_CONFIG = {
    // 미발급
    NOT_ISSUED: {
        titleKey: 'admin.licenseBanner.noLicense.title',
        descKey: 'admin.licenseBanner.noLicense.description',
        sidebar: {
            iconSrc: '/admin/license-banner/icon-ic-license-not-issued.svg',
            iconBg: palette.sidebar_neutral_bg,
            highlightColor: null,
        },
    },
    // 시트수 초과
    SEAT_EXCEEDED: {
        titleKey: 'admin.licenseBanner.seatExceeded.title',
        descKey: 'admin.licenseBanner.seatExceeded.description',
        sidebar: {
            iconSrc: '/admin/license-banner/icon-ic-license-seat-exceeded.svg',
            iconBg: palette.sidebar_warning_bg,
            highlightColor: palette.sidebar_warning,
        },
    },
    // 만료 — 예전 유예 종료 배너 문구를 그대로 쓴다
    EXPIRED: {
        titleKey: 'admin.licenseBanner.graceEnded.title',
        descKey: 'admin.licenseBanner.graceEnded.description',
        sidebar: {
            iconSrc: '/admin/license-banner/icon-ic-license-suspended.svg',
            iconBg: palette.sidebar_error_bg,
            highlightColor: palette.sidebar_error,
        },
    },
    // 인증 실패 (30일 유예 중 포함)
    AUTH_FAILED: {
        titleKey: 'admin.licenseBanner.authFail.title',
        descKey: 'admin.licenseBanner.authFail.description',
        sidebar: {
            iconSrc: '/admin/license-banner/icon-ic-license-expired.svg',
            iconBg: palette.sidebar_error_bg_strong,
            highlightColor: palette.sidebar_error,
        },
    },
    // 만료 예정 (7일 이내)
    EXPIRING: {
        titleKey: 'admin.licenseBanner.expiring.title',
        descKey: 'admin.licenseBanner.expiring.description',
        sidebar: {
            iconSrc: '/admin/license-banner/icon-ic-license-expiring.svg',
            iconBg: palette.sidebar_warning_bg,
            highlightColor: palette.sidebar_warning,
        },
    },
};

const AdminAuthLayout = ({ children, loading }) => {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const router = useRouter();
    const user = useUser();

    const isSuperAdmin = (user?.roles ?? []).includes('SUPER_ADMIN');
    const isPlayground = isApiKeyLogin('ADMIN');
    const isAccountPage = router.pathname === ROUTES.ADMIN_ACCOUNT;

    const [drawerOpen, setDrawerOpen] = useState(false);
    const [menuAnchorEl, setMenuAnchorEl] = useState(null);
    const [isContentsScroll, setIsContentsScroll] = useState(false);

    // 메뉴 스크롤 위치 저장을 위한 상태
    const [menusScrollPosition, setMenusScrollPosition] = useState(0);

    const menusScrollRef = useRef(null);
    const headerRef = useRef(null);
    const [headerHeight, setHeaderHeight] = useState(0);
    const { banner, paidWhiteLabel } = useLicense();
    const { plan } = useBrandingConfig();

    const breadcrumb = findBreadcrumb(
        router.asPath.split(/[?#]/)[0].replace(/\/+$/, '').split('/').filter(Boolean).pop() || ''
    );

    const profileImageSrc = useMemo(() => {
        if (!user?.userSeq) return '';
        return api.getProfileImage(user.userSeq);
    }, [user?.userSeq]);

    const handleMenusScroll = useCallback(() => {
        if (menusScrollRef.current) {
            setMenusScrollPosition(menusScrollRef.current.scrollTop);
        }
    }, []);

    const handleAdminLogout = async () => {
        const res = await api.Post('auth/logout', null, null, { __roleType: 'ADMIN' });
        if (res?.code !== 200) return;

        clearAuth('ADMIN');
        window.location.replace(tenantHref(ROUTES.ADMIN_LOGIN));
    };

    useLayoutEffect(() => {
        const savedScrollPosition = sessionStorage.getItem('menusScrollPosition');
        if (savedScrollPosition && menusScrollRef.current) {
            menusScrollRef.current.scrollTop = parseInt(savedScrollPosition);
        }
    }, [router.pathname]);

    useEffect(() => {
        const savedScrollPosition = sessionStorage.getItem('menusScrollPosition');
        if (savedScrollPosition && menusScrollRef.current) {
            const timer = setTimeout(() => {
                if (menusScrollRef.current) {
                    menusScrollRef.current.scrollTop = parseInt(savedScrollPosition);
                }
            }, 10);
            return () => clearTimeout(timer);
        }
    }, [router.pathname]);

    useEffect(() => {
        sessionStorage.setItem('menusScrollPosition', menusScrollPosition.toString());
    }, [menusScrollPosition]);

    //ui
    useEffect(() => {
        setDrawerOpen(false);
    }, [router]);

    useEffect(() => {
        if (!router.isReady || loading) return;
        const checkScreenSize = () => {
            const container = document.querySelector('#container');
            if (!container) return;
            const isContentsScroll =
                container.clientHeight >= document.querySelector('html').clientHeight;
            setIsContentsScroll(isContentsScroll);
        };

        checkScreenSize();
        window.addEventListener('resize', checkScreenSize);
        return () => window.removeEventListener('resize', checkScreenSize);
    }, [router, loading]);

    useEffect(() => {
        if (!isContentsScroll) {
            document.body.style.overflowY = 'hidden';
        }
    }, [isContentsScroll]);

    // 비밀번호 변경 유도 다이얼로그 오픈
    useEffect(() => {
        if (!getPasswordChangeRequired()) return;
        removePasswordChangeRequired();
        openDialog('initialPasswordChange');
    }, [user?.userId]);

    // 본문 카드 헤더 높이 측정 (슈퍼어드민 배너 포함)
    useLayoutEffect(() => {
        const el = headerRef.current;
        if (!el) return;

        const updateHeight = () => setHeaderHeight(el.offsetHeight);
        updateHeight();

        const resizeObserver = new ResizeObserver(updateHeight);
        resizeObserver.observe(el);
        return () => resizeObserver.disconnect();
    }, []);

    const bannerCfg = banner ? BANNER_CONFIG[banner.status] : null;

    {
        /* userSelect: globals.css에서 body에 none으로 막혀있으나, 관리자 페이지는 텍스트 셀렉 허용 */
    }
    return (
        <Box
            sx={{
                display: 'flex',
                height: '100vh',
                overflow: 'hidden',
                p: `${CANVAS_GAP}px`,
                gap: `${CANVAS_GAP}px`,
                boxSizing: 'border-box',
                backgroundColor: CANVAS_BACKGROUND,
                userSelect: 'text',
                WebkitUserSelect: 'text',
            }}
        >
            <CssBaseline />
            <Drawer
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                variant={drawerOpen ? 'temporary' : 'permanent'}
                sx={{
                    display: {
                        xs: drawerOpen ? 'flex' : 'none',
                        md: 'flex',
                    },
                    zIndex: 1202,
                    width: SIDEBAR_WIDTH,
                    height: '100%',
                    flexShrink: 0,
                    ['& .MuiDrawer-paper']: {
                        width: SIDEBAR_WIDTH,
                        boxSizing: 'border-box',
                        borderRight: 'none',
                        background: palette.sidebar_bg,
                        display: 'flex',
                        flexDirection: 'column',
                        overflowX: 'hidden',
                    },
                    // 데스크톱은 캔버스 안에 떠 있는 카드로 — 기본 fixed 배치를 풀어 여백 안에 둔다
                    ['&.MuiDrawer-docked .MuiDrawer-paper']: {
                        position: { md: 'relative' },
                        height: '100%',
                        borderRadius: { md: CARD_RADIUS },
                    },
                }}
            >
                <Box
                    sx={{
                        pt: '24px',
                        pb: '16px',
                        px: '24px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '2px',
                        cursor: 'pointer',
                    }}
                    onClick={() => {
                        sessionStorage.removeItem('treeExpandedState');
                        window.location.href = tenantHref(ROUTES.ADMIN_ACCOUNTS_USERS);
                    }}
                >
                    <Typography sx={{ fontSize: 18, fontWeight: 500, lineHeight: '24px' }}>
                        <Box component="span" sx={{ color: palette.sidebar_text_primary }}>
                            Thinkfree{' '}
                        </Box>
                        <Box component="span" sx={{ color: palette.sidebar_primary }}>
                            Drive
                        </Box>
                    </Typography>
                    <Typography variant="caption" color={palette.sidebar_text_secondary}>
                        Admin Portal
                    </Typography>
                </Box>
                <Box
                    sx={{
                        width: 230,
                        height: '1px',
                        flexShrink: 0,
                        alignSelf: 'center',
                        backgroundColor: palette.sidebar_divider,
                    }}
                />

                <Box
                    sx={{
                        flex: 1,
                        minHeight: 0,
                        overflowY: 'auto',
                        overflowX: 'hidden',
                        // 스크롤바 유무로 메뉴 너비가 바뀌지 않도록 자리를 항상 확보한다
                        scrollbarGutter: 'stable',
                        // 전역 thumb 색은 어두운 사이드바에서 보이지 않아 비활성 메뉴 텍스트 색으로 덮는다
                        '&::-webkit-scrollbar-thumb, &::-webkit-scrollbar-thumb:hover': {
                            backgroundColor: palette.text_muted,
                        },
                    }}
                    ref={menusScrollRef}
                    onScroll={handleMenusScroll}
                    data-testid="menus-scroll-container"
                >
                    <AdminMenus paidWhiteLabel={paidWhiteLabel} />
                </Box>

                {/** 사이드바 라이선스 배너 */}
                {bannerCfg &&
                    (() => {
                        const { iconSrc, iconBg, highlightColor } = bannerCfg.sidebar;
                        // 문구의 <b> 구간을 상태 색으로 강조
                        const params = {
                            ...banner.params,
                            expireDate: banner.params?.expireDate?.replace(/-/g, '.'),
                            b: chunks => (
                                <span style={{ color: highlightColor, fontWeight: 500 }}>
                                    {chunks}
                                </span>
                            ),
                        };

                        return (
                            <Box
                                sx={{
                                    width: '236px',
                                    mx: '12px',
                                    my: '8px',
                                    px: '16px',
                                    pt: '12px',
                                    pb: '16px',
                                    boxSizing: 'border-box',
                                    backgroundColor: palette.sidebar_card_bg,
                                    borderRadius: '8px',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '16px',
                                }}
                            >
                                <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                                    <Box
                                        sx={{
                                            p: '6px',
                                            borderRadius: '50px',
                                            backgroundColor: iconBg,
                                            flexShrink: 0,
                                            lineHeight: 0,
                                        }}
                                    >
                                        <Image src={iconSrc} width={16} height={16} alt="" />
                                    </Box>
                                    <Box
                                        sx={{
                                            flex: 1,
                                            display: 'flex',
                                            flexDirection: 'column',
                                            justifyContent: 'center',
                                            gap: '4px',
                                        }}
                                    >
                                        <Typography
                                            component="div"
                                            variant="body7_tight"
                                            color={palette.sidebar_text_secondary}
                                        >
                                            {t.rich(bannerCfg.titleKey, params)}
                                        </Typography>
                                        <Typography
                                            component="div"
                                            variant="caption"
                                            color={palette.sidebar_text_hint}
                                        >
                                            {t.rich(bannerCfg.descKey, params)}
                                        </Typography>
                                    </Box>
                                </Box>
                                <Box
                                    onClick={() => router.push(ROUTES.ADMIN_SETTINGS_LICENSE)}
                                    sx={{
                                        height: '28px',
                                        px: '8px',
                                        py: '4px',
                                        boxSizing: 'border-box',
                                        overflow: 'hidden',
                                        borderRadius: '6px',
                                        outline: `1px solid ${palette.sidebar_btn_border}`,
                                        outlineOffset: '-1px',
                                        boxShadow: '0px 1.5px 0px rgba(0, 0, 0, 0.03)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        cursor: 'pointer',
                                        '&:hover': {
                                            backgroundColor: palette.sidebar_btn_bg_hover,
                                        },
                                    }}
                                >
                                    <Typography
                                        variant="body6_tight"
                                        color={palette.sidebar_text_secondary}
                                    >
                                        {t('admin.licenseBanner.detailBtn')}
                                    </Typography>
                                </Box>
                            </Box>
                        );
                    })()}

                {/** 하단 여백 — Powered by 는 8·12 패딩 + 16px 마진, 배너만 있으면 배너 마진 8px 을 더해 24px */}
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'center',
                        flexShrink: 0,
                        px: '12px',
                        ...(isPoweredByPlan(plan)
                            ? { py: '8px', mb: '16px' }
                            : { pb: bannerCfg ? '16px' : '24px' }),
                    }}
                >
                    {isPoweredByPlan(plan) && (
                        <Image
                            src={'/watermark/img-watermark-powered-by.svg'}
                            alt={'Powered by Thinkfree'}
                            width={129}
                            height={18}
                        />
                    )}
                </Box>
            </Drawer>

            {/** 본문 카드 — 헤더(브레드크럼·프로필) + 스크롤되는 본문 */}
            <Box
                sx={{
                    flex: 1,
                    minWidth: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'hidden',
                    backgroundColor: palette.white,
                    borderRadius: CARD_RADIUS,
                }}
            >
                <Box ref={headerRef} sx={{ flexShrink: 0 }}>
                    {/** developers 플레이그라운드 접속 표시 상단 배너 */}
                    {isPlayground && <PlaygroundBanner />}

                    {/** 슈퍼 어드민 접속 표시 상단 배너 */}
                    {isSuperAdmin && !isPlayground && (
                        <Box
                            sx={{
                                width: '100%',
                                py: '3px',
                                backgroundColor: 'rgba(69, 102, 234, 0.15)',
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                gap: '2px',
                            }}
                        >
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                <Image
                                    src="/admin/banner/icon-ic-superadmin-badge.svg"
                                    alt="superadmin"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9_10"
                                    color={palette.text_100}
                                    sx={{ opacity: 0.9 }}
                                >
                                    {t('admin.adminPortal.infoBox.desc')}
                                </Typography>
                            </Box>
                            <Box
                                onClick={() => window.location.assign(ROUTES.SUPERADMIN_ACCOUNT)}
                                sx={{
                                    height: 24,
                                    pl: '8px',
                                    pr: '6px',
                                    borderRadius: '4px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '2px',
                                    cursor: 'pointer',
                                    '&:hover': { backgroundColor: 'rgba(69, 102, 234, 0.1)' },
                                }}
                            >
                                <Typography variant="body9_10" color={palette.text_tab_selected}>
                                    {t('admin.adminPortal.infoBox.portalLink')}
                                </Typography>
                                <Image
                                    src="/admin/banner/icon-ic-link-arrow.svg"
                                    alt="link arrow"
                                    width={16}
                                    height={16}
                                />
                            </Box>
                        </Box>
                    )}

                    <Stack
                        direction="row"
                        alignItems="center"
                        justifyContent="space-between"
                        sx={{
                            px: { xs: '16px', md: '32px' },
                            py: { xs: '8px', md: '12px' },
                            gap: '10px',
                        }}
                    >
                        <Stack direction="row" alignItems="center" sx={{ minWidth: 0 }}>
                            <IconButton
                                onClick={() => setDrawerOpen(true)}
                                sx={{
                                    display: { xs: 'flex', md: 'none' },
                                    p: '8px',
                                    borderRadius: '6px',
                                }}
                            >
                                <Image
                                    src={'/common/icon-ic-menu-admin.svg'}
                                    alt={'menu-icon'}
                                    width={20}
                                    height={20}
                                />
                            </IconButton>

                            {/** 브레드크럼 — 메뉴에 없는 화면(계정 설정 등)은 표시하지 않는다 */}
                            {breadcrumb && (
                                <Stack
                                    direction="row"
                                    alignItems="center"
                                    gap="3px"
                                    sx={{ display: { xs: 'none', md: 'flex' } }}
                                >
                                    <Typography variant="body6_tight" color={palette.text_muted}>
                                        {t(breadcrumb[0])}
                                    </Typography>
                                    <ArrowIcon
                                        direction="right"
                                        sx={{ width: 14, height: 14, color: palette.text_muted }}
                                    />
                                    <Typography variant="body6_tight" color={palette.text_muted}>
                                        {t(breadcrumb[1])}
                                    </Typography>
                                </Stack>
                            )}
                        </Stack>

                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <BaseTooltip title={t('tooltip.myAccount')}>
                                <Stack
                                    direction="row"
                                    alignItems="center"
                                    justifyContent="center"
                                    onClick={e => setMenuAnchorEl(e.currentTarget)}
                                    sx={{
                                        cursor: 'pointer',
                                        height: 32,
                                        px: '8px',
                                        borderRadius: '6px',
                                        gap: '4px',
                                        '&:hover': { backgroundColor: palette.btn_white_bg_hover },
                                    }}
                                >
                                    <Stack direction="row" alignItems="center" gap="8px">
                                        <ProfileImage
                                            src={profileImageSrc}
                                            style={{ cursor: 'pointer' }}
                                            alt="user"
                                            width={20}
                                            height={20}
                                            priority
                                        />
                                        <Typography
                                            variant="body7_tight"
                                            noWrap
                                            sx={{
                                                display: { xs: 'none', md: 'block' },
                                                maxWidth: 90,
                                            }}
                                            color={palette.control_text_hint}
                                        >
                                            {user?.userName}
                                        </Typography>
                                    </Stack>
                                    <ArrowIcon
                                        direction={menuAnchorEl ? 'up' : 'down'}
                                        sx={{
                                            width: 14,
                                            height: 14,
                                            color: palette.control_text_hint,
                                        }}
                                    />
                                </Stack>
                            </BaseTooltip>
                            {/* Fade: 확대 후 mask 아이콘 재렌더링 방지 · keepMounted: 아이콘 미리 로드 · disableAutoFocusItem: 첫 항목 포커스 배경 방지 */}
                            <Menu
                                open={!!menuAnchorEl}
                                TransitionComponent={Fade}
                                keepMounted
                                disableAutoFocusItem
                                TransitionProps={{ onEntering: focusProfileMenuList }}
                                anchorEl={menuAnchorEl}
                                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                                PaperProps={{
                                    sx: {
                                        mt: 2,
                                        minWidth: 135,
                                        borderRadius: '6px',
                                        boxShadow: PROFILE_MENU_SHADOW,
                                        outline: `1px solid ${palette.border_subtle}`,
                                        outlineOffset: '-1px',
                                        backgroundColor: palette.white,
                                    },
                                }}
                                MenuListProps={{ sx: { py: '4px' } }}
                                onClose={() => setMenuAnchorEl(null)}
                            >
                                <MenuItem
                                    selected={isAccountPage}
                                    sx={PROFILE_MENU_ITEM_SX}
                                    onClick={() => {
                                        router.push(ROUTES.ADMIN_ACCOUNT);
                                        setMenuAnchorEl(null);
                                    }}
                                >
                                    <ProfileMenuIcon
                                        src="/admin/profile-menu/icon-ic-account-setting.svg"
                                        color={
                                            isAccountPage
                                                ? palette.menu_item_selected_text
                                                : palette.control_text_hint
                                        }
                                    />
                                    <Typography
                                        variant="body5"
                                        color={
                                            isAccountPage
                                                ? palette.menu_item_selected_text
                                                : palette.control_text
                                        }
                                    >
                                        {t('admin.accountSetting.title')}
                                    </Typography>
                                </MenuItem>
                                {/* API Key 로그인 시 로그아웃 미노출 */}
                                {!isApiKeyLogin('ADMIN') && (
                                    <MenuItem
                                        sx={PROFILE_MENU_ITEM_SX}
                                        onClick={async () => {
                                            openDialog('alert', {
                                                title: t('common.action.logout'),
                                                description: t('logoutDialog.description'),
                                                onClick: handleAdminLogout,
                                            });
                                            setMenuAnchorEl(null);
                                        }}
                                    >
                                        <ProfileMenuIcon
                                            src="/admin/profile-menu/icon-ic-logout.svg"
                                            color={palette.control_text_hint}
                                        />
                                        <Typography variant="body5" color={palette.control_text}>
                                            {t('common.action.logout')}
                                        </Typography>
                                    </MenuItem>
                                )}
                            </Menu>
                        </Box>
                    </Stack>
                </Box>

                <Main
                    sx={{
                        // 뷰포트 높이로 스크롤 영역을 잡는 화면이 본문 위(헤더·캔버스 여백)를 빼 쓰도록 내려준다.
                        '--admin-appbar-h': `${headerHeight + CANVAS_GAP * 2}px`,
                        // position: fixed 요소가 본문 카드 폭에 맞추도록 내려준다 (100% 는 뷰포트 기준)
                        '--admin-main-w': {
                            xs: `calc(100% - ${CANVAS_GAP * 2}px)`,
                            md: `calc(100% - ${SIDEBAR_WIDTH + CANVAS_GAP * 3}px)`,
                        },
                    }}
                >
                    {loading ? null : children}
                </Main>
            </Box>
            {/** 본문 카드가 창 끝에서 CANVAS_GAP 만큼 떨어져 있어 우측·하단 모두 카드 안쪽 기준으로 더한다 */}
            <LicenseWatermark
                offset={{ right: CANVAS_GAP + 48, bottom: CANVAS_GAP + 24 }}
                mobileOffset={{ right: CANVAS_GAP + 16, bottom: CANVAS_GAP + 20 }}
            />
        </Box>
    );
};

export default AdminAuthLayout;

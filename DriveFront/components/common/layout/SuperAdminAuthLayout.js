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
import SuperAdminMenus, { findBreadcrumb } from '@/components/views/SuperAdminMenus';
import {
    clearAuth,
    getPasswordChangeRequired,
    removePasswordChangeRequired,
} from '@/utils/authTokens';
import { ROUTES } from '@/constants/routes';
import { BaseTooltip } from '../tooltip/BaseTooltip';

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

const SuperAdminAuthLayout = ({ children, loading }) => {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const router = useRouter();
    const user = useUser();

    const isAccountPage = router.pathname === ROUTES.SUPERADMIN_ACCOUNT;

    const [drawerOpen, setDrawerOpen] = useState(false);
    const [menuAnchorEl, setMenuAnchorEl] = useState(null);
    const [isContentsScroll, setIsContentsScroll] = useState(false);

    // 메뉴 스크롤 위치 저장을 위한 상태
    const [menusScrollPosition, setMenusScrollPosition] = useState(0);

    const menusScrollRef = useRef(null);
    const headerRef = useRef(null);
    const [headerHeight, setHeaderHeight] = useState(0);

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
        const res = await api.Post('auth/logout', null, null, { __roleType: 'SUPER_ADMIN' });
        if (res?.code !== 200) return;

        clearAuth('SUPER_ADMIN');
        window.location.replace(ROUTES.SUPERADMIN_LOGIN);
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

    // 본문 카드 헤더 높이 측정
    useLayoutEffect(() => {
        const el = headerRef.current;
        if (!el) return;

        const updateHeight = () => setHeaderHeight(el.offsetHeight);
        updateHeight();

        const resizeObserver = new ResizeObserver(updateHeight);
        resizeObserver.observe(el);
        return () => resizeObserver.disconnect();
    }, []);

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
                        window.location.href = ROUTES.SUPERADMIN_ACCOUNTS_TENANTS;
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
                        Super Admin Portal
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
                    <SuperAdminMenus />
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
                <Stack
                    ref={headerRef}
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{
                        flexShrink: 0,
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
                                    router.push(ROUTES.SUPERADMIN_ACCOUNT);
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
                        </Menu>
                    </Box>
                </Stack>

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
        </Box>
    );
};

export default SuperAdminAuthLayout;

import React, { useCallback, useEffect, useLayoutEffect, useRef, useState, useMemo } from 'react';
import {
    AppBar,
    Box,
    CssBaseline,
    Divider,
    Drawer,
    IconButton,
    InputAdornment,
    List,
    ListItemIcon,
    ListItemText,
    Menu,
    MenuItem,
    Stack,
    Toolbar,
    Typography,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import Menus from '@/components/views/Menus';
import StyledButton from '@/components/common/button/StyledButton';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { getFile } from '@/constants/FILES';
import api from '@/utils/api';
import NotificationsPopover from '@/components/views/NotificationsPopover';
import { useDialog } from '@/provider/DialogProvider';
import convertBytesToReadableSize from '@/utils/math';
import { useUser } from '@/context/UserContext';
import typography from '@/theme/typography';
import { createDocument, getOfficeEditUrl } from '@/apis/webOfficeApi';
import useOfficeFeature from '@/hooks/useOfficeFeature';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useDevice from '@/hooks/useDevice';
import useNavigate from '@/hooks/useNavigate';
import useDragDrop from '@/hooks/useDragDrop';
import { registerRecent } from '@/apis/recentApi';
import { getResourceDetail } from '@/apis/resourceApi';
import { useQuota } from '@/provider/QuotaProvider';
import ProfileImage from '@/components/common/input/ProfileImage';
import { clearAuth, isApiKeyLogin } from '@/utils/authTokens';
import checkDevice from '@/utils/etc';
import { getIntellectUrl } from '@/apis/intellectApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import { BaseTooltip } from '../tooltip/BaseTooltip';
import { ROUTES } from '@/constants/routes';
import useLoginConfig from '@/hooks/useLoginConfig';
import useClientConfig from '@/hooks/useClientConfig';
import { tenantHref } from '@/utils/tenantPrefix';
import LicenseWatermark from '@/components/common/LicenseWatermark';
import PlaygroundBanner from '@/components/common/PlaygroundBanner';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { isPoweredByPlan } from '@/utils/license';

// const Main = styled('main')(({ theme }) => ({
//     flexGrow: 1,
//     backgroundColor: '#FAFAFA',
//     minHeight: '100vh',
// }));
const Main = styled('main')(({ theme }) => ({
    flexGrow: 1,
    backgroundColor: '#FAFAFA',
    minHeight: 0,
}));

const MyAppBar = styled(AppBar)(({ theme }) => ({
    zIndex: theme.zIndex.drawer + 1,
    backgroundColor: '#fff',
    color: '#000',
    boxShadow: 'none',
    borderBottom: '1px solid #e0e0e0',
}));

function BellIcon({ isActive, activeSrc, inactiveSrc, alt, width, height }) {
    return (
        <Box sx={{ position: 'relative', width, height, flexShrink: 0 }}>
            <Image
                src={inactiveSrc}
                alt={alt}
                width={width}
                height={height}
                className={`menu-icon ${!isActive ? 'visible' : ''}`}
            />
            <Image
                src={activeSrc}
                alt={`${alt} (active)`}
                width={width}
                height={height}
                className={`menu-icon ${isActive ? 'visible' : ''}`}
            />
        </Box>
    );
}

const AuthLayout = ({ children, loading }) => {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const { quota, refreshQuota } = useQuota();
    const { plan } = useBrandingConfig();
    const usageRatio =
        quota?.totalQuotaByte > 0 ? Math.min(quota.useQuotaByte / quota.totalQuotaByte, 1) : 0; // 용량 사용 비율이 1을 넘지 않도록 제한

    const router = useRouter();
    const user = useUser();
    const { isMobile } = useDevice();
    const navigate = useNavigate();
    const device = checkDevice();
    const isTablet = device?.isTablet;
    const isPhone = device?.isPhone;
    const { showSnackbar } = useSnackbarContext();
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();
    const { shareEnabled } = useClientConfig();
    const { isJwt: isJwtLogin, errorRedirectUrl } = useLoginConfig();
    const [defaultPage, setDefaultPage] = useState(''); // 사용자가 설정한 첫 화면

    useDragDrop({
        onDrop: () => {
            setNewMenuOpen(false);
            setSubMenuOpen(false);
            setAnchorEl(null);
            setNotificationAnchorEl(null);
        },
    });

    const [notificationLoading, setNotificationLoading] = useState(true);
    const [subMenuOpen, setSubMenuOpen] = useState(false);
    const subMenuRef = useRef(null);

    const [newMenuOpen, setNewMenuOpen] = useState(false);

    const [notificationAnchorEl, setNotificationAnchorEl] = useState(null);

    const [drawerOpen, setDrawerOpen] = useState(false);
    const [menuAnchorEl, setMenuAnchorEl] = useState(null);
    const [searchText, setSearchText] = useState('');
    const [anchorEl, setAnchorEl] = useState(null);
    const [notificationList, setNotificationList] = React.useState([]);

    const [cursorId, setCursorId] = useState(null);
    const [hasNext, setHasNext] = useState(false);
    const [cursorSearchCount, setCursorSearchCount] = useState(0);

    const [isContentsScroll, setIsContentsScroll] = useState(false);
    const [isRead, setIsRead] = useState(true);

    // 메뉴 스크롤 위치 저장을 위한 상태
    const [menusScrollPosition, setMenusScrollPosition] = useState(0);

    // 전체 안 읽은 알림 수. 로드된 목록으로 세면 일부 누락되므로, 별도 조회
    const [unreadCount, setUnreadCount] = useState(0);
    const hasUnread = useMemo(() => unreadCount > 0, [unreadCount]);

    const searchRef = useRef(null);
    const fileInputRef = useRef(null);
    const folderInputRef = useRef(null);
    const menusScrollRef = useRef(null);
    const notificationLoadingMoreRef = useRef(false);
    const notificationDataLen = useMemo(() => notificationList.length, [notificationList]);

    const isHome = router.asPath.startsWith('/home');
    const isPlayground = isApiKeyLogin('USER');
    const bannerRef = useRef(null);

    // 상단 배너 높이를 --app-banner-height 로 공유 — 각 화면의 높이·위치 계산에서 제외한다
    useLayoutEffect(() => {
        const root = document.documentElement;
        const el = bannerRef.current;
        if (!el) {
            root.style.setProperty('--app-banner-height', '0px');
            return;
        }

        const updateHeight = () =>
            root.style.setProperty('--app-banner-height', `${el.offsetHeight}px`);
        updateHeight();

        const resizeObserver = new ResizeObserver(updateHeight);
        resizeObserver.observe(el);
        return () => {
            resizeObserver.disconnect();
            root.style.setProperty('--app-banner-height', '0px');
        };
    }, [isPlayground]);

    const profileImageSrc = useMemo(() => {
        if (!user?.userSeq) return '';
        return api.getProfileImage(user.userSeq);
    }, [user?.userSeq]);

    const handleMenuOpen = e => setAnchorEl(e.currentTarget);
    const handleMenuClose = drive => {
        setAnchorEl(null);
    };

    const handleMenusScroll = useCallback(() => {
        if (menusScrollRef.current) {
            setMenusScrollPosition(menusScrollRef.current.scrollTop);
        }
    }, []);

    const createNewDocument = async type => {
        const isNote = type === 'note';
        const popup = openPendingPopup();

        try {
            const pfs = router.query.pfs || '';
            const ss = router.query.ss || '';
            const root = router.asPath.replace(/^\//, '').split(/[/?#]/)[0];

            const res = await createDocument(type, user, pfs, ss ? ss : user?.userSeq);
            if (res?.code !== 201) {
                closePendingPopup(popup);
                showSnackbar(t('newOfficeCreate.fail'), 'error');
                return;
            }

            const documentResourceSeq = res.data.resourceSeq;
            const documentSs = res.data.sharedByUserSeq || res.data.userSeq;
            const newDocument = await getResourceDetail(documentResourceSeq, { ss: documentSs });

            let editUrl = isNote
                ? await getIntellectUrl(newDocument, user)
                : await getOfficeEditUrl(newDocument, user);
            if (!editUrl) throw new Error('Edit URL not generated');

            openDocumentWindow(editUrl, popup);

            const registered = await registerRecent(documentResourceSeq, documentSs);
            if (!registered) {
                showSnackbar(t('recent.register.fail'), 'error');
            }
            const query = pfs ? `?pfs=${pfs}&ss=${ss}` : ``;

            router.push(`/${root}${query}`);
            window.postMessage('drive_reload', '*');
            window.postMessage('quota_reload', '*');
            showSnackbar(
                t(isNote ? 'newIntellectCreate.success' : 'newOfficeCreate.success'),
                'success'
            );
        } catch (error) {
            console.log(error);
            closePendingPopup(popup);
            showSnackbar(t(isNote ? 'common.toast.fail' : 'newOfficeCreate.fail'), 'error');
            return;
        }
    };

    const handleSearch = () => {
        if (searchText === '') return;
        const keyword = encodeURIComponent(searchText);
        navigate(`/search?sk=${keyword}`);
    };

    const getNotificationList = async (isNewSearch = false) => {
        let params = { ps: 20, ir: isRead === true ? '' : false };
        if (!isNewSearch && cursorId) {
            params = {
                ps: 10,
                ci: cursorId,
                ir: isRead === true ? '' : false, // true면 모두(""), 아니라면 안 읽은 알림만(false)
            };
        }

        try {
            if (isNewSearch) setNotificationLoading(true);
            const res = await api.Get('notifications', {
                params,
            });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                const newData = res?.data?.data;

                if (isNewSearch) {
                    setNotificationList(newData);
                } else {
                    setNotificationList(prevList => [...prevList, ...newData]);
                }
            }
        } catch (error) {
            console.error('Failed to fetch notifications:', error);
            return;
        } finally {
            if (isNewSearch) setNotificationLoading(false);
        }
    };
    const reloadNotifications = () => getNotificationList(true); // 알림 목록 새로 조회
    const loadMoreNotifications = () => getNotificationList(false); // 알림 목록 추가 조회

    const getUnreadCount = async () => {
        try {
            const res = await api.Get('notifications/unread-count');
            if (res?.status === 200 && typeof res?.data?.data?.unreadCount === 'number') {
                setUnreadCount(res.data.data.unreadCount);
            }
        } catch (error) {
            console.error('Failed to fetch unread notification count:', error);
        }
    };

    // row 단건 업데이트(읽음/안읽음) - Notifications에서 호출
    const updateNotificationReadLocal = useCallback(
        (seq, nextIsRead) => {
            setNotificationList(prev => {
                // 안읽은 목록만 볼 때(isRead === false), 읽음 처리되면 목록에서 제거
                if (isRead === false && nextIsRead === true) {
                    return prev.filter(n => n.notificationSeq !== seq);
                }
                return prev.map(n =>
                    n.notificationSeq === seq ? { ...n, isRead: nextIsRead } : n
                );
            });
        },
        [isRead]
    );

    const handleLogout = async () => {
        const res = await api.Post('auth/logout', null, null, { __roleType: 'USER' });
        if (res?.code !== 200) return;

        if (isJwtLogin) {
            clearAuth('USER');
            window.location.href = errorRedirectUrl || tenantHref(ROUTES.LOGIN);
        } else {
            clearAuth('USER');
            window.location.replace(tenantHref(ROUTES.LOGIN));
        }
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

    //api
    useEffect(() => {
        setNotificationList([]);
        setCursorId(null);
        setHasNext(false);
        setCursorSearchCount(0);

        reloadNotifications();
    }, [isRead]);

    // 벨 배지용 초기값
    useEffect(() => {
        getUnreadCount();
    }, []);

    // 팝오버 열릴 때 새로 fetch. 목록을 비우면 열 때마다 스켈레톤이 끼어들어 깜빡이므로 지우지 않는다.
    useEffect(() => {
        if (!notificationAnchorEl) return;

        setCursorId(null);
        setHasNext(false);
        setCursorSearchCount(0);
        getNotificationList(true);
        getUnreadCount(); // 새 알림이 생겼을 수 있어 열 때마다 다시 받는다
    }, [notificationAnchorEl]);

    // 알림 목록 추가 조회 - 스크롤이 맨 아래에 도달했을 때, hasNext가 true일 때, cursorId가 있을 때만 실행
    useEffect(() => {
        if (!cursorId) return;
        if (cursorSearchCount <= 0) return; // 다음 목룍 요청인지

        loadMoreNotifications();
    }, [cursorSearchCount]);

    //ui
    useEffect(() => {
        setDrawerOpen(false);
    }, [router]);

    useEffect(() => {
        if (isMobile) {
        } else {
            setSubMenuOpen(false);
            handleMenuClose();
        }
    }, [isMobile]);

    useEffect(() => {
        if (!router.isReady || loading) return;
        const checkScreenSize = () => {
            const container = document.querySelector('#container');

            if (!container) return;

            const isContentsScroll =
                container.clientHeight >= document.querySelector('html').clientHeight;
            if (isContentsScroll) {
                setIsContentsScroll(true);
            } else {
                setIsContentsScroll(false);
            }
        };

        checkScreenSize();

        window.addEventListener('resize', checkScreenSize);

        return () => {
            window.removeEventListener('resize', checkScreenSize);
        };
    }, [router, loading]);

    // useEffect(() => {
    //     if (isContentsScroll && router.pathname.includes('/home')) {
    //         document.body.style.overflowY = 'scroll';
    //     } else if (!isContentsScroll) {
    //         document.body.style.overflowY = 'hidden';
    //     }
    // }, [isContentsScroll]);

    useEffect(() => {
        if (router.query.sk) {
            document.querySelector('#search-input').focus();
            setSearchText(router.query.sk);
        }
    }, [router.query.sk]);

    useEffect(() => {
        if (user) {
            setDefaultPage(user?.defaultLandingPage === 'my-drive' ? '/drive' : '/home');
        }
    }, [user]);

    // 플로팅 메뉴 외부 클릭 시 닫기
    useEffect(() => {
        const handleClickOutside = event => {
            if (subMenuRef.current && !subMenuRef.current.contains(event.target)) {
                setSubMenuOpen(false);
            }
        };

        if (subMenuOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            document.addEventListener('touchstart', handleClickOutside);
        }

        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('touchstart', handleClickOutside);
        };
    }, [subMenuOpen]);

    return (
        <Box sx={{ display: 'flex' }}>
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
                    width: 260,
                    flexShrink: 0,
                    ['& .MuiDrawer-paper']: {
                        width: 260,
                        boxSizing: 'border-box',
                        borderRight: `1px solid ${palette.divider_12}`,
                        background: '#fbfbfb',
                    },
                }}
            >
                <Toolbar
                    sx={{ justifyContent: 'center', cursor: 'pointer', minHeight: 0, py: '38px' }}
                    onClick={() => {
                        sessionStorage.removeItem('treeExpandedState');
                        window.location.href = tenantHref(`${defaultPage}`);
                    }}
                >
                    <BaseTooltip title={t('tooltip.drive')}>
                        <Image src={api.getBasicLogoImage()} alt={'logo'} width={75} height={20} />
                    </BaseTooltip>
                </Toolbar>
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        flex: 1,
                        minHeight: 0,
                        overflow: 'hidden',
                    }}
                >
                    <Box sx={{ px: 3, position: 'relative', flexShrink: 0 }}>
                        <StyledButton
                            startIcon={
                                <Image
                                    src={'/common/icon-icon-16-plus.svg'}
                                    alt={'plus'}
                                    width={12}
                                    height={12}
                                />
                            }
                            fullWidth
                            color={'btn_point_bg'}
                            variant="contained"
                            onClick={handleMenuOpen}
                            sx={{
                                width: '100%',
                                height: 36,
                                mb: '18px',
                                borderRadius: '6px',
                                padding: '6px 103px 9px 87px',
                            }}
                        >
                            <Typography variant={'body4'} color={'white'} sx={{}}>
                                New
                            </Typography>
                        </StyledButton>
                        <Menu
                            anchorEl={anchorEl}
                            open={Boolean(anchorEl)}
                            onClose={() => handleMenuClose()}
                            PaperProps={{
                                sx: {
                                    border: `1px solid ${palette.control_border_normal}`,
                                    left: '12px !important',
                                    mt: 0.5,
                                    minWidth: 235,
                                    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                },
                            }}
                        >
                            <MenuItem
                                sx={{
                                    gap: 2.5,
                                    px: 3,
                                }}
                                onClick={() => {
                                    setNewMenuOpen(!newMenuOpen);
                                    setAnchorEl(null);
                                    openDialog('folderCreate', {
                                        pfs: router.query.pfs || '',
                                        ss: router.query.ss || '',
                                    });
                                }}
                            >
                                <Image
                                    src={getFile('folder').src}
                                    alt={'folder'}
                                    width={24}
                                    height={24}
                                />
                                <ListItemText
                                    primary={t('uploadDialog.newFolder')}
                                    sx={{ color: palette.text_80 }}
                                />
                            </MenuItem>
                            <Divider
                                sx={{
                                    my: 1,
                                    borderColor: palette.divider_8,
                                    borderBottomWidth: 0.5,
                                }}
                            />
                            {officeFeature.createMenus.map(menu => (
                                <MenuItem
                                    key={menu.type}
                                    sx={{
                                        gap: 2.5,
                                        px: 3,
                                    }}
                                    onClick={async () => {
                                        setAnchorEl(null);
                                        await createNewDocument(menu.type);
                                    }}
                                >
                                    <Image
                                        src={getFile(menu.icon).src}
                                        alt={menu.type}
                                        width={24}
                                        height={24}
                                    />
                                    <ListItemText
                                        primary={t(menu.label)}
                                        sx={{ color: palette.text_80 }}
                                    />
                                </MenuItem>
                            ))}
                            {intellectEnabled && (
                                <MenuItem
                                    sx={{
                                        gap: 2.5,
                                        px: 3,
                                    }}
                                    onClick={async () => {
                                        setAnchorEl(null);
                                        await createNewDocument('note');
                                    }}
                                >
                                    <Image
                                        src={getFile('intellect').src}
                                        alt={'folder'}
                                        width={24}
                                        height={24}
                                    />
                                    <Stack direction="row" alignItems="center" gap={1}>
                                        <Typography
                                            variant={typography.body8}
                                            color={palette.text_80}
                                        >
                                            {t('uploadDialog.newIntellect')}
                                        </Typography>
                                        <Typography
                                            variant="caption"
                                            sx={{
                                                color: palette.text_80,
                                                position: 'relative',
                                                top: '1px',
                                            }}
                                        >
                                            {t('label.beta')}
                                        </Typography>
                                    </Stack>
                                </MenuItem>
                            )}
                            {(officeFeature.canCreate || intellectEnabled) && (
                                <Divider
                                    sx={{
                                        my: 1,
                                        borderColor: palette.divider_8,
                                        borderBottomWidth: 0.5,
                                    }}
                                />
                            )}
                            <MenuItem
                                sx={{
                                    gap: 2.5,
                                    px: 3,
                                }}
                                onClick={() => {
                                    fileInputRef.current?.click();
                                }}
                            >
                                <input
                                    type="file"
                                    ref={fileInputRef}
                                    style={{ display: 'none' }}
                                    multiple
                                    onChange={e => {
                                        const files = Array.from(e.target.files);
                                        if (files.length === 0) return;

                                        const uploadItems = files.map(file => ({
                                            name: file.name,
                                            type: 'file',
                                            file: file,
                                        }));

                                        openDialog('uploadFiles', {
                                            uploadItems: uploadItems,
                                            reload: () => window.postMessage('drive_reload', '*'),
                                        });

                                        e.target.value = '';
                                        setAnchorEl(null);
                                    }}
                                />
                                <ListItemIcon sx={{ minWidth: '24px !important' }} />
                                <ListItemText
                                    primary={t('uploadDialog.fileUpload')}
                                    sx={{ color: palette.text_80 }}
                                />
                            </MenuItem>
                            {!isPhone && !isTablet && (
                                <MenuItem
                                    sx={{
                                        gap: 2.5,
                                        px: 3,
                                    }}
                                    onClick={() => {
                                        folderInputRef.current?.click();
                                    }}
                                >
                                    <ListItemIcon sx={{ minWidth: '24px !important' }} />
                                    <ListItemText
                                        primary={t('uploadDialog.folderUpload')}
                                        sx={{ color: palette.text_80 }}
                                    />
                                </MenuItem>
                            )}
                            <input
                                type="file"
                                ref={folderInputRef}
                                style={{ display: 'none' }}
                                onChange={e => {
                                    const files = Array.from(e.target.files);
                                    if (files.length === 0) return;

                                    const rootNodes = [];

                                    files.forEach(file => {
                                        const path = file.webkitRelativePath || file.name;
                                        const parts = path.split('/').filter(p => p);

                                        const fileName = parts[parts.length - 1];
                                        const folderParts = parts.slice(0, -1);

                                        let currentChildren = rootNodes;

                                        folderParts.forEach(part => {
                                            let folderNode = currentChildren.find(
                                                child =>
                                                    child.name === part && child.type === 'folder'
                                            );

                                            if (!folderNode) {
                                                folderNode = {
                                                    name: part,
                                                    type: 'folder',
                                                    children: [],
                                                    file: null,
                                                };
                                                currentChildren.push(folderNode);
                                            }
                                            currentChildren = folderNode.children;
                                        });

                                        if (fileName) {
                                            currentChildren.push({
                                                name: fileName,
                                                type: 'file',
                                                file: file,
                                            });
                                        }
                                    });

                                    openDialog('uploadFiles', {
                                        uploadItems: rootNodes,
                                        reload: () => window.postMessage('drive_reload', '*'),
                                    });

                                    e.target.value = '';
                                    setAnchorEl(null);
                                }}
                                webkitdirectory=""
                                directory=""
                            />
                        </Menu>
                    </Box>
                    <Box
                        sx={{
                            minHeight: 0,
                            overflowY: 'auto',
                        }}
                        ref={menusScrollRef}
                        onScroll={handleMenusScroll}
                        data-testid="menus-scroll-container"
                    >
                        <Menus shareEnabled={shareEnabled} />
                    </Box>
                    <Stack px={'16px'} pb={'24px'} mt={'auto'} flexShrink={0}>
                        <Divider
                            sx={{ my: 1, borderColor: palette.divider_12, borderBottomWidth: 0.5 }}
                        />

                        {quota && (
                            <Stack gap={'8px'} px={'4px'} py={'16px'}>
                                <Typography variant="body9">{t('common.label.storage')}</Typography>
                                <Box
                                    sx={{
                                        width: '100%',
                                        height: 8,
                                        background: palette.divider_8,
                                        borderRadius: 2,
                                        border: `1px solid #3a3d4933`,
                                        overflow: 'hidden',
                                    }}
                                >
                                    <Box
                                        sx={{
                                            width: `${usageRatio * 100}%`,
                                            height: 8,
                                            background: 'black',
                                            borderRadius:
                                                quota?.useQuotaByte >= quota?.totalQuotaByte
                                                    ? 0
                                                    : '8px 0px 0px 8px',
                                        }}
                                    />
                                </Box>
                                <Typography variant="body8" color={'text_100'} component={'span'}>
                                    {convertBytesToReadableSize(quota?.useQuotaByte)}
                                    <Typography
                                        variant="body9"
                                        color={'text_60'}
                                        component={'span'}
                                    >
                                        &nbsp;of&nbsp;
                                    </Typography>
                                    <Typography
                                        variant="body8"
                                        color={'text_80'}
                                        component={'span'}
                                    >
                                        {convertBytesToReadableSize(quota?.totalQuotaByte)}
                                    </Typography>
                                    <Typography
                                        variant="body9"
                                        color={'text_80'}
                                        component={'span'}
                                    >
                                        &nbsp;used
                                    </Typography>
                                </Typography>
                            </Stack>
                        )}
                        {isPoweredByPlan(plan) && (
                            <Box
                                sx={{
                                    display: 'flex',
                                    justifyContent: 'center',
                                    mt: '24px',
                                }}
                            >
                                <Image
                                    src={'/watermark/img-watermark-powered-by.svg'}
                                    alt={'Powered by Thinkfree'}
                                    width={129}
                                    height={18}
                                />
                            </Box>
                        )}
                    </Stack>
                </Box>
            </Drawer>

            <Box sx={{ width: '100%' }}>
                {/** 상단 헤더 */}
                <MyAppBar
                    position="fixed"
                    sx={{
                        left: { md: '240px', xs: 0 },
                        width: { md: 'calc(100% - 240px)', xs: '100%' },
                        zIndex: theme => theme.zIndex.drawer + 1,
                        borderBottom: { xs: 'none', md: `1px solid ${palette.divider_8}` },
                    }}
                >
                    {/** developers 플레이그라운드 접속 표시 상단 배너 */}
                    {isPlayground && <PlaygroundBanner ref={bannerRef} />}

                    <Toolbar
                        sx={{
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            display: 'flex',
                            paddingRight: `16px !important`,
                            maxWidth: '100%',
                            height: { xs: 56, md: 64 },
                            minHeight: 0,
                            gap: 4,
                            p: '0 16px',
                        }}
                    >
                        <Box sx={{ width: { md: 200 } }}>
                            <IconButton
                                onClick={() => setDrawerOpen(true)}
                                sx={{
                                    display: {
                                        xs: 'flex',
                                        md: 'none',
                                    },
                                }}
                            >
                                <Image
                                    src={'/common/icon-ic-menu.svg'}
                                    alt={'menu-icon'}
                                    width={24}
                                    height={24}
                                />
                            </IconButton>
                        </Box>

                        <Box
                            ref={searchRef}
                            sx={{ position: 'relative', maxWidth: 470, width: '100%' }}
                        >
                            <StyledTextField
                                placeholder={t('header.searchInput')}
                                size="small"
                                variant="outlined"
                                sx={{
                                    width: '100%',
                                    backgroundColor: '#3a3d490a',
                                    borderRadius: '20px !important',
                                    '& .MuiOutlinedInput-root': {
                                        borderRadius: '20px !important',
                                        color: palette.text_80,
                                        ...typography.body5,
                                        '& .MuiOutlinedInput-notchedOutline': {
                                            border: `1px solid ${palette.btn_border}`,
                                        },
                                        '&:hover .MuiOutlinedInput-notchedOutline': {
                                            border: `1px solid ${palette.btn_border}`,
                                        },
                                        '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                                            border: `1px solid ${palette.btn_border}`,
                                        },
                                    },
                                    '& .MuiInputBase-input': {
                                        padding: '0 !important',
                                        fontSize: '14px',
                                    },
                                }}
                                height={32}
                                value={searchText}
                                onKeyDown={e => {
                                    if (e.key === 'Enter') {
                                        e.preventDefault();
                                        handleSearch(e);
                                    }
                                }}
                                onChange={e => setSearchText(e.target.value)}
                                InputProps={{
                                    id: 'search-input',
                                    startAdornment: (
                                        <InputAdornment
                                            position="start"
                                            sx={{ cursor: 'pointer' }}
                                            onClick={handleSearch}
                                        >
                                            <BaseTooltip title={t('tooltip.search')}>
                                                <Image
                                                    src={'/common/icon-ic-search.svg'}
                                                    alt={'search'}
                                                    width={20}
                                                    height={20}
                                                />
                                            </BaseTooltip>
                                        </InputAdornment>
                                    ),
                                    endAdornment: searchText && (
                                        <InputAdornment
                                            position="end"
                                            sx={{ opacity: 0.36, cursor: 'pointer' }}
                                            onClick={() => setSearchText('')}
                                        >
                                            <BaseTooltip title={t('tooltip.clearSearch')}>
                                                <Image
                                                    src={'/common/icon-ic-delete.svg'}
                                                    alt={'delete'}
                                                    width={13}
                                                    height={13}
                                                />
                                            </BaseTooltip>
                                        </InputAdornment>
                                    ),
                                }}
                            />
                        </Box>

                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <BaseTooltip title={t('tooltip.notifications')}>
                                <IconButton
                                    sx={{ display: { xs: 'none', md: 'flex' } }}
                                    onClick={e => setNotificationAnchorEl(e.currentTarget)}
                                >
                                    <BellIcon
                                        isActive={hasUnread}
                                        activeSrc="/common/icon-ic-bell-badge-outline.svg"
                                        inactiveSrc="/common/icon-ic-bell-outline.svg"
                                        alt="bell"
                                        width={24}
                                        height={24}
                                    />
                                </IconButton>
                            </BaseTooltip>
                            <NotificationsPopover
                                loadingMoreRef={notificationLoadingMoreRef}
                                dataLen={notificationDataLen}
                                notificationList={notificationList}
                                reload={reloadNotifications}
                                onToggleRead={updateNotificationReadLocal}
                                unreadCount={unreadCount}
                                onUnreadCountChange={setUnreadCount}
                                refreshUnreadCount={getUnreadCount}
                                loading={notificationLoading}
                                isRead={isRead}
                                setIsRead={setIsRead}
                                hasNext={hasNext}
                                setCursorSearchCount={setCursorSearchCount}
                                anchorEl={notificationAnchorEl}
                                setAnchorEl={setNotificationAnchorEl}
                            />
                            <BaseTooltip title={t('common.label.setting')}>
                                <IconButton
                                    sx={{ display: { xs: 'none', md: 'flex' } }}
                                    onClick={() => {
                                        window.location.href = tenantHref(ROUTES.SETTING_GENERAL);
                                    }}
                                >
                                    <Image
                                        src={'/common/icon-ic-setting-outline.svg'}
                                        alt="bell"
                                        width={24}
                                        height={24}
                                    />
                                </IconButton>
                            </BaseTooltip>
                            <BaseTooltip title={t('tooltip.myAccount')}>
                                <Stack
                                    direction="row"
                                    alignItems="center"
                                    justifyContent="center"
                                    onClick={e => setMenuAnchorEl(e.currentTarget)}
                                    ml={{
                                        md: 4,
                                        xs: 0,
                                    }}
                                    gap={2}
                                    sx={{
                                        cursor: 'pointer',
                                        boxShadow: '0 0 4px 0 #3a3d492e',
                                        padding: {
                                            xs: 1,
                                            md: '4px 14px 4px 4px',
                                        },
                                        borderRadius: '21px',
                                    }}
                                >
                                    <ProfileImage
                                        src={profileImageSrc}
                                        style={{ cursor: 'pointer' }}
                                        alt="user"
                                        width={24}
                                        height={24}
                                        priority
                                    />
                                    <Typography
                                        sx={{ display: { xs: 'none', md: 'flex' } }}
                                        variant={'body8'}
                                        color={'text_100'}
                                    >
                                        {user?.userName}
                                    </Typography>
                                </Stack>
                            </BaseTooltip>
                            <Menu
                                open={!!menuAnchorEl}
                                anchorEl={menuAnchorEl}
                                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                                PaperProps={{
                                    // elevation: 2,
                                    sx: {
                                        mt: 2,
                                        minWidth: 133,
                                        borderRadius: 1,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                        border: `1px solid ${palette.control_border_normal}`,
                                        backgroundColor: palette.btn_white_bg_normal,
                                    },
                                }}
                                onClose={() => setMenuAnchorEl(null)}
                            >
                                <MenuItem
                                    sx={{ py: 0, px: 2.5 }}
                                    onClick={() => {
                                        window.location.href = tenantHref(ROUTES.SETTING_GENERAL);
                                        setMenuAnchorEl(null);
                                    }}
                                >
                                    <ListItemIcon sx={{ minWidth: '28px !important' }}>
                                        <Image
                                            src={'/common/icon-ic-setting-outline.svg'}
                                            alt="bell"
                                            width={20}
                                            height={20}
                                        />
                                    </ListItemIcon>
                                    <ListItemText
                                        primary={
                                            <Typography variant={'body5'} color={'text_80'}>
                                                {t('common.label.setting')}
                                            </Typography>
                                        }
                                    />
                                </MenuItem>
                                {/* API Key 로그인 시 로그아웃 미노출 */}
                                {!isApiKeyLogin('USER') && (
                                    <MenuItem
                                        sx={{ py: 0, px: 2.5 }}
                                        onClick={async () => {
                                            openDialog('alert', {
                                                title: t('common.action.logout'),
                                                description: t('logoutDialog.description'),
                                                onClick: handleLogout,
                                            });
                                            setMenuAnchorEl(null);
                                        }}
                                    >
                                        <ListItemIcon sx={{ minWidth: '28px !important' }} />
                                        <ListItemText
                                            primary={
                                                <Typography variant={'body5'} color={'text_80'}>
                                                    {t('common.action.logout')}
                                                </Typography>
                                            }
                                        />
                                    </MenuItem>
                                )}
                            </Menu>
                        </Box>
                    </Toolbar>
                </MyAppBar>

                <Main
                    sx={{
                        p: 0,
                        background: '#fff',
                        minHeight: 0,
                        flexGrow: 1,
                        pt: !isHome
                            ? {
                                  xs: 'calc(56px + var(--app-banner-height, 0px))',
                                  md: 'calc(64px + var(--app-banner-height, 0px))',
                              }
                            : {},
                        maxWidth: {
                            md: 'calc(100vw - 259px)',
                            xs: '100%',
                        },
                        display: 'flex',
                        flexDirection: 'column',
                    }}
                >
                    {loading ? null : children}
                    {subMenuOpen ? (
                        <List
                            ref={subMenuRef}
                            sx={{
                                position: 'fixed',
                                bottom: 20,
                                right: 20,
                                boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                border: `1px solid ${palette.control_border_normal}`,
                                borderRadius: 1,
                                width: '100%',
                                maxWidth: 236,
                                bgcolor: '#fff',
                                display: { xs: 'flex', md: 'none' },
                                flexDirection: 'column',
                            }}
                            component="nav"
                            aria-labelledby="nested-list-subheader"
                        >
                            <MenuItem
                                sx={{
                                    gap: 2.5,
                                    px: 3,
                                }}
                                onClick={() => {
                                    setNewMenuOpen(!newMenuOpen);
                                    openDialog('folderCreate', {
                                        pfs: router.query.pfs || '',
                                        ss: router.query.ss || '',
                                    });
                                    setSubMenuOpen(!subMenuOpen);
                                }}
                            >
                                <Image
                                    src={getFile('folder').src}
                                    alt={'folder'}
                                    width={24}
                                    height={24}
                                />
                                <ListItemText
                                    primary={t('uploadDialog.newFolder')}
                                    sx={{ color: palette.text_80 }}
                                />
                            </MenuItem>
                            <Divider
                                sx={{
                                    my: 1,
                                    borderColor: palette.divider_8,
                                    borderBottomWidth: 0.5,
                                }}
                            />
                            {officeFeature.createMenus.map(menu => (
                                <MenuItem
                                    key={menu.type}
                                    sx={{
                                        gap: 2.5,
                                        px: 3,
                                    }}
                                    onClick={async () => {
                                        setNewMenuOpen(!newMenuOpen);
                                        await createNewDocument(menu.type);
                                        setSubMenuOpen(!subMenuOpen);
                                    }}
                                >
                                    <Image
                                        src={getFile(menu.icon).src}
                                        alt={menu.type}
                                        width={24}
                                        height={24}
                                    />
                                    <ListItemText
                                        primary={t(menu.label)}
                                        sx={{ color: palette.text_80 }}
                                    />
                                </MenuItem>
                            ))}
                            {intellectEnabled && (
                                <MenuItem
                                    sx={{
                                        gap: 2.5,
                                        px: 3,
                                    }}
                                    onClick={async () => {
                                        setAnchorEl(null);
                                        await createNewDocument('note');
                                    }}
                                >
                                    <Image
                                        src={getFile('intellect').src}
                                        alt={'folder'}
                                        width={24}
                                        height={24}
                                    />
                                    <ListItemText
                                        primary={
                                            <Stack direction="row" alignItems="center" gap={1}>
                                                <Typography
                                                    variant={typography.body8}
                                                    color={palette.text_80}
                                                >
                                                    {t('uploadDialog.newIntellect')}
                                                </Typography>
                                                <Typography
                                                    variant="caption"
                                                    sx={{
                                                        color: palette.text_60,
                                                        position: 'relative',
                                                        top: '1px',
                                                    }}
                                                >
                                                    {t('label.beta')}
                                                </Typography>
                                            </Stack>
                                        }
                                    />
                                </MenuItem>
                            )}
                            {(officeFeature.canCreate || intellectEnabled) && (
                                <Divider
                                    sx={{
                                        my: 1,
                                        borderColor: palette.divider_8,
                                        borderBottomWidth: 0.5,
                                    }}
                                />
                            )}
                            <MenuItem
                                sx={{
                                    gap: 2.5,
                                    px: 3,
                                }}
                                onClick={() => {
                                    fileInputRef.current?.click();
                                }}
                            >
                                <input
                                    type="file"
                                    ref={fileInputRef}
                                    style={{ display: 'none' }}
                                    multiple
                                    onChange={e => {
                                        const files = Array.from(e.target.files);
                                        if (files.length === 0) return;

                                        const uploadItems = files.map(file => ({
                                            name: file.name,
                                            type: 'file',
                                            file: file,
                                        }));

                                        openDialog('uploadFiles', {
                                            uploadItems: uploadItems,
                                            reload: () => window.postMessage('drive_reload', '*'),
                                        });

                                        e.target.value = '';
                                        setSubMenuOpen(!subMenuOpen);
                                    }}
                                />
                                <ListItemIcon sx={{ minWidth: '24px !important' }} />
                                <ListItemText
                                    primary={t('uploadDialog.fileUpload')}
                                    sx={{ color: palette.text_80 }}
                                />
                            </MenuItem>
                            {!isPhone && !isTablet && (
                                <MenuItem
                                    sx={{
                                        gap: 2.5,
                                        px: 3,
                                    }}
                                    onClick={() => {
                                        folderInputRef.current?.click();
                                    }}
                                >
                                    <ListItemIcon sx={{ minWidth: '24px !important' }} />
                                    <ListItemText
                                        primary={t('uploadDialog.folderUpload')}
                                        sx={{ color: palette.text_80 }}
                                    />
                                </MenuItem>
                            )}
                            <input
                                type="file"
                                ref={folderInputRef}
                                style={{ display: 'none' }}
                                onChange={e => {
                                    const files = Array.from(e.target.files);
                                    if (files.length === 0) return;

                                    const rootNodes = [];

                                    files.forEach(file => {
                                        const path = file.webkitRelativePath || file.name;
                                        const parts = path.split('/').filter(p => p);

                                        const fileName = parts[parts.length - 1];
                                        const folderParts = parts.slice(0, -1);

                                        let currentChildren = rootNodes;

                                        folderParts.forEach(part => {
                                            let folderNode = currentChildren.find(
                                                child =>
                                                    child.name === part && child.type === 'folder'
                                            );

                                            if (!folderNode) {
                                                folderNode = {
                                                    name: part,
                                                    type: 'folder',
                                                    children: [],
                                                    file: null,
                                                };
                                                currentChildren.push(folderNode);
                                            }
                                            currentChildren = folderNode.children;
                                        });

                                        if (fileName) {
                                            currentChildren.push({
                                                name: fileName,
                                                type: 'file',
                                                file: file,
                                            });
                                        }
                                    });

                                    openDialog('uploadFiles', {
                                        uploadItems: rootNodes,
                                        reload: () => window.postMessage('drive_reload', '*'),
                                    });

                                    e.target.value = '';
                                    setSubMenuOpen(!subMenuOpen);
                                }}
                                webkitdirectory=""
                                directory=""
                            />
                            <Stack alignItems={'flex-end'} sx={{ pr: 3 }}>
                                <IconButton
                                    sx={{ p: 0 }}
                                    color={'primary'}
                                    onClick={() => {
                                        setSubMenuOpen(!subMenuOpen);
                                    }}
                                >
                                    <Image
                                        src={'/common/icon-ic-close.svg'}
                                        alt={'close'}
                                        width={24}
                                        height={24}
                                    />
                                </IconButton>
                            </Stack>
                        </List>
                    ) : (
                        <IconButton
                            color="primary"
                            onClick={() => setSubMenuOpen(!subMenuOpen)}
                            sx={{
                                display: {
                                    md: 'none',
                                    xs: 'flex',
                                },
                                position: 'fixed',
                                bottom: 20,
                                right: 20,
                                backgroundColor: 'black !important',
                                '&:hover': {
                                    backgroundColor: 'black !important',
                                    boxShadow: 'none !important',
                                },
                            }}
                        >
                            <svg
                                width="24"
                                height="24"
                                viewBox="0 0 24 24"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                            >
                                <path
                                    d="M12 3.75a.75.75 0 0 1 .75.75v6.75h6.75a.75.75 0 0 1 0 1.5h-6.75v6.75a.75.75 0 0 1-1.5 0v-6.75H4.5a.75.75 0 0 1 0-1.5h6.75V4.5a.75.75 0 0 1 .75-.75z"
                                    fill="#fff"
                                />
                            </svg>
                        </IconButton>
                    )}
                </Main>
            </Box>
            {/* md 미만은 플로팅 버튼(right 20, 너비 40) 좌측 8px */}
            <LicenseWatermark mobileOffset={{ right: 68, bottom: 20, left: 16 }} />
        </Box>
    );
};

export default AuthLayout;

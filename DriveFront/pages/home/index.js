'use client';

import Image from 'next/image';
import Box from '@mui/material/Box';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useEffect, useState, useRef, useMemo } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import AddOutlinedIcon from '@mui/icons-material/AddOutlined';
import { useUser } from '@/context/UserContext';
import convertBytesToReadableSize from '@/utils/math';
import { useTranslations } from 'next-intl';
import HomeTable from '@/components/views/home/HomeTable';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useDialog } from '@/provider/DialogProvider';
import dayjs from 'dayjs';
import Head from 'next/head';
import DriveDrawer from '@/components/views/drive/DriveDrawer';
import useWindowMessage from '@/hooks/useWindowMessage';
import { createDocument, getOfficeEditUrl } from '@/apis/webOfficeApi';
import useOfficeFeature, { OFFICE_CREATE_TYPES } from '@/hooks/useOfficeFeature';
import { getShareSeq } from '@/utils/resourceUtils';
import { registerRecent } from '@/apis/recentApi';
import { getResourceDetail } from '@/apis/resourceApi';
import { getIntellectUrl } from '@/apis/intellectApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import DriveActionIconMenuBar from '@/components/common/TopActionIconMenuBar';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import useClientConfig from '@/hooks/useClientConfig';
import { ROUTES } from '@/constants/routes';
import useSyncedSelection from '@/hooks/useSyncedSelection';
import useStarredToggle from '@/hooks/useStarredToggle';

const homeMenu = [
    {
        text: 'home.quickMenu.newDocs',
        backgroundColor: '#8d9ef233',
        src: '/home/img-img-home-new-doc.svg',
        alt: 'doc',
        value: 'word',
    },
    {
        text: 'home.quickMenu.newExcel',
        backgroundColor: '#69d7a233',
        src: '/home/img-img-home-new-xls.svg',
        alt: 'sheet',
        value: 'excel',
    },
    {
        text: 'home.quickMenu.newPpt',
        backgroundColor: '#e2b46a33',
        src: '/home/img-img-home-ptx.svg',
        alt: 'slide',
        value: 'ppt',
    },
    {
        text: 'home.quickMenu.newIntellect',
        backgroundColor: '#e6e6e699',
        src: '/home/img-img-home-intellect.svg',
        alt: 'slide',
        value: 'note',
        isBeta: true,
    },
    {
        text: 'common.label.myDrive',
        backgroundColor: '#e6e6e699',
        src: '/home/img-img-home-drive.svg',
        alt: 'drive',
        route: '/drive',
    },
    {
        text: 'home.quickMenu.shared',
        backgroundColor: '#e6e6e699',
        src: '/home/img-img-home-share.svg',
        alt: 'shared',
        value: 'shared',
        route: '/shared',
    },
];

export default function Home() {
    const user = useUser();
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const hour = dayjs().hour();
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();
    const { tabTitle, serviceName } = useBrandingConfig();
    const { shareEnabled } = useClientConfig();

    const recentTableRef = useRef(null);
    const starredTableRef = useRef(null);
    const recentActionMenuAreaRef = useRef(null);
    const starredActionMenuAreaRef = useRef(null);
    const quickMenuAreaRef = useRef(null);
    const drawerRef = useRef(null);
    const homeInfoAreaRef = useRef(null);
    const recentTitleAreaRef = useRef(null);
    const starredTitleAreaRef = useRef(null);
    const [recentCheckItems, setRecentCheckItems] = useState([]);
    const [starredCheckItems, setStarredCheckItems] = useState([]);
    const checkItems = useMemo(
        () => [...recentCheckItems, ...starredCheckItems],
        [recentCheckItems, starredCheckItems]
    );
    const [drawerOpen, setDrawerOpen] = useState(false);

    const [recentList, setRecentList] = useState([]);
    const [starredList, setStarredList] = useState([]);
    const [pinnedList, setPinnedList] = useState([]);

    // 홈은 별 추가 시 중요문서함 목록 구성까지 바뀌어야 하므로 전체 재조회를 유지한다.
    // 대신 재조회 후 선택 항목을 새 목록의 최신 객체로 다시 맞춰 상태 불일치를 막는다.
    useSyncedSelection(recentList, setRecentCheckItems);
    useSyncedSelection(starredList, setStarredCheckItems);

    const lastOpenedRsRef = useRef(null); // 공유 링크 미리보기 중복 오픈 방지

    const [isReady, setIsReady] = useState(false); // 화면 그릴 준비 완료 여부
    const [showUI, setShowUI] = useState(false); // 화면 로딩 가능 여부 (스크롤 위치 조정까지 완료된 상태)

    const filteredMenu = homeMenu.filter(item => {
        if (!officeFeature.canCreate && OFFICE_CREATE_TYPES.includes(item.value)) return false;
        if (!intellectEnabled && item.value === 'note') return false;
        if (!shareEnabled && item.value === 'shared') return false;
        return true;
    });
    const areAllRecentItemsStarred = recentCheckItems.every(item => item?.isStarred);

    const handleDeleteDrive = async () => {
        try {
            for (let item of checkItems) {
                const res =
                    item.resourceType === 'FILE'
                        ? await api.Patch(`files/${item.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          })
                        : await api.Patch(`folders/${item.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          });
                if (res?.status === 200) {
                    showSnackbar(t('moveToTrash.success'), 'success');
                } else if (res?.status === 423) {
                    showSnackbar(t('lock.locked'), 'error');
                } else {
                    showSnackbar(t('moveToTrash.fail'), 'error');
                }
            }
        } catch (e) {
            showSnackbar(t('moveToTrash.fail'), 'error');
        } finally {
            setRecentCheckItems([]);
            setStarredCheckItems([]);
            getListItems(true);
            refreshResources();
        }
    };

    // 다른 상단 액션 아이콘과 동일하게 선택 상태를 유지한다.
    // 홈은 중요문서함 목록 구성까지 바뀌어야 해서 전체 재조회를 유지하고,
    // 재조회 후 선택 항목은 useSyncedSelection이 새 목록의 최신 객체로 다시 맞춘다.
    const { toggleStarred } = useStarredToggle({
        reload: isNewSearch => getListItems(isNewSearch),
    });

    const handleMultiStarred = type =>
        toggleStarred(checkItems, {
            forceRemove: type === 'unactive',
            ss: router.query.ss,
        });

    const { handleDownload } = useDriveDownload({ t });

    const getListItems = async () => {
        try {
            const [recent, starred, pinned] = await Promise.all([
                api.Get('recent', {
                    params: { pi: 1, ps: 8 },
                }),

                api.Get('starred', {
                    params: { pi: 1, ps: 8 },
                }),

                getPinnedList(),
            ]);

            if (recent?.status === 200) {
                setRecentList(recent?.data?.data || []);
            }

            if (starred?.status === 200) {
                setStarredList(starred?.data?.data || []);
            }
        } catch (err) {
            console.error('리스트 가져오기 실패:', err);
        }
    };

    const getPinnedList = async () => {
        const res = await api.Get('pinned-folders', {});

        if (res?.status === 200) {
            setPinnedList(res?.data?.data || []);
        }
    };

    const handleNewDocument = async tt => {
        const isNote = tt === 'note';
        const popup = openPendingPopup();

        try {
            const res = await createDocument(tt, user);
            if (res.code !== 201) {
                closePendingPopup(popup);
                showSnackbar(res.message, 'error');
                return;
            }

            const resourceSeq = res.data.resourceSeq;
            const result = await api.Get(`resources/${resourceSeq}`, {});
            const newDocument = result.data?.data;

            let editUrl = isNote
                ? await getIntellectUrl(newDocument, user)
                : await getOfficeEditUrl(newDocument, user);
            if (!editUrl) throw new Error('Edit URL not generated');

            openDocumentWindow(editUrl, popup);

            const registered = await registerRecent(resourceSeq, '');
            if (!registered) {
                showSnackbar(t('recent.register.fail'), 'error');
            }

            window.postMessage('drive_reload', '*');
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

    const handleDeletePinnedFolder = async item => {
        const res = await api.Delete(`pinned-folders/${item?.pinnedFolderSeq}`, {});

        if (res.status === 200) {
            showSnackbar(t('pinnedFolder.remove.success'), 'success');
            await getPinnedList();
        } else {
            showSnackbar(res?.message, 'error');
        }
    };

    const getResource = async () => {
        const rs = router.query.rs;
        const ss = router.query.ss;

        try {
            const data = await getResourceDetail(rs, { ss });

            if (data) {
                return data;
            } else {
                showSnackbar(t('resource.fetch.fail'), 'error');
            }
        } catch (err) {
            console.log(err);
        }
    };

    const handleFilePreview = async () => {
        const item = await getResource();
        if (!item) return;
        // user는 로그인 사용자(useUser)여야 한다 — 웹오피스 URL의 loginUserSeq/user_id가 이 값이라
        // 리소스 소유자를 넘기면 공유 파일에서 남의 계정으로 편집기가 열린다.
        // 접근 스코프(ss)는 webOfficeApi가 item에서 sharedByUserSeq ?? userSeq로 따로 계산한다.
        openDialog('preview', {
            previewItem: item,
            user,
        });
    };

    const handlePinnedFolderClick = item => {
        const basePath = getShareSeq(item) === user?.userSeq ? '/drive' : '/shared';
        router.push(`${basePath}?pfs=${item?.resourceSeq}&ss=${getShareSeq(item)}`);
    };

    const handleContainerMouseDown = e => {
        const clickedInsideRecentTable = recentTableRef.current?.contains(e.target);
        const clickedInsideStarredTable = starredTableRef.current?.contains(e.target);
        const clickedInsideRecentMenu = recentActionMenuAreaRef.current?.contains(e.target);
        const clickedInsideStarredMenu = starredActionMenuAreaRef.current?.contains(e.target);
        const clickedInsideQuickMenu = quickMenuAreaRef.current?.contains(e.target);
        const clickedInsideDrawer = drawerRef.current?.contains(e.target);
        const clickedInsideRecentTitle = recentTitleAreaRef.current?.contains(e.target);
        const clickedInsideStarredTitle = starredTitleAreaRef.current?.contains(e.target);
        const clickedInsideHomeInfo = homeInfoAreaRef.current?.contains(e.target);

        // 테이블 바깥 영역 & checkItems가 있다면
        if (
            !clickedInsideRecentTable &&
            !clickedInsideStarredTable &&
            !clickedInsideRecentMenu &&
            !clickedInsideStarredMenu &&
            !clickedInsideQuickMenu &&
            !clickedInsideDrawer &&
            !clickedInsideRecentTitle &&
            !clickedInsideStarredTitle &&
            !clickedInsideHomeInfo &&
            (recentCheckItems.length > 0 || starredCheckItems.length > 0)
        ) {
            setRecentCheckItems([]);
            setStarredCheckItems([]);
        }
    };

    useEffect(() => {
        if (!router.isReady) return;

        // rs 있으면 미리보기 오픈
        const rs = router.query?.rs;
        if (!rs) return;

        if (lastOpenedRsRef.current === rs) return; // 같은 rs로 중복 오픈 방지
        lastOpenedRsRef.current = rs;

        (async () => {
            try {
                await handleFilePreview();
            } finally {
                // rs 쿼리 제거
                const { rs: _omitRs, ss: _omitSs, ...rest } = router.query;
                router.replace({ pathname: router.pathname, query: rest }, undefined, {
                    shallow: true,
                });
            }
        })();
    }, [router.isReady, router.query?.rs, handleFilePreview]);

    /** 아래는 화면 스크롤 조정을 위한 코드 */

    // 세로 스크롤 상태만 맞춤 (위치 이동 X)
    const hiddenOverflow = () => {
        const container = document.querySelector('#home-root');
        const target = document.querySelector('#container');
        if (!container || !target) return;

        // 렌더 직후 1 frame만 사용
        requestAnimationFrame(() => {
            if (target.scrollHeight > container.clientHeight) {
                container.style.overflowY = 'auto';
            } else {
                container.style.overflowY = 'hidden';
            }
        });
    };

    useEffect(() => {
        if (!router.isReady) return;

        hiddenOverflow();

        // 데이터 로드 + 렌더 완료 후 isReaddy true
        getListItems().finally(() => {
            setTimeout(() => setIsReady(true), 250);
        });
    }, [router.isReady]);

    // 초기 이후의 모든 변화 고려해서 스크롤 상태 확인 (데이터 로드, 창 크기 변경 등)
    // 윈도우 리사이즈 + 내용 박스 높이 변화 확인
    useEffect(() => {
        const container = document.querySelector('#home-root');
        const target = document.querySelector('#container');
        if (!container || !target) return;

        let executed = false;
        const ro = new ResizeObserver(() => {
            if (!executed) {
                hiddenOverflow();
                executed = true;
            }
        });
        ro.observe(target);

        return () => {
            ro.disconnect();
        };
    }, []);

    // isReady가 true가 되면 스크롤 0 보정 후에 showUI=true
    useEffect(() => {
        if (!isReady) return;
        const root = document.querySelector('#home-root');
        if (!root) return;

        // 잠깐 잠금(홈화면 잘림 방지)
        root.style.overflow = 'hidden';
        root.style.scrollBehavior = 'auto';

        // 2-frame 보정: 레이아웃/이미지 paint 이후까지 보장
        const raf1 = requestAnimationFrame(() => {
            root.scrollTo(0, 0);
            const raf2 = requestAnimationFrame(() => {
                root.scrollTo(0, 0);
                root.style.overflow = 'auto'; // 잠금 해제
                setShowUI(true); // 이때 화면 노출
            });
            // cleanup 내부 raf
            return () => cancelAnimationFrame(raf2);
        });

        return () => cancelAnimationFrame(raf1);
    }, [isReady]);

    // 페이지 진입 시 스크롤 위치 강제 초기화
    useEffect(() => {
        if (!router.isReady) return;

        requestAnimationFrame(() => {
            const root = document.querySelector('#home-root');
            root?.scrollTo(0, 0);
        });
    }, [router.pathname]);

    // 로그인 직후 최초 진입 시 스크롤 강제 초기화 (Safari / Tablet 전용 버그 대응)
    useEffect(() => {
        if (typeof window === 'undefined') return;

        // 브라우저 자동 복원 끊기
        if ('scrollRestoration' in history) {
            history.scrollRestoration = 'manual';
        }

        // layout ready 전 자동 스크롤 복원되는 문제를 frame 단위로 무시
        const unblock = setTimeout(() => {
            requestAnimationFrame(() => {
                window.scrollTo({ top: 0, behavior: 'auto' });
            });
        }, 150); // 100~200ms가 안정적

        return () => clearTimeout(unblock);
    }, []);

    useWindowMessage(() => getListItems({}, true), 'drive_reload');

    return (
        <>
            <Head>
                <title>{t('meta.home', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box sx={{ position: 'relative', width: '100%', height: '100vh', overflow: 'hidden' }}>
                <Box
                    id={'home-root'}
                    onContextMenu={e => {
                        e.preventDefault();
                    }}
                    onMouseDown={handleContainerMouseDown}
                    sx={{
                        // opacity: isReady ? 1 : 0,
                        transition: 'opacity 0.1s ease', // 부드럽게 등장
                        width: '100%',
                        // height: '100vh',
                        height: '100dvh', // iPad/WebView 대응
                        // minHeight: 'max-content',
                        minHeight: 0,
                        display: 'flex',
                        alignItems: 'flex-start',
                        flexDirection: 'row',
                        position: 'relative',
                        justifyContent: 'space-between',
                        overflow: 'hidden',
                        pt: 'calc(64px + var(--app-banner-height, 0px))',
                        overflowAnchor: 'none', // 브라우저 자동 스크롤 복원 차단
                    }}
                >
                    {!showUI && ( // isReady 대신 showUI 사용 (화면 그리기 + 스크롤 조정 완료될 때까지 Loading 표시)
                        <Stack
                            sx={{
                                position: 'fixed', // fixed 권장
                                inset: 0,
                                left: { xs: 0, md: '259PX' }, // md일 때, 왼쪽 사이드바 영역 만큼 오른쪽으로 -> 'Loading...' 컨텐츠 영역 가운데 위치
                                zIndex: 1200,
                                backgroundColor: 'background.default',
                            }}
                            alignItems="center"
                            justifyContent="center"
                        >
                            <Typography variant="body1" color="text_60">
                                Loading...
                            </Typography>
                        </Stack>
                    )}
                    <Box
                        id={'container'}
                        sx={{
                            width: {
                                xs: '100%',
                                md: '100%',
                                midLg: drawerOpen ? 'calc(100% - 320px)' : '100%',
                            },
                            overflowY: 'auto',
                            overflowX: 'hidden',
                            overflowAnchor: 'none',
                            scrollbarGutter: 'stable both-edges',
                            display: 'flex',
                            position: 'relative',
                            flexDirection: 'column',
                            justifyContent: 'center',
                            alignItems: 'center',
                            pt: {
                                xs: 7,
                                md: 8,
                            },
                            pb: 16,
                            px: {
                                md: 12,
                                xs: 4,
                            },
                        }}
                    >
                        {/* padding 전용 래퍼 */}
                        <Box
                            sx={{
                                pt: {
                                    xs: 0,
                                    md: 4, // 32px + 16px
                                },
                                width: '100%',
                            }}
                        >
                            <Stack
                                ref={homeInfoAreaRef}
                                spacing={0.5}
                                alignItems="center"
                                textAlign="center"
                            >
                                <Stack
                                    direction={{
                                        sm: 'row',
                                        xs: 'column',
                                    }}
                                    alignItems={'center'}
                                    gap={1}
                                    mb={1}
                                >
                                    <Typography variant="h1_h2" color={'text_100'}>
                                        {t(
                                            hour >= 5 && hour < 10
                                                ? 'UserSummary.greetingMorning'
                                                : hour >= 10 && hour < 18
                                                  ? 'UserSummary.greetingAfternoon'
                                                  : 'UserSummary.greetingEvening'
                                        )}
                                        ,&nbsp;
                                    </Typography>
                                    <Typography
                                        variant="h1_h2"
                                        component={'span'}
                                        color={'#586dd6'}
                                    >
                                        {user?.userName}
                                    </Typography>
                                </Stack>
                                <Typography variant="body6" color={'text_100'}>
                                    {convertBytesToReadableSize(user?.useQuotaByte)}
                                    <Typography variant="body6" color={'text_60'}>
                                        &nbsp;of&nbsp;
                                    </Typography>
                                    <Typography variant="body6" color={'text_80'}>
                                        {convertBytesToReadableSize(user?.totalQuotaByte)} used
                                    </Typography>
                                </Typography>
                            </Stack>
                        </Box>
                        <Box
                            sx={{
                                width: '100%',
                            }}
                        >
                            <Stack
                                ref={quickMenuAreaRef}
                                direction={{ sm: 'row', xs: 'column' }}
                                spacing={2}
                                alignItems={'center'}
                                sx={{
                                    overflowX: 'auto',
                                    overflowY: 'hidden',
                                    pb: 4,
                                    pt: 6,

                                    // 가운데 정렬 (정렬 메뉴가 적을 때, 양쪽에 가상 요소 추가)
                                    '&::before, &::after': {
                                        content: '""',
                                        flex: '1 0 auto',
                                    },
                                }}
                            >
                                {filteredMenu.map((item, index) => (
                                    <StyledButton
                                        color={'bg_gray.main'}
                                        variant={'outlined'}
                                        key={index}
                                        onClick={async () => {
                                            if (item.route) router.push(item.route);
                                            else await handleNewDocument(item.value);
                                        }}
                                        sx={{
                                            width: 160,
                                            height: 80,
                                            flexShrink: 0,
                                            display: 'flex',
                                            borderRadius: 2,
                                            flexDirection: 'column',
                                            gap: '6px',
                                            py: 2.5,
                                            '&:hover': {
                                                boxShadow: `0 0 11px 0 #3a3d492b`,
                                                border: 0,
                                                backgroundColor: palette.white,
                                            },
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                background: item.backgroundColor,
                                                display: 'flex',
                                                justifyContent: 'center',
                                                borderRadius: 2,
                                            }}
                                            px={2}
                                            py={1.5}
                                        >
                                            <Image
                                                src={item.src}
                                                alt={item.alt}
                                                width={28}
                                                height={28}
                                            />
                                        </Box>
                                        <Stack direction="row" alignItems="center" gap={1}>
                                            <Typography
                                                variant={'body8'}
                                                fontWeight={500}
                                                color={'text_80'}
                                            >
                                                {t(item.text)}
                                            </Typography>
                                            {item.isBeta && (
                                                <Typography
                                                    variant="caption"
                                                    sx={{
                                                        color: palette.text_80,
                                                        position: 'relative',
                                                        top: '0px',
                                                    }}
                                                >
                                                    {t('label.beta')}
                                                </Typography>
                                            )}
                                        </Stack>
                                    </StyledButton>
                                ))}

                                {pinnedList?.map((item, index) => (
                                    <StyledButton
                                        color={'bg_gray.main'}
                                        variant={'outlined'}
                                        onClick={() => handlePinnedFolderClick(item)}
                                        key={index}
                                        sx={{
                                            width: 160,
                                            height: 80,
                                            flexShrink: 0,
                                            display: 'flex',
                                            flexDirection: 'column',
                                            position: 'relative',
                                            gap: '6px',
                                            py: 2.5,
                                            borderRadius: 2,
                                            '&:hover': {
                                                boxShadow: `0 0 11px 0 #3a3d492b`,
                                                border: 0,
                                                backgroundColor: palette.white,
                                            },
                                            [`&:hover #delete-${index}`]: {
                                                opacity: '1 !important',
                                            },
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                position: 'absolute',
                                                right: 6,
                                                top: 6,
                                                opacity: { xs: 1, md: 0 },
                                            }}
                                            id={`delete-${index}`}
                                        >
                                            <Image
                                                src={'/common/icon-ic-delete.svg'}
                                                alt={`delete`}
                                                width={13}
                                                height={13}
                                                onClick={e => {
                                                    e.stopPropagation();
                                                    handleDeletePinnedFolder(item);
                                                }}
                                            />
                                        </Box>

                                        <Box
                                            sx={{
                                                background: '#e6e6e699',
                                                display: 'flex',
                                                justifyContent: 'center',
                                                alignItems: 'center',
                                                borderRadius: 2,
                                                position: 'relative',
                                            }}
                                            px={2}
                                            py={1.5}
                                        >
                                            <Stack
                                                justifyContent={'center'}
                                                alignItems={'center'}
                                                sx={{ width: 28, height: 28 }}
                                            >
                                                <Image
                                                    src={'/common/icon-ic-folder-outline.svg'}
                                                    alt={`folder-${index}`}
                                                    width={28}
                                                    height={28}
                                                />
                                            </Stack>
                                        </Box>
                                        <Stack
                                            direction="row"
                                            alignItems="center"
                                            justifyContent="center"
                                            gap={1}
                                            sx={{ width: '100%', overflow: 'hidden' }}
                                        >
                                            <BaseTooltip
                                                title={item?.resourceName}
                                                disableHoverListener={
                                                    item.resourceName.length <= 12
                                                }
                                            >
                                                <Typography
                                                    variant={'body8'}
                                                    color={'text_80'}
                                                    noWrap
                                                >
                                                    {item.resourceName}
                                                </Typography>
                                            </BaseTooltip>
                                        </Stack>
                                    </StyledButton>
                                ))}
                                {pinnedList.length < 3 && (
                                    <IconButton
                                        sx={{
                                            width: 24,
                                            height: 24,
                                            flexShrink: 0,
                                            border: `1px solid ${palette.btn_border}`,
                                        }}
                                        onClick={() =>
                                            openDialog('folderFix', {
                                                reload: getPinnedList,
                                            })
                                        }
                                    >
                                        <AddOutlinedIcon sx={{ width: 16 }} />
                                    </IconButton>
                                )}
                            </Stack>
                        </Box>

                        <Grid
                            sx={{ width: '100%' }}
                            container
                            spacing={{
                                lg: 21,
                                xs: 8,
                            }}
                            size={12}
                            direction="row"
                            mt={6}
                            alignItems={'flex-start'}
                        >
                            {/*recent*/}
                            <Grid
                                size={{
                                    lg: 6,
                                    xs: 12,
                                }}
                            >
                                <Stack
                                    direction="row"
                                    px={1}
                                    sx={{
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                        borderBottom: `1px solid ${palette.divider_12}`,
                                    }}
                                >
                                    <Stack direction="row" alignItems="center" gap={1}>
                                        <Typography
                                            ref={recentTitleAreaRef}
                                            variant={'h5'}
                                            sx={{ whiteSpace: 'nowrap' }}
                                        >
                                            {t('common.label.recent')}
                                        </Typography>
                                        {/** 상단 액션 아이콘 메뉴바 */}
                                        <Box
                                            ref={recentActionMenuAreaRef}
                                            sx={{
                                                visibility:
                                                    recentCheckItems.length > 0
                                                        ? 'visible'
                                                        : 'hidden',
                                                pointerEvents:
                                                    recentCheckItems.length > 0 ? 'auto' : 'none',
                                            }}
                                        >
                                            <DriveActionIconMenuBar
                                                page="recent"
                                                t={t}
                                                router={router}
                                                checkItems={recentCheckItems}
                                                setCheckItems={setRecentCheckItems}
                                                openDialog={openDialog}
                                                handleDownload={handleDownload}
                                                handleMultiStarred={handleMultiStarred}
                                                areAllItemsStarred={areAllRecentItemsStarred}
                                                handleDeleteDrive={handleDeleteDrive}
                                                reload={() => getListItems(true)}
                                                mt={0}
                                                mb={0}
                                            />
                                        </Box>
                                    </Stack>

                                    <IconButton onClick={() => router.push(ROUTES.RECENT)}>
                                        <Image
                                            src={'/common/right-arrow.svg'}
                                            alt={'arrow'}
                                            width={20}
                                            height={20}
                                        />
                                    </IconButton>
                                </Stack>

                                {recentList.length > 0 ? (
                                    <Box ref={recentTableRef} sx={{ height: '100%' }}>
                                        <HomeTable
                                            data={recentList}
                                            showShare={true}
                                            getData={getListItems}
                                            drawerOpen={drawerOpen}
                                            setDrawerOpen={setDrawerOpen}
                                            checkItems={recentCheckItems}
                                            setCheckItems={setRecentCheckItems}
                                        />
                                    </Box>
                                ) : (
                                    <Box py={3}>
                                        <Typography variant={'body5'} color={'text_60'}>
                                            {t('Recent.text1')}
                                        </Typography>
                                    </Box>
                                )}
                            </Grid>
                            {/*starred*/}
                            <Grid
                                size={{
                                    lg: 6,
                                    xs: 12,
                                }}
                            >
                                <Stack
                                    direction="row"
                                    sx={{
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                        borderBottom: `1px solid ${palette.divider_12}`,
                                        px: 1,
                                    }}
                                >
                                    <Stack direction="row" alignItems="center" gap={1}>
                                        <Typography
                                            ref={starredTitleAreaRef}
                                            variant={'h5'}
                                            sx={{ whiteSpace: 'nowrap' }}
                                        >
                                            {t('common.label.starred')}
                                        </Typography>
                                        {/** 상단 액션 아이콘 메뉴바 */}
                                        <Box
                                            ref={starredActionMenuAreaRef}
                                            sx={{
                                                visibility:
                                                    starredCheckItems.length > 0
                                                        ? 'visible'
                                                        : 'hidden',
                                                pointerEvents:
                                                    starredCheckItems.length > 0 ? 'auto' : 'none',
                                            }}
                                        >
                                            <DriveActionIconMenuBar
                                                page="starred"
                                                t={t}
                                                router={router}
                                                checkItems={starredCheckItems}
                                                setCheckItems={setStarredCheckItems}
                                                openDialog={openDialog}
                                                handleDownload={handleDownload}
                                                handleMultiStarred={handleMultiStarred}
                                                areAllItemsStarred={true} // 중요문서함에서는 항상 true
                                                handleDeleteDrive={handleDeleteDrive}
                                                reload={() => getListItems(true)}
                                                mt={0}
                                                mb={0}
                                            />
                                        </Box>
                                    </Stack>
                                    <IconButton onClick={() => router.push(ROUTES.STARRED)}>
                                        <Image
                                            src={'/common/right-arrow.svg'}
                                            alt={'arrow'}
                                            width={20}
                                            height={20}
                                        />
                                    </IconButton>
                                </Stack>

                                {starredList.length > 0 ? (
                                    <Box ref={starredTableRef} sx={{ height: '100%' }}>
                                        <HomeTable
                                            data={starredList}
                                            showLike={true}
                                            showShare={true}
                                            getData={getListItems}
                                            drawerOpen={drawerOpen}
                                            setDrawerOpen={setDrawerOpen}
                                            checkItems={starredCheckItems}
                                            setCheckItems={setStarredCheckItems}
                                        />
                                    </Box>
                                ) : (
                                    <Box py={3}>
                                        <Typography variant={'body5'} color={'text_60'}>
                                            {t('Starred.text1')}
                                        </Typography>
                                    </Box>
                                )}
                            </Grid>
                        </Grid>
                    </Box>
                </Box>

                {/** Details */}
                {drawerOpen && (
                    <Box
                        sx={{
                            position: 'fixed', // 항상 화면 기준으로 고정
                            top: 'calc(64px + var(--app-banner-height, 0px))', // 상단 AppBar·배너 아래부터 시작
                            right: 0, // 오른쪽 끝에 붙이기
                            zIndex: 1200,
                        }}
                    >
                        <DriveDrawer
                            drawerOpen={drawerOpen}
                            setDrawerOpen={setDrawerOpen}
                            checkItems={checkItems}
                            user={user}
                        />
                    </Box>
                )}
            </Box>
        </>
    );
}

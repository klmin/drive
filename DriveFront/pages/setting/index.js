'use client';

import Box from '@mui/material/Box';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useCallback, useEffect, useMemo, useRef } from 'react';
import { Stack, Typography } from '@mui/material';
import { useState } from 'react';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import api from '@/utils/api';
import General from '@/components/views/setting/General';
import Notifications from '@/components/views/setting/Notifications';
import { useRouter } from '@/hooks/useRouter';
import Head from 'next/head';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import { ROUTES } from '@/constants/routes';

export default function Setting() {
    const t = useTranslations();
    const router = useRouter();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [menu, setMenu] = useState(router.query.menu || 'general');
    const [notificationList, setNotificationList] = useState([]);
    // 전체 안 읽은 알림 수. 로드된 목록으로 세면 창 밖의 알림이 빠지므로 서버 값을 쓴다.
    const [unreadCount, setUnreadCount] = useState(0);
    const [isRead, setIsRead] = useState(true); // 알림 탭 필터

    const [cursorId, setCursorId] = useState(null);
    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    const [hasNext, setHasNext] = useState(false);

    const [loading, setLoading] = useState(true);

    const tableContainerRef = useRef(null);
    const loadingMoreRef = useRef(false);
    const dataLen = useMemo(() => notificationList.length, [notificationList]);

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
            if (isNewSearch) setLoading(true);
            let res;
            if (menu !== 'general') {
                res = await api.Get('notifications', {
                    params,
                });
            }

            if (res?.status === 200 && res.data) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                const newData = res?.data?.data;

                if (isNewSearch) {
                    setNotificationList(newData);
                } else {
                    // 기존 목록에 새 데이터 추가
                    setNotificationList(prev => [...prev, ...newData]);
                }
            }
        } catch (error) {
            console.error('Failed to fetch data:', error);
        } finally {
            console.log('로딩 끝남');
            if (isNewSearch) setLoading(false);
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

    useEffect(() => {
        setNotificationList([]);
        setCursorId(null);
        setHasNext(false);
        setCursorSearchCount(0);
        if (tableContainerRef.current) {
            tableContainerRef.current.scrollTo(0, 0);
        }

        // notifications 메뉴일 때만 데이터 fetch
        if (menu !== 'general') {
            setLoading(true);
            reloadNotifications();
            getUnreadCount();
        } else {
            setLoading(false);
        }

        reloadNotifications();
    }, [menu, isRead]);

    useEffect(() => {
        if (!cursorId) return;
        if (cursorSearchCount <= 0) return; // 다음 목룍 요청인지

        loadMoreNotifications();
    }, [cursorSearchCount]);

    useEffect(() => {
        loadingMoreRef.current = false;
    }, [dataLen]);

    const tryLoadMore = useCallback(() => {
        if (!hasNext) return;
        if (loadingMoreRef.current) return;

        loadingMoreRef.current = true;
        setCursorSearchCount(prev => prev + 1);
    }, [hasNext]);

    const handleTableScroll = useCallback(() => {
        const container = tableContainerRef.current;
        if (!container) return;

        const { scrollTop, scrollHeight, clientHeight } = container;
        if (scrollHeight - scrollTop - clientHeight < 200) {
            tryLoadMore();
        }
    }, [tryLoadMore]);

    useEffect(() => {
        const container = tableContainerRef.current;
        if (container) {
            container.addEventListener('scroll', handleTableScroll);
            return () => container.removeEventListener('scroll', handleTableScroll);
        }
    }, [handleTableScroll]);

    useEffect(() => {
        if (router.query.menu) {
            setMenu(router.query.menu);
        }
    }, [router.query]);

    useVvh();

    return (
        <>
            <Head>
                <title>{t('meta.common', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box
                id={'container'}
                sx={{
                    height: 'calc(var(--vvh, 1vh) * 100 - var(--app-banner-height, 0px))',
                    overflowY: 'auto',
                }}
                ref={tableContainerRef}
            >
                <Box
                    sx={{
                        width: {
                            xs: '100%',
                            md: 'calc(100% - 260px)',
                        },
                        position: 'fixed',
                        background: 'white',
                        zIndex: 999,
                        px: {
                            md: 12,
                            xs: 4,
                        },
                        pt: {
                            xs: 7,
                            md: 8,
                        },
                    }}
                >
                    <Stack
                        direction={'row'}
                        alignItems={'flex-end'}
                        gap={6}
                        sx={{
                            position: 'relative',
                            '&:after': {
                                content: '""',
                                position: 'absolute',
                                bottom: 0,
                                left: 0,
                                width: '100%',
                                height: '1px',
                                backgroundColor: palette.divider_8,
                            },
                        }}
                    >
                        <StyledButton
                            onClick={() => router.push(ROUTES.SETTING_GENERAL)}
                            variant={'text'}
                            sx={{
                                px: 1,
                                borderRadius: 0,
                                pt: 0,
                                pb: 1.5,
                                height: 30,
                                borderBottom:
                                    menu === 'general'
                                        ? `2px solid ${palette.text_100}`
                                        : '2px solid transparent',
                                '&:hover': {
                                    background: 'none !important',
                                    borderBottom: `2px solid ${palette.text_60}`,
                                },
                            }}
                        >
                            <Typography
                                noWrap
                                variant={'h4'}
                                color={menu === 'general' ? 'text_100' : 'text_60'}
                            >
                                {t('common.label.setting')}
                            </Typography>
                        </StyledButton>
                        <StyledButton
                            onClick={() => router.push(ROUTES.SETTING_NOTIFICATIONS)}
                            variant={'text'}
                            sx={{
                                px: 1,
                                borderRadius: 0,
                                pt: 0,
                                pb: 1.5,
                                height: 30,
                                borderBottom:
                                    menu === 'notifications'
                                        ? `2px solid ${palette.text_100}`
                                        : '2px solid transparent',
                                '&:hover': {
                                    background: 'none !important',
                                    // 3. hover 시에는 테두리를 추가하는 대신 색상만 변경합니다.
                                    borderBottomColor: palette.text_60,
                                },
                            }}
                        >
                            <Typography
                                noWrap
                                variant={'h4'}
                                color={menu === 'notifications' ? 'text_100' : 'text_60'}
                            >
                                {t('notifications.title')}
                            </Typography>
                        </StyledButton>
                    </Stack>
                </Box>
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: menu === 'general' ? 'center' : 'flex-start',
                        position: 'relative',
                        backgroundColor: 'inherit',
                        px: {
                            md: 12,
                            xs: 4,
                        },
                    }}
                >
                    {menu === 'general' ? (
                        <General />
                    ) : (
                        <Notifications
                            notificationList={notificationList}
                            onReload={reloadNotifications}
                            onToggleRead={updateNotificationReadLocal}
                            unreadCount={unreadCount}
                            onUnreadCountChange={setUnreadCount}
                            isRead={isRead}
                            setIsRead={setIsRead}
                            loading={loading}
                        />
                    )}
                </Box>
                <Box sx={{ height: '64px', flexShrink: 0 }} />
            </Box>
        </>
    );
}

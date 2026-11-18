import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Popover, Box, Typography, Stack, IconButton, Skeleton } from '@mui/material';
import Image from 'next/image';
import { getFileByFileType, getItemExt } from '@/constants/FILES';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import dayjs from 'dayjs';
import StyledButton from '@/components/common/button/StyledButton';
import StyledSwitchButton from '@/components/common/button/StyledSwitchButton';
import api from '@/utils/api';
import { getResourceDetail } from '@/apis/resourceApi';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useUser } from '@/context/UserContext';
import { useDialog } from '@/provider/DialogProvider';
import useOfficeViewer from '@/hooks/useOfficeViewer';
import { formatUnreadCount } from '@/utils/format';
import { BaseTooltip } from '../common/tooltip/BaseTooltip';
import EllipsisText from '../common/tooltip/EllipsisText';
import { ROUTES } from '@/constants/routes';

// const NotificationItem = ({ notification, showSnackbar, reload }) => {
const NotificationItem = React.memo(
    function NotificationItem({ notification, showSnackbar, onToggleRead, onUnreadCountChange }) {
        const t = useTranslations();
        const {
            actionType,
            description,
            isRead,
            registerDate,
            resourceSeq,
            resourceName,
            resourceType,
            sharedByUserSeq,
        } = notification;
        const user = useUser();
        const router = useRouter();
        const { openDialog } = useDialog();
        const { isOfficeViewerTarget, openOfficeViewer } = useOfficeViewer();
        const isFile = resourceType === 'FILE';
        const isExternalTenant = description?.tenantSeq !== user?.tenantSeq;

        const checkResourceAccess = async () => {
            const data = await getResourceDetail(resourceSeq, { ss: sharedByUserSeq });

            if (data) {
                return true;
            } else {
                /** 접근 불가
                 *  휴지통에 있는 리소스라면
                    {"status":404,"errorCode":"RESOURCE_005","message":"데이터를 찾을 수 없습니다."}

                    완전 삭제된 리소스라면
                    {"status":403,"errorCode":"PERMISSION_001","message":"리소스가 공유되지 않았습니다."}

                    공유 해제된 리소스라면 (휴지통x, 완전삭제x)
                    {"status":403,"errorCode":"PERMISSION_002","message":"접근 권한이 없습니다"}
                 */
                return false;
            }
        };

        const openShareDisabledResourceDialog = () => {
            openDialog('alert', {
                title: t('admin.share.disabledResourceDialog.title'),
                description: t('admin.share.disabledResourceDialog.desc', {
                    // 접근 불가 리소스는 resourceName이 없으므로 description.fileName으로 대체
                    resourceName: resourceName ? resourceName : description?.fileName,
                }),
                cancelButtonName: '',
                maxWidth: '423PX',
            });
        };

        const handlePreview = async () => {
            if (!(await checkResourceAccess())) {
                openShareDisabledResourceDialog();
                return;
            }

            const item = {
                resourceSeq: resourceSeq,
                resourceName: resourceName,
                sharedByUserSeq: sharedByUserSeq,
                resourceType: resourceType,
            };
            // http TFO 오피스는 다이얼로그 대신 새 창 — 편집 권한 있으면 에디터
            if (isOfficeViewerTarget(item)) {
                openOfficeViewer(item, { preferEdit: true });
                return;
            }
            openDialog('preview', {
                previewItem: item,
                user: user,
                preferEdit: true,
            });
        };

        const handleNavigate = async () => {
            if (!(await checkResourceAccess())) {
                openShareDisabledResourceDialog();
                return;
            }

            const root = sharedByUserSeq === user?.userSeq ? '/drive' : '/shared';
            const query = `pfs=${resourceSeq}&ss=${sharedByUserSeq}`;
            router.push(`${root}?${query}`);
        };

        const renderContent = () => {
            switch (actionType) {
                case 'PERMISSION_REQUESTED':
                    return (
                        <Stack direction={'column'} sx={{ maxWidth: 300 }}>
                            <Typography
                                variant="body7"
                                color={'text_80'}
                                component="div"
                                sx={{
                                    minWidth: 0, // flex 안에서 줄바꿈하려면 필수
                                    wordBreak: 'break-word', // 긴 파일명 줄바꿈
                                    overflowWrap: 'anywhere', // 공백 없는 문자열도 줄바꿈
                                }}
                            >
                                {t.rich('Notification.PERMISSION_REQUESTED', {
                                    userName: isExternalTenant
                                        ? description?.userName + `(${description?.tenantName})`
                                        : description?.userName,
                                    fileName: description?.fileName,
                                    highlight: chunks => (
                                        <Typography
                                            variant="body6"
                                            component={'span'}
                                            color={isRead ? 'text_100' : 'text_tab_selected'}
                                            sx={{ display: 'inline' }}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                })}
                            </Typography>
                            <Stack direction={'row'} alignItems={'center'} gap={1} mt={2} mb={3}>
                                <StyledButton
                                    variant="contained"
                                    color={'btn_point_bg'}
                                    size="small"
                                    sx={{ px: 2, height: 24 }}
                                >
                                    <Typography variant={'body9'} color={'white'}>
                                        {t('Notification.APPROVE')}
                                    </Typography>
                                </StyledButton>
                                <StyledButton
                                    variant="outlined"
                                    color={'btn_white_bg_normal'}
                                    size="small"
                                    sx={{ px: 2, height: 24 }}
                                >
                                    <Typography variant={'body9'} color={'text_100'}>
                                        {t('Notification.REJECT')}
                                    </Typography>
                                </StyledButton>
                            </Stack>
                            <Typography variant="body9" color="text_60">
                                {dayjs(registerDate).format('YYYY. M. D. H:mm')}
                            </Typography>
                        </Stack>
                    );
                case 'SHARED':
                case 'UNSHARED':
                    return (
                        <Stack direction={'column'} sx={{ maxWidth: 300 }}>
                            <Typography
                                variant="body7"
                                color={'text_80'}
                                component="div"
                                sx={{
                                    minWidth: 0, // flex 안에서 줄바꿈하려면 필수
                                    wordBreak: 'break-word', // 긴 파일명 줄바꿈
                                    overflowWrap: 'anywhere', // 공백 없는 문자열도 줄바꿈
                                }}
                            >
                                {t.rich(
                                    actionType === 'UNSHARED'
                                        ? 'Notification.UNSHARED'
                                        : 'Notification.SHARED',
                                    {
                                        userName: isExternalTenant
                                            ? description?.userName + `(${description?.tenantName})`
                                            : description?.userName,
                                        fileName: description?.fileName,
                                        highlight: chunks => (
                                            <Typography
                                                variant="body6"
                                                component={'span'}
                                                color={isRead ? 'text_100' : 'text_tab_selected'}
                                                sx={{ display: 'inline' }}
                                            >
                                                {chunks}
                                            </Typography>
                                        ),
                                    }
                                )}
                            </Typography>
                            <StyledButton
                                variant={'outlined'}
                                color={'btn_border'}
                                sx={{
                                    gap: 1.5,
                                    maxWidth: '100%', // 말줄임을 위해
                                    px: 2,
                                    height: { xs: 32, md: 28 },
                                    justifyContent: 'flex-start',
                                    my: 3,
                                }}
                                onClick={isFile ? handlePreview : handleNavigate}
                            >
                                <Image
                                    src={
                                        getFileByFileType(
                                            getItemExt(notification, description?.fileName),
                                            resourceType
                                        )?.src
                                    }
                                    alt={'file'}
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant={'body9'}
                                    color={'text_80'}
                                    noWrap
                                    sx={{
                                        minWidth: 0, // flex 말줄임
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                    }}
                                >
                                    <EllipsisText>{description.fileName}</EllipsisText>
                                </Typography>
                            </StyledButton>
                            <Typography variant="body9" color="text_60">
                                {dayjs(registerDate).format('YYYY. M. D. H:mm')}
                            </Typography>
                        </Stack>
                    );
                case 'PERMISSION_CHANGED':
                    return (
                        <Stack direction={'column'} sx={{ maxWidth: 300 }}>
                            <Typography variant="body7" color={'text_80'} component={'span'}>
                                {t.rich('Notification.PERMISSION_CHANGED', {
                                    userName: isExternalTenant
                                        ? description?.userName + `(${description?.tenantName})`
                                        : description?.userName,
                                    fileName: description?.fileName,
                                    permission: description?.permission,
                                    highlight: chunks => (
                                        <Typography
                                            variant="body6"
                                            component={'span'}
                                            color={isRead ? 'text_100' : 'text_tab_selected'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                })}
                            </Typography>
                            <Typography variant="body9" color="text_60">
                                {dayjs(registerDate).format('YYYY. M. D. H:mm')}
                            </Typography>
                        </Stack>
                    );

                case 'STORAGE_WARNING':
                    return (
                        <Stack direction={'column'} sx={{ maxWidth: 300 }}>
                            <Typography variant="body7" color={'text_80'} component={'span'}>
                                {t.rich('Notification.STORAGE_WARNING', {
                                    usagePercentage: description?.usagePercentage,
                                    highlight: chunks => (
                                        <Typography
                                            variant="body6"
                                            component={'span'}
                                            color={isRead ? 'text_100' : 'text_tab_selected'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                })}
                            </Typography>
                            <Typography variant="body9" color="text_60">
                                {dayjs(registerDate).format('YYYY. M. D. H:mm')}
                            </Typography>
                        </Stack>
                    );

                case 'FILE_UPLOAD':
                    return (
                        <Stack direction={'column'} sx={{ maxWidth: 300 }}>
                            <Typography variant="body7" color={'text_80'} component={'span'}>
                                {t.rich('Notification.FILE_UPLOAD', {
                                    userName: isExternalTenant
                                        ? description?.userName + `(${description?.tenantName})`
                                        : description?.userName,
                                    fileName: description?.fileName,
                                    folderName: description?.folderName,
                                    highlight: chunks => (
                                        <Typography
                                            variant="body6"
                                            component={'span'}
                                            color={isRead ? 'text_100' : 'text_tab_selected'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                })}
                            </Typography>
                            <Typography variant="body9" color="text_60">
                                {dayjs(registerDate).format('YYYY. M. D. H:mm')}
                            </Typography>
                        </Stack>
                    );

                case 'STORAGE_EXCEEDED':
                    return (
                        <Stack direction={'column'} sx={{ maxWidth: 300 }}>
                            <Typography variant="body7" color={'text_80'} component={'span'}>
                                {t.rich('Notification.STORAGE_EXCEEDED', {
                                    highlight: chunks => (
                                        <Typography
                                            variant="body6"
                                            component={'span'}
                                            color={isRead ? 'text_100' : 'text_tab_selected'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                })}
                            </Typography>
                            <Typography variant="body9" color="text_60">
                                {dayjs(registerDate).format('YYYY. M. D. H:mm')}
                            </Typography>
                        </Stack>
                    );

                default:
                    return null;
            }
        };

        const handleRead = async () => {
            const url = notification?.isRead
                ? `notifications/${notification?.notificationSeq}/unread`
                : `notifications/${notification?.notificationSeq}/read`;
            const res = await api.Patch(url, {});

            if (res.status === 200) {
                onToggleRead(notification.notificationSeq, !notification.isRead);
                // 응답이 갱신된 전체 안 읽은 수를 돌려준다
                if (typeof res?.data?.data?.unreadCount === 'number') {
                    onUnreadCountChange?.(res.data.data.unreadCount);
                }
            } else {
                showSnackbar(t('notifications.updateReadStatus.fail'), 'error');
            }
        };

        return (
            <Stack
                direction={'row'}
                alignItems={'flex-start'}
                gap={2}
                py={2}
                sx={{ position: 'relative' }}
            >
                <Stack direction={'column'} sx={{ height: '100%' }}>
                    <Box
                        alignItems={'center'}
                        justifyContent={'center'}
                        sx={{
                            display: 'flex',
                            width: 36,
                            height: 36,
                            backgroundColor: palette.btn_white_bg_hover,
                            borderRadius: 32,
                        }}
                    >
                        <BaseTooltip
                            title={isRead ? t('tooltip.markAsUnread') : t('tooltip.markAsRead')}
                        >
                            <IconButton onClick={handleRead}>
                                <Image
                                    src={
                                        isRead
                                            ? '/common/icon-ic-bell.svg'
                                            : '/common/icon-ic-bell-badge-outline.svg'
                                    }
                                    alt="bell"
                                    width={24}
                                    height={24}
                                    onError={e =>
                                        (e.currentTarget.src =
                                            'https://placehold.co/24x24/F5F5F5/333?text=🔔')
                                    }
                                />
                            </IconButton>
                        </BaseTooltip>
                    </Box>
                    <Box
                        sx={{
                            position: 'absolute',
                            top: 45, // [수정] 아이콘 아래 시작(36) + 상단 패딩(16)
                            bottom: 11.5, // [수정] 하단 패딩(16)만큼 띄움
                            left: 17,
                            borderLeft: '2px dotted #3a3d494d',
                            zIndex: 0,
                        }}
                    />
                </Stack>
                <Stack gap={1} sx={{ minWidth: 0 }}>
                    {renderContent()}
                </Stack>
            </Stack>
        );
    },
    (prev, next) => {
        return (
            prev.notification.notificationSeq === next.notification.notificationSeq &&
            prev.notification.isRead === next.notification.isRead
        );
    }
);

const LoadingSkeleton = () => {
    return (
        <Stack
            direction={'row'}
            alignItems={'flex-start'}
            gap={2}
            py={2}
            sx={{ position: 'relative' }}
        >
            {/* 실제 항목의 벨 아이콘 영역과 같은 36px */}
            <Skeleton variant="circular" width={36} height={36} />
            <Stack direction={'column'}>
                <Skeleton variant="text" width={90} />
                <Stack direction={'row'} alignItems={'flex-start'} gap={'5px'}>
                    <Skeleton variant="text" width={144} />
                    <Skeleton variant="text" width={98} />
                </Stack>
                <Stack direction={'row'} alignItems={'flex-start'} gap={'5px'}>
                    <Skeleton variant="text" width={187} />
                    <Skeleton variant="text" width={55} />
                </Stack>
            </Stack>
        </Stack>
    );
};

const SKELETON_DELAY = 250; // 이 시간 안에 응답이 오면 스켈레톤을 아예 노출하지 않는다
const SKELETON_MIN_VISIBLE = 400; // 한 번 노출했다면 최소 이 시간은 유지한다

/**
 * 로딩 표시를 '지연 노출 + 최소 유지'로 바꿔 잠깐 나타났다 사라지는 깜빡임을 없앤다.
 * - 빠른 응답: 스켈레톤이 한 번도 보이지 않음
 * - 느린 응답: 스켈레톤이 뜨고, 최소 시간은 유지된 뒤 사라짐
 */
const useDelayedSkeleton = active => {
    const [visible, setVisible] = useState(false);
    const shownAtRef = useRef(0);

    useEffect(() => {
        let timer;

        if (active) {
            if (visible) return undefined; // 이미 노출 중이면 타이머를 다시 걸지 않는다
            timer = setTimeout(() => {
                shownAtRef.current = Date.now();
                setVisible(true);
            }, SKELETON_DELAY);
        } else if (visible) {
            const remain = Math.max(0, SKELETON_MIN_VISIBLE - (Date.now() - shownAtRef.current));
            timer = setTimeout(() => setVisible(false), remain);
        }

        return () => clearTimeout(timer);
    }, [active, visible]);

    return visible;
};

const groupNotificationsByDate = (notifications, t) => {
    const grouped = {};
    const today = dayjs();

    notifications.forEach(notification => {
        const itemDate = dayjs(notification.registerDate);
        let dateKey;

        if (itemDate.isSame(today, 'day')) {
            dateKey = t('common.dateFilter.today'); // 오늘
        } else if (itemDate.isSame(today.subtract(1, 'day'), 'day')) {
            dateKey = t('common.dateFilter.yesterday'); // 어제
        } else if (today.diff(itemDate, 'day') < 7) {
            dateKey = t('common.dateFilter.last7Days'); // 최근 7일
        } else if (today.diff(itemDate, 'day') < 30) {
            dateKey = t('common.dateFilter.last30Days'); // 최근 30일
        } else if (itemDate.isSame(today, 'year')) {
            dateKey = t('common.dateFilter.thisYear'); // 올해
        } else {
            dateKey = t('data.older');
        }

        if (!grouped[dateKey]) {
            grouped[dateKey] = [];
        }
        grouped[dateKey].push(notification);
    });

    return grouped;
};

export default function NotificationsPopover({
    loadingMoreRef,
    dataLen,
    notificationList,
    reload,
    onToggleRead,
    unreadCount = 0,
    onUnreadCountChange,
    refreshUnreadCount,
    loading = false,
    isRead,
    setIsRead,
    hasNext,
    setCursorSearchCount,
    anchorEl,
    setAnchorEl,
}) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const router = useRouter();
    const ref = useRef(null);
    const [isMobile, setMobile] = React.useState(false);

    const groupedNotifications = useMemo(
        () => groupNotificationsByDate(notificationList, t),
        [notificationList]
    );
    // 보여줄 목록이 이미 있으면 재조회 중에도 스켈레톤으로 바꾸지 않는다(깜빡임 방지).
    // 첫 조회, 읽음 필터 변경처럼 목록이 비어 있을 때만 스켈레톤 대상.
    const isInitialLoading = loading && notificationList.length === 0;
    const showSkeleton = useDelayedSkeleton(isInitialLoading);

    const handleAllRead = async () => {
        const res = await api.Patch(`notifications/read-all`, {});
        if (res.status === 200) {
            onUnreadCountChange?.(0); // read-all 응답에는 개수가 없다. 모두 읽었으므로 0

            await reload(true);
        }
    };

    useEffect(() => {
        loadingMoreRef.current = false;
    }, [dataLen]);

    const tryLoadMore = useCallback(() => {
        if (!hasNext) return;
        if (loadingMoreRef.current) return;

        loadingMoreRef.current = true;
        setCursorSearchCount(prev => prev + 1);
    }, [hasNext]);

    // onScroll prop으로 직접 연결 (addEventListener + useEffect 방식은 Portal 렌더 타이밍에 따라 ref.current가 null일 수 있어 미사용)
    const handleTableScroll = useCallback(
        e => {
            const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
            if (scrollHeight - scrollTop - clientHeight < 200) {
                tryLoadMore();
            }
        },
        [tryLoadMore]
    );

    useEffect(() => {
        const checkScreenSize = () => {
            if (window.innerWidth > 1024) {
                setMobile(false);
            } else {
                setMobile(true);
            }
        };

        checkScreenSize();

        window.addEventListener('resize', checkScreenSize);
        return () => {
            window.removeEventListener('resize', checkScreenSize);
        };
    }, []);

    useEffect(() => {
        if (isMobile) setAnchorEl(null);
    }, [isMobile]);

    return (
        <Popover
            open={!!anchorEl}
            anchorEl={anchorEl}
            onClose={() => setAnchorEl(null)}
            PaperProps={{
                sx: {
                    mt: 2,
                    display: { xs: 'none', md: 'block' },
                    minWidth: 320,
                    maxHeight: 529,
                    pt: 4,
                    borderRadius: 2,
                    overflowY: 'hidden',
                    border: `1px solid ${palette.control_border_normal}`,
                    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                },
            }}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'left',
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'left',
            }}
        >
            <Box
                display="flex"
                justifyContent="space-between"
                alignItems="center"
                mb={2}
                px={5}
                py={2}
                sx={{ borderBottom: `1px solid ${palette.divider_8}` }}
            >
                <Typography variant="h5">{t(`notifications.title`)}</Typography>
                <BaseTooltip title={t('tooltip.settingsLink')}>
                    <IconButton onClick={() => router.push(ROUTES.SETTING_NOTIFICATIONS)}>
                        <Image
                            src={'/common/icon-ic-open-in-new.svg'}
                            alt={'edit'}
                            width={20}
                            height={20}
                        />
                    </IconButton>
                </BaseTooltip>
            </Box>

            <Box
                sx={{
                    width: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 2,
                    mt: 4,
                    px: 5,
                }}
            >
                <Stack
                    direction={'row'}
                    alignItems={'center'}
                    gap={3}
                    justifyContent={'space-between'}
                >
                    <StyledButton
                        variant="outlined"
                        size="small"
                        sx={{ height: { xs: 32, md: 24 }, px: 2 }}
                        startIcon={
                            <Image
                                src={'/common/icon-ic-bell-outline.svg'}
                                alt="mark as read"
                                width={16}
                                height={16}
                            />
                        }
                        onClick={handleAllRead}
                    >
                        <Typography variant={'body9'} color={'text_80'}>
                            {t('notifications.markAllAsReadButton')}
                        </Typography>
                    </StyledButton>
                    <Stack direction={'row'} alignItems={'center'} gap={1}>
                        <Typography variant={'body5'} color={'text_60'} component={'span'} noWrap>
                            {t('notifications.toggleReadStatusButton')}&nbsp;
                            {/* 개수가 바뀌어도 옆 스위치가 밀리지 않도록 표기 상한(999+) 기준 최소 폭 지정 */}
                            <Typography
                                variant={'body4'}
                                color={'text_60'}
                                component={'span'}
                                sx={{ display: 'inline-block', minWidth: 48 }}
                            >
                                ({formatUnreadCount(unreadCount)})
                            </Typography>
                        </Typography>

                        <StyledSwitchButton value={isRead} onChange={() => setIsRead(!isRead)} />
                    </Stack>
                </Stack>
            </Box>
            <Box
                sx={{
                    overflowY: 'scroll',
                    maxHeight: 355,
                    // 로딩 대기 → 스켈레톤 → 목록으로 넘어가는 동안 팝오버 높이가 단계마다 바뀌면
                    // 하단 Refresh 영역이 여러 번 움직여 깜빡이는 것처럼 보인다. 빈 상태와 같은 값으로 하한을 고정.
                    minHeight: 292,
                    // 목록 내용·스크롤바 폭이 Popover(Paper) 의 shrink-to-fit 너비 계산에 반영되지 않도록 격리.
                    // 스크롤바 유무에 따라 팝오버 전체 너비가 달라지는 문제 방지.
                    contain: 'inline-size',
                }}
                ref={ref}
                onScroll={handleTableScroll}
            >
                {showSkeleton ? (
                    // 실제 목록의 그룹 구조(px 4 / mt 22 / 행 간격 25)를 그대로 맞춰 전환 시 튀지 않게 한다
                    <Stack direction="column" gap={3} px={4}>
                        <Box
                            mt={'22px'}
                            display={'flex'}
                            flexDirection={'column'}
                            sx={{ gap: '25px' }}
                        >
                            <Skeleton variant="text" width={60} height={18} />
                            {Array(5)
                                .fill()
                                .map((item, index) => (
                                    <LoadingSkeleton key={index} />
                                ))}
                        </Box>
                    </Stack>
                ) : isInitialLoading ? (
                    // 스켈레톤 지연 노출 대기 구간. 빈 상태 화면이 잠깐 스치는 것을 막고 높이만 유지한다
                    <Box sx={{ height: 292 }} />
                ) : notificationList?.length === 0 ? (
                    <Stack
                        direction="column"
                        justifyContent={'center'}
                        alignItems={'center'}
                        gap={4}
                        sx={{ height: 292 }}
                    >
                        <Image
                            src={'/common/img-img-inbox-empty.svg'}
                            alt={'empty'}
                            width={160}
                            height={72}
                        />
                        <Typography variant={'body7'} color={'text_60'}>
                            {t('notifications.emptyMessage.unread')}
                        </Typography>
                    </Stack>
                ) : (
                    Object.entries(groupedNotifications)?.map(([date, notifications]) => (
                        // 그룹이 추가/삭제돼도(예: '오늘' 그룹 신규 생성) 나머지 블록이 재생성되지 않도록
                        // index 대신 날짜 라벨로 keying 한다
                        <Stack direction="column" gap={3} px={4} key={date}>
                            <Box
                                mt={'22px'}
                                display={'flex'}
                                flexDirection={'column'}
                                sx={{ gap: '25px' }}
                            >
                                <Typography variant="subtitle3" color="text_60">
                                    {date}
                                </Typography>
                                {notifications.map(notification => (
                                    <NotificationItem
                                        key={notification.notificationSeq}
                                        notification={notification}
                                        onToggleRead={onToggleRead}
                                        onUnreadCountChange={onUnreadCountChange}
                                        showSnackbar={showSnackbar}
                                    />
                                ))}
                            </Box>
                        </Stack>
                    ))
                )}
            </Box>
            <Box
                display="flex"
                alignItems="center"
                justifyContent="flex-end"
                gap={1}
                py={2}
                pr={5}
                onClick={() => {
                    reload(true);
                    refreshUnreadCount?.();
                    setCursorSearchCount(0);
                }}
                sx={{
                    width: '100%',
                    borderTop: `1px solid ${palette.divider_8}`,
                    top: '44%',
                    background: 'white',
                }}
            >
                <StyledButton variant="text" size="small" sx={{ pl: 1, pr: 2.5, py: 1, gap: 1 }}>
                    <Image
                        src={'/common/icon-ic-refresh_active.svg'}
                        width={20}
                        height={20}
                        alt={'Refresh'}
                    />
                    <Typography variant={'body5'} color={'text_tab_selected'}>
                        Refresh
                    </Typography>
                </StyledButton>
            </Box>
        </Popover>
    );
}

import React, { useMemo } from 'react';
import { Box, Typography, Stack, IconButton } from '@mui/material';
import Image from 'next/image';
import dayjs from 'dayjs';
import 'dayjs/locale/ko';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { getResourceDetail } from '@/apis/resourceApi';
import StyledSwitchButton from '@/components/common/button/StyledSwitchButton';
import { getFileByFileType, getItemExt } from '@/constants/FILES';
import palette from '@/theme/palette';
import StyledButton from '@/components/common/button/StyledButton';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import { useUser } from '@/context/UserContext';
import { useDialog } from '@/provider/DialogProvider';
import useOfficeViewer from '@/hooks/useOfficeViewer';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import EllipsisText from '@/components/common/tooltip/EllipsisText';
import { formatUnreadCount } from '@/utils/format';

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
                        <Stack>
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
                            <Stack direction={'row'} alignItems={'center'} gap={1} mt={2}>
                                <StyledButton
                                    variant="contained"
                                    color={'btn_point_bg'}
                                    size="small"
                                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                                >
                                    <Typography variant={'body9'} color={'white'}>
                                        {t('Notification.APPROVE')}
                                    </Typography>
                                </StyledButton>
                                <StyledButton
                                    variant="outlined"
                                    color={'btn_white_bg_normal'}
                                    size="small"
                                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                                >
                                    <Typography variant={'body9'} color={'text_100'}>
                                        {t('Notification.REJECT')}
                                    </Typography>
                                </StyledButton>
                            </Stack>
                        </Stack>
                    );
                case 'SHARED':
                case 'UNSHARED':
                    return (
                        <Stack direction={'column'}>
                            <Typography
                                variant="body7"
                                color="text_80"
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
                                                component="span"
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
                                    mt: 3,
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
                        </Stack>
                    );
                case 'PERMISSION_CHANGED':
                    return (
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
                    );

                case 'STORAGE_WARNING':
                    return (
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
                    );

                case 'FILE_UPLOAD':
                    return (
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
                    );

                case 'STORAGE_EXCEEDED':
                    return (
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
                gap={4}
                py={{
                    xs: 5,
                    md: 4,
                    borderBottom: `1px solid ${palette.table_divider_nomal}`,
                }}
            >
                <Box
                    alignItems={'center'}
                    justifyContent={'center'}
                    sx={{
                        display: 'flex',
                        width: 36,
                        height: 36,
                        backgroundColor: '#F5F5F5',
                        borderRadius: '50%',
                        flexShrink: 0,
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
                <Stack gap={1} sx={{ minWidth: 0 }}>
                    <Typography variant="body8" color={'text_60'}>
                        {dayjs(registerDate).format('YYYY. M. D.  H:mm')}
                    </Typography>

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

export default function Notifications({
    notificationList,
    onReload,
    onToggleRead,
    unreadCount = 0,
    onUnreadCountChange,
    isRead,
    setIsRead,
    loading,
}) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();

    const groupedNotifications = useMemo(
        () => groupNotificationsByDate(notificationList, t),
        [notificationList]
    );
    const handleAllRead = async () => {
        const res = await api.Patch(`notifications/read-all`, {});
        if (res.status === 200) {
            onUnreadCountChange?.(0); // read-all 응답에는 개수가 없다. 모두 읽었으므로 0

            await onReload();
        }
    };

    return (
        <Box
            sx={{
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                gap: 2,
                pt: {
                    md: '86px',
                    xs: '82px',
                },
                //  상단 패딩 + tab메뉴 높이 + 기본 패딩
            }}
        >
            <Stack direction={'row'} alignItems={'center'} gap={6}>
                <StyledButton
                    variant="outlined"
                    size="small"
                    sx={{ height: { xs: 32, md: 24 }, px: 2 }}
                    onClick={handleAllRead}
                    startIcon={
                        <Image
                            src={'/common/icon-ic-bell-outline.svg'}
                            alt="mark as read"
                            width={16}
                            height={16}
                        />
                    }
                >
                    <Typography variant={'body9'} color={'text_80'} noWrap>
                        {t('notifications.markAllAsReadButton')}
                    </Typography>
                </StyledButton>
                <Stack direction={'row'} alignItems={'center'} gap={2}>
                    <Typography variant={'body5'} color={'text_60'} component={'span'}>
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

            <Box
                display={'flex'}
                flexDirection={'column'}
                pb={16}
                gap={{
                    xs: 5,
                    md: '32px',
                }}
            >
                {loading ? (
                    <Stack
                        direction="column"
                        justifyContent={'center'}
                        alignItems={'center'}
                        sx={{ width: '100%', minHeight: '400px' }}
                    >
                        <Typography variant={'body1'} color={'text_60'}>
                            Loading...
                        </Typography>
                    </Stack>
                ) : Object.keys(groupedNotifications).length > 0 ? (
                    Object.entries(groupedNotifications).map(([date, notifications]) => (
                        <Box
                            key={date}
                            mt={2}
                            display={'flex'}
                            flexDirection={'column'}
                            sx={{ gap: 2 }}
                        >
                            <Stack justifyContent={'center'} sx={{ minHeight: 32 }}>
                                <Typography variant="subtitle3" color="text_60">
                                    {date}
                                </Typography>
                            </Stack>

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
                    ))
                ) : (
                    <Stack
                        direction="column"
                        justifyContent={'center'}
                        alignItems={'center'}
                        gap={4}
                        sx={{ width: '100%', mt: '143px' }}
                    >
                        <Image
                            src={'/common/img-img-inbox-empty.svg'}
                            alt={'empty'}
                            width={267}
                            height={120}
                        />
                        <Typography variant={'body5'} color={'text_60'}>
                            {t('notifications.emptyMessage.unread')}
                        </Typography>
                    </Stack>
                )}
            </Box>
        </Box>
    );
}

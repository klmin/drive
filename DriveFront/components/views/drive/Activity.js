'use client';

import React, { useMemo } from 'react';
import { Box, Typography, Stack } from '@mui/material';
import { useTranslations } from 'next-intl';
import Image from 'next/image';
import palette from '@/theme/palette';
import { getFileByFileType, getItemExt } from '@/constants/FILES';
import dayjs from 'dayjs';
import StyledButton from '@/components/common/button/StyledButton';

const groupNotificationsByDate = (notifications, t) => {
    const grouped = {};
    const today = dayjs();

    console.log(notifications);

    notifications?.forEach(notification => {
        const itemDate = dayjs(notification.registerDate);
        let dateKey;

        if (itemDate.isSame(today, 'day')) {
            dateKey = t('common.dateFilter.today');
        } else if (itemDate.isSame(today.subtract(1, 'day'), 'day')) {
            dateKey = t('common.dateFilter.yesterday');
        } else if (today.diff(itemDate, 'day') < 7) {
            dateKey = t('common.dateFilter.last7Days');
        } else if (today.diff(itemDate, 'day') < 30) {
            dateKey = t('common.dateFilter.last30Days');
        } else if (itemDate.isSame(today, 'year')) {
            dateKey = t('common.dateFilter.thisYear');
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

function NotificationItem({ group, t }) {
    const firstItem = group[0];
    const { actionType, description, registerDate } = firstItem;
    const isGrouped = group.length > 1;

    const renderContent = () => {
        switch (actionType) {
            case 'UPLOAD':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t(isGrouped ? 'ActivityLog.UPLOAD.other' : 'ActivityLog.UPLOAD.one', {
                                userName: description?.userName,
                                count: group.length,
                            })}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}

                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'EDIT':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t(isGrouped ? 'ActivityLog.EDIT.other' : 'ActivityLog.EDIT.one', {
                                userName: description?.userName,
                                count: group.length,
                            })}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}

                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'COPY':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t(isGrouped ? 'ActivityLog.COPY.other' : 'ActivityLog.COPY.one', {
                                userName: description?.userName,
                                count: group.length,
                            })}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}

                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'MOVE':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t.rich(isGrouped ? 'ActivityLog.MOVE.other' : 'ActivityLog.MOVE.one', {
                                userName: description?.userName,
                                targetFolder: description?.targetFolder,
                                count: group.length,
                                b: chunks => (
                                    <Typography
                                        variant="subtitle2"
                                        component={'span'}
                                        color={'text_80'}
                                    >
                                        {chunks}
                                    </Typography>
                                ),
                            })}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}
                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'RENAME':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t.rich('ActivityLog.RENAME.one', {
                                userName: description?.userName,
                                beforeName: description?.beforeName,
                                afterName: description?.afterName,
                                b: chunks => (
                                    <Typography
                                        variant="subtitle2"
                                        component={'span'}
                                        color={'text_80'}
                                    >
                                        {chunks}
                                    </Typography>
                                ),
                            })}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}
                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'SHARE':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t.rich(
                                isGrouped ? 'ActivityLog.SHARE.other' : 'ActivityLog.SHARE.one',
                                {
                                    userName: description?.userName,
                                    count: group.length,
                                    b: chunks => (
                                        <Typography
                                            variant="subtitle2"
                                            component={'span'}
                                            color={'text_80'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                }
                            )}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}
                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'UNSHARE':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t.rich(
                                isGrouped ? 'ActivityLog.UNSHARE.other' : 'ActivityLog.UNSHARE.one',
                                {
                                    userName: description?.userName,
                                    count: group.length,
                                    b: chunks => (
                                        <Typography
                                            variant="subtitle2"
                                            component={'span'}
                                            color={'text_80'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                }
                            )}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}
                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'DELETE':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t.rich(
                                isGrouped ? 'ActivityLog.DELETE.other' : 'ActivityLog.DELETE.one',
                                {
                                    userName: description?.userName,
                                    count: group.length,
                                    b: chunks => (
                                        <Typography
                                            variant="subtitle2"
                                            component={'span'}
                                            color={'text_80'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                }
                            )}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}
                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            case 'RESTORE':
                return (
                    <Stack gap={3}>
                        <Typography variant="body7" component="span" color="text_80">
                            {t.rich(
                                isGrouped ? 'ActivityLog.RESTORE.other' : 'ActivityLog.RESTORE.one',
                                {
                                    userName: description?.userName,
                                    count: group.length,
                                    b: chunks => (
                                        <Typography
                                            variant="subtitle2"
                                            component={'span'}
                                            color={'text_80'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                }
                            )}
                        </Typography>

                        {isGrouped && (
                            <Stack sx={{ overflow: 'hidden' }} gap={0.5}>
                                {group.slice(0, 3).map((item, index) => (
                                    <StyledButton
                                        key={item.activityLogSeq}
                                        variant={'outlined'}
                                        color={'btn_border'}
                                        sx={{
                                            gap: 1.5,
                                            px: '8.3px',
                                            width: '100%',
                                            height: { xs: 32, md: 28 },
                                            justifyContent: 'flex-start',
                                        }}
                                    >
                                        <Image
                                            src={
                                                getFileByFileType(
                                                    getItemExt(item, item.description?.fileName)
                                                )?.src
                                            }
                                            alt="file"
                                            width={14}
                                            height={14}
                                        />
                                        <Typography variant="body9" color="text_80" noWrap>
                                            {item.description?.fileName}
                                        </Typography>
                                    </StyledButton>
                                ))}
                                {group.length > 3 && (
                                    <Typography
                                        variant="body9"
                                        color="text_tab_selected"
                                        sx={{
                                            mt: 0.5,
                                            backgroundColor: palette.white,
                                            textDecoration: 'underline',
                                        }}
                                    >
                                        외 {group.length - 3} 개
                                    </Typography>
                                )}
                            </Stack>
                        )}
                        <Typography variant="body9" color="text_60">
                            {registerDate}
                        </Typography>
                    </Stack>
                );

            default:
                return null;
        }
    };

    const getIconSrc = type => {
        const iconMap = {
            UPLOAD: '/common/img-img-activity-upload.svg',
            FILE_UPLOAD: '/drawer/img-img-activity-upload.svg',
            COPY: '/drawer/img-img-activity-copy.svg',
            MOVE: '/drawer/img-img-activity-move.svg',
            RENAME: '/drawer/img-img-activity-rename.svg',
            SHARE: '/drawer/img-img-activity-share.svg',
            EDIT: '/drawer/img-img-edit.svg',
            DELETE: '/drawer/img-img-activity-trash.svg',
            RESTORE: '/drawer/img-img-edit.svg',
        };
        return iconMap[type] || '/drawer/img-img-edit.svg'; // 기본 아이콘
    };

    return (
        <Stack direction={'row'} alignItems={'flex-start'} gap={2} sx={{ position: 'relative' }}>
            <Box sx={{ height: '100%' }}>
                <Box
                    sx={{
                        display: 'flex',
                        width: 36,
                        height: 36,
                        borderRadius: '50%',
                        alignItems: 'center',
                        justifyContent: 'center',
                        position: 'relative',
                        zIndex: 1,
                    }}
                >
                    <Image src={getIconSrc(actionType)} alt="icon" width={32} height={32} />
                </Box>
                <Box
                    sx={{
                        position: 'absolute',
                        top: 36,
                        left: 17,
                        height: `calc(100% - 36px)`,
                        borderLeft: `2px dotted #3a3d494d`,
                    }}
                />
            </Box>
            <Box sx={{ pt: 0.5, width: '100%' }}>{renderContent()}</Box>
        </Stack>
    );
}

const groupActivities = notifications => {
    if (!notifications) return [];

    const activityGroups = notifications.reduce((acc, notification) => {
        const key = `${notification.description?.userName}-${notification.actionType}-${notification.registerDate}`;
        if (!acc[key]) {
            acc[key] = [];
        }
        acc[key].push(notification);
        return acc;
    }, {});

    return Object.values(activityGroups);
};

export default function Activity({ notifications }) {
    const t = useTranslations();

    const groupedByDate = useMemo(
        () => groupNotificationsByDate(notifications, t),
        [notifications, t]
    );

    return (
        <Box sx={{ width: '100%', overflowY: 'scroll', maxHeight: '90vh', pb: 10 }}>
            {Object.entries(groupedByDate).map(([date, notificationsInDate], index) => {
                const activityGroups = groupActivities(notificationsInDate);

                return (
                    <Box key={date} mt={index === 0 ? 0 : 3}>
                        <Typography variant="body9" color="text_80">
                            {date}
                        </Typography>
                        <Stack spacing={6} mt={3}>
                            {activityGroups.map((group, index) => (
                                <NotificationItem key={index} group={group} t={t} />
                            ))}
                        </Stack>
                    </Box>
                );
            })}
        </Box>
    );
}

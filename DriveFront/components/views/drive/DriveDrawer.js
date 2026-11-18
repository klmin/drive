import { Box, Drawer, IconButton, Stack, Typography } from '@mui/material';
import Image from 'next/image';
import palette from '@/theme/palette';
import { getFileByFileType, getItemExt } from '@/constants/FILES';
import React, { useEffect, useMemo, useState } from 'react';
import StyledToggle from '@/components/common/button/StyledToggle';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import Details from '@/components/views/drive/Details';
import Activity from '@/components/views/drive/Activity';
import { router } from 'next/client';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';

const FileItem = ({ icon, name }) => (
    <Box
        display="flex"
        alignItems="center"
        gap={1}
        px={1.5}
        py={1}
        border={`1px solid ${palette.btn_border}`}
        borderRadius={1}
        bgcolor="#fff"
        maxWidth="100%"
    >
        <Image src={icon} alt="icon" width={18} height={18} />
        <Typography
            variant="body9"
            color={'text_80'}
            sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
        >
            {name}
        </Typography>
    </Box>
);

const PAGE_LABEL_KEYS = [
    { path: '/drive', key: 'common.label.myDrive' },
    { path: '/home', key: 'sidebar.home' },
    { path: '/recent', key: 'common.label.recent' },
    { path: '/starred', key: 'common.label.starred' },
    { path: '/shared', key: 'common.label.shared' },
    { path: '/trash', key: 'common.label.trash' },
];

export default function DriveDrawer({ drawerOpen, setDrawerOpen, data, checkItems, user }) {
    const t = useTranslations();

    const getPageLabel = (fallbackKey = 'sidebar.search') => {
        const match = PAGE_LABEL_KEYS.find(({ path }) => router.pathname.includes(path));
        return t(match ? match.key : fallbackKey);
    };
    const [toggle, setToggle] = useState('details');
    const [activityList, setActivityList] = useState([]);
    const drive = useMemo(() => (checkItems?.length === 1 ? checkItems?.[0] : {}), [checkItems]);

    const getActivityList = async () => {
        // if (!drive || Object.keys(drive).length === 0) return;
        // const res = await api.Get(`resources/${drive?.resourceSeq}/activities`, {});
        // if (res?.data?.data) {
        //     setActivityList(res?.data?.data);
        // }
    };

    useEffect(() => {
        if (checkItems.length === 1) getActivityList();
    }, [checkItems]);

    if (!drawerOpen) return null;

    return (
        <Box
            data-marquee-ignore // 여백 드래그 선택(marquee)이 Details 위에서 시작되지 않게
            onMouseDown={e => e.stopPropagation()} // 행 선택 해제 mousedown 버블 차단
            sx={{ position: 'relative', flex: 1 }}
        >
            <BaseTooltip title={t('tooltip.hideDetails')}>
                <IconButton
                    onClick={() => setDrawerOpen(!drawerOpen)}
                    sx={{
                        position: 'absolute',
                        top: 16,
                        left: -20,
                        zIndex: 1201,
                        display: {
                            xs: 'flex',
                            md: drawerOpen ? 'flex' : 'none',
                        },
                    }}
                >
                    <Image
                        src={'/common/ic-expander-right.svg'}
                        alt={'right'}
                        width={24}
                        height={24}
                    />
                </IconButton>
            </BaseTooltip>
            {/*pc*/}
            <Box
                sx={{
                    display: {
                        xs: 'none',
                        md: drawerOpen ? 'flex' : 'none',
                    },
                    flexDirection: 'column',
                    width: 320,
                    height: 'calc(100vh - 64px - var(--app-banner-height, 0px))',
                    pt: '48px',
                    px: 5,
                    borderLeft: `1px solid ${palette.divider_8}`,
                    position: 'relative',
                    zIndex: 1200,
                    background: 'white',
                }}
            >
                {checkItems.length === 1 ? (
                    <Stack direction="row" alignItems="center" gap={2}>
                        <Image
                            src={getFileByFileType(getItemExt(drive), drive?.resourceType)?.src}
                            alt={'/common/icon-file-ic-file-presentation-24.svg'}
                            width={24}
                            height={24}
                        />
                        <Typography variant="subtitle1" color={'text_80'} noWrap>
                            {drive?.resourceName}
                        </Typography>
                    </Stack>
                ) : (
                    <Typography variant="body3" color={'text_80'} noWrap sx={{ mb: '28px' }}>
                        {getPageLabel()}
                    </Typography>
                )}

                <Box py={checkItems.length === 1 && drive ? 6 : 0}>
                    {/* <StyledToggle
                        items={[
                            {
                                title: 'Details',
                                value: 'details',
                            },
                            {
                                title: 'Activity',
                                value: 'activity',
                            },
                        ]}
                        sx={{borderRadius: 999}}
                        value={toggle}
                        onChange={setToggle}
                    /> */}
                </Box>

                {checkItems?.length > 1 ? (
                    <Stack justifyContent={'center'} alignItems={'center'} mt={12}>
                        <Typography variant={'body7'} color={'text_60'}>
                            {t('dataDetail.multiSelected', { count: checkItems.length })}
                        </Typography>
                    </Stack>
                ) : Object.keys(drive).length === 0 ? (
                    <Stack justifyContent={'center'} alignItems={'center'} mt={12}>
                        <Typography variant={'body7'} color={'text_60'}>
                            {t('dataDetail.unselected')}
                        </Typography>
                    </Stack>
                ) : drive && toggle === 'details' ? (
                    <Details selectedDrive={drive} />
                ) : (
                    // drive && toggle === 'activity' && <Activity notifications={activityList} />
                    <></>
                )}
            </Box>

            {/*mo*/}
            <Drawer
                anchor="right"
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                variant={'temporary'}
                BackdropProps={{
                    sx: {
                        backgroundColor: 'transparent',
                    },
                }}
                sx={{
                    display: {
                        xs: 'flex',
                        md: 'none',
                    },
                    zIndex: 1202,
                    width: 320,
                    flexShrink: 0,
                    ['& .MuiDrawer-paper']: {
                        width: 320,
                        boxSizing: 'border-box',
                        // borderLeft: `1px solid ${palette.divider_8}`,
                        background: palette.white,
                        boxShadow: '-2px 0 6px 0 #0003',
                    },
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        width: 320,
                        pt: '32px',
                        px: 5,
                        borderLeft: `1px solid ${palette.divider_8}`,
                        position: 'relative',
                    }}
                >
                    {checkItems.length === 1 ? (
                        <Stack direction="row" alignItems="center" gap={2}>
                            <Image
                                src={getFileByFileType(getItemExt(drive), drive?.resourceType)?.src}
                                alt={'/common/icon-file-ic-file-presentation-24.svg'}
                                width={24}
                                height={24}
                            />
                            <Typography variant="subtitle1" color={'text_80'} noWrap>
                                {drive?.resourceName}
                            </Typography>
                        </Stack>
                    ) : (
                        <Typography
                            variant="subtitle1"
                            color={'text_80'}
                            noWrap
                            sx={{ mb: '28px' }}
                        >
                            {getPageLabel('common.label.trash')}
                        </Typography>
                    )}

                    <Box py={6}>
                        {/* <StyledToggle
                            items={[
                                {
                                    title: 'Details',
                                    value: 'details',
                                },
                                {
                                    title: 'Activity',
                                    value: 'activity',
                                },
                            ]}
                            sx={{ borderRadius: 999 }}
                            value={toggle}
                            onChange={setToggle}
                        /> */}
                    </Box>

                    {checkItems?.length > 1 ? (
                        <Stack justifyContent={'center'} alignItems={'center'} mt={12}>
                            <Typography variant={'body7'} color={'text_60'}>
                                {t('dataDetail.multiSelected', { count: checkItems.length })}
                            </Typography>
                        </Stack>
                    ) : Object.keys(drive).length === 0 ? (
                        <Stack justifyContent={'center'} alignItems={'center'} mt={12}>
                            <Typography variant={'body7'} color={'text_60'}>
                                {t('dataDetail.unselected')}
                            </Typography>
                        </Stack>
                    ) : drive && toggle === 'details' ? (
                        <Details selectedDrive={drive} />
                    ) : (
                        // drive && toggle === 'activity' && <Activity notifications={activityList} />
                        <></>
                    )}
                </Box>
            </Drawer>
        </Box>
    );
}

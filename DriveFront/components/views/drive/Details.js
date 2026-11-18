import {
    Box,
    Drawer,
    IconButton,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Stack,
    Toolbar,
    Typography,
} from '@mui/material';
import Image from 'next/image';
import StyledButton from '@/components/common/button/StyledButton';
import { Add } from '@mui/icons-material';
import palette from '@/theme/palette';
import { getItemExt } from '@/constants/FILES';
import Menus from '@/components/views/Menus';
import React, { useEffect, useState } from 'react';
import convertBytesToReadableSize from '@/utils/math';
import dayjs from 'dayjs';
import StyledTag from '@/components/common/chip/StyledTag';
import StyledToggle from '@/components/common/button/StyledToggle';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { router } from 'next/client';
import ProfileImage from '@/components/common/input/ProfileImage';
import useClientConfig from '@/hooks/useClientConfig';
import { useUser } from '@/context/UserContext';

export default function Details({ drawerOpen, setDrawerOpen, data, selectedDrive }) {
    const t = useTranslations();
    const { shareEnabled } = useClientConfig();
    const user = useUser();
    const isExternalTenant = Number(selectedDrive?.ownerTenantSeq) !== Number(user?.tenantSeq);

    const isFile = selectedDrive?.resourceType === 'FILE';
    const shareSeq = selectedDrive?.shareSeq;
    const ss = selectedDrive?.sharedByUserSeq
        ? selectedDrive?.sharedByUserSeq
        : selectedDrive?.userSeq;
    const [sharedUsers, setShareUsers] = useState([]);

    // 공유 받은 사용자 리스트
    const getShareUsers = async () => {
        const params = {
            ss: ss,
        };

        const res = await api.Get(`shares/${shareSeq}/users`, { params });
        if (res?.data?.code === 200) {
            setShareUsers(res?.data?.data);
        }
    };

    useEffect(() => {
        if (!shareSeq) {
            setShareUsers([]);
            return;
        }

        getShareUsers();
    }, [selectedDrive]);

    return (
        <Box display={'flex'} flexDirection="column" gap={6}>
            <Stack direction="column" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    {t('dataDetail.path')} :
                </Typography>
                <Typography variant="body7" color={'text_80'}>
                    {/** Detail 경로 시작이 공유 문서함인 경우 */}
                    {router.pathname.includes('/shared') ||
                    selectedDrive?.resourceLocation === 'SHARE'
                        ? t('common.label.shared')
                        : t('common.label.myDrive')}
                    /{selectedDrive?.fullPath}
                </Typography>
            </Stack>
            <Stack direction="row" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    {t('dataDetail.format')} :
                </Typography>
                <Typography variant="body7" color={'text_80'}>
                    {getItemExt(selectedDrive) || (isFile ? t('dataDetail.etc') : 'folder')}
                </Typography>
            </Stack>
            {isFile && (
                <Stack direction="row" gap={1}>
                    <Typography variant="subtitle3" color={'text_80'}>
                        {t('common.label.fileSize')} :
                    </Typography>
                    <Typography variant="body7" color={'text_80'}>
                        {convertBytesToReadableSize(selectedDrive?.sizeByte || 0)}
                    </Typography>
                </Stack>
            )}
            <Stack direction="column" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    {t('dataDetail.updateDate')} :
                </Typography>
                <Typography variant="body7" color={'text_80'}>
                    {dayjs(selectedDrive?.resourceUpdateDate).format('YYYY. M. D. H:mm')}
                </Typography>
            </Stack>
            <Stack direction="column" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    {t('dataDetail.registerDate')} :
                </Typography>
                <Typography variant="body7" color={'text_80'}>
                    {dayjs(selectedDrive?.resourceRegisterDate).format('YYYY. M. D. H:mm')}
                </Typography>
            </Stack>
            <Stack direction="column" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    {t('common.label.owner')} :
                </Typography>
                <Stack direction={'row'} alignItems={'center'} gap={2}>
                    <ProfileImage
                        src={api.getProfileImage(selectedDrive?.ownerSeq)}
                        alt={'share'}
                        width={20}
                        height={20}
                    />
                    <Typography variant="body7" color={'text_80'}>
                        {isExternalTenant
                            ? selectedDrive?.ownerName + `(${selectedDrive?.ownerTenantName})`
                            : selectedDrive?.ownerName}
                    </Typography>
                </Stack>
            </Stack>
            {/** 공유 기능이 활성화된 경우에만 공유받은 사용자 표시
             * 현재는 공유 기능을 비활성화하면 모든 공유가 해제되므로
             * 공유받은 사용자가 표시될 상황이 없음.
             */}
            {shareEnabled && sharedUsers.length > 0 && (
                <Stack direction="column" gap={1}>
                    <Typography variant="subtitle3" color={'text_80'}>
                        {t('dataDetail.share')} :
                    </Typography>
                    <Stack direction="row" alignItems="center" gap={2} flexWrap="wrap">
                        {sharedUsers.map((sharedUser, index) => (
                            <Stack key={index} direction={'row'} alignItems={'center'} gap={2}>
                                <ProfileImage
                                    src={api.getProfileImage(sharedUser?.userSeq)}
                                    alt={'share'}
                                    width={20}
                                    height={20}
                                />
                                <Typography variant="body7" color={'text_80'}>
                                    {/** isExternalTenant */}
                                    {sharedUser?.tenantSeq !== user?.tenantSeq
                                        ? sharedUser?.userName + `(${sharedUser?.tenantName})`
                                        : sharedUser?.userName}
                                    {index < sharedUsers.length - 1 ? ',' : ''}
                                </Typography>
                            </Stack>
                        ))}
                    </Stack>
                </Stack>
            )}
            {/* <Stack direction="column" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    {t('dataDetail.tag')} :
                </Typography>
                <Stack direction={'row'} alignItems={'center'} gap={0.5}>
                    <StyledTag label="# tagtag" variant="outlined" index={1}/>
                    <IconButton
                        sx={{
                            width: {xs: 32, md: 24},
                            height: {xs: 32, md: 24},
                            p: 0,
                            border: `1px solid ${palette.btn_border}`
                        }}>
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none"
                             xmlns="http://www.w3.org/2000/svg">
                            <path
                                d="M7.167 8.333H3.833a.483.483 0 0 1-.356-.143.484.484 0 0 1-.144-.357c0-.142.048-.26.144-.356a.484.484 0 0 1 .356-.144h3.334V4c0-.142.048-.26.143-.356a.484.484 0 0 1 .357-.144c.142 0 .26.048.356.144A.484.484 0 0 1 8.167 4v3.333H11.5c.142 0 .26.048.356.144a.484.484 0 0 1 .144.356c0 .142-.048.261-.144.357a.484.484 0 0 1-.356.143H8.167v3.334c0 .141-.048.26-.144.356a.484.484 0 0 1-.357.144.483.483 0 0 1-.356-.144.483.483 0 0 1-.143-.356V8.333z"
                                fill="#3A3D49" opacity=".8"/>
                        </svg>
                    </IconButton>
                </Stack>
            </Stack>
            <Stack direction="row" gap={1}>
                <Typography variant="subtitle3" color={'text_80'}>
                    타이틀 :
                </Typography>
                <Typography variant="body7" color={'text_80'}>
                    비공개
                </Typography>
            </Stack> */}
        </Box>
    );
}

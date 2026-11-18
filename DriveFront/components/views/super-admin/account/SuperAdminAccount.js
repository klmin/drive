import { Box, Menu, MenuItem, Stack, Typography } from '@mui/material';
import { ArrowFilledDown, ArrowFilledUp } from '@/icon/Arrow';
import Image from 'next/image';
import palette from '@/theme/palette';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import { useDialog } from '@/provider/DialogProvider';
import { setLanguage } from '@/utils/authTokens';
import ProfileImageEditor from '@/components/common/input/ProfileImageEditor';
import { FieldErrorMessage } from '@/components/common/input/StyledTextField';
import { useUser } from '@/context/UserContext';
import { ROUTES } from '@/constants/routes';
import MyApiKeySection from '@/components/common/MyApiKeySection';

const PasswordRoundBox = () => {
    return (
        <Box sx={{ width: 8, height: 8, backgroundColor: palette.text_80, borderRadius: '50%' }} />
    );
};

const UserInfoStack = ({ title, ele, isFirst, isLast }) => {
    const border = { md: `1px solid ${palette.control_border_normal}` };
    return (
        <Stack direction={{ md: 'row', xs: 'column' }}>
            <Typography
                sx={{
                    backgroundColor: { md: palette.bg_gray },
                    height: { md: 40 },
                    py: { md: '11px', xs: 0 },
                    pl: { md: '12px', xs: 0 },
                    width: { md: 200 },
                    minWidth: { md: 200 },
                    borderTop: { md: isFirst ? border.md : 'none' },
                    borderBottom: border,
                    borderLeft: border,
                    borderRight: border,
                    borderTopLeftRadius: { md: isFirst ? '6px' : 0 },
                    borderBottomLeftRadius: { md: isLast ? '6px' : 0 },
                    display: 'flex',
                    alignItems: 'center',
                }}
                variant={'body7'}
                color={'text_60'}
            >
                {title}
            </Typography>
            <Box
                sx={{
                    flex: 1,
                    height: { md: 40 },
                    borderTop: { md: isFirst ? border.md : 'none' },
                    borderBottom: border,
                    borderRight: border,
                    borderTopRightRadius: { md: isFirst ? '6px' : 0 },
                    borderBottomRightRadius: { md: isLast ? '6px' : 0 },
                    display: 'flex',
                    alignItems: 'center',
                    pl: { md: '24px', xs: 0 },
                    pr: { md: '16px', xs: 0 },
                }}
            >
                {ele}
            </Box>
        </Stack>
    );
};

export default function SuperAdminAccount() {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const user = useUser();
    const router = useRouter();
    const { openDialog } = useDialog();

    const [profileImageFile, setProfileImageFile] = useState(null);
    const [deleteProfileImage, setDeleteProfileImage] = useState(false);
    const [profileImageError, setProfileImageError] = useState('');
    const [lang, setLang] = useState('');
    const langTriggerRef = useRef(null);
    const [langAnchorEl, setLangAnchorEl] = useState(null);
    const langLabelMap = {
        ko: t('common.language.ko'),
        en: t('common.language.en'),
        es: t('common.language.es'),
    };
    const [screen, setScreen] = useState('');

    const currentProfileImageUrl = api.getProfileImage(user?.userSeq);
    const [previewUrl, setPreviewUrl] = useState(currentProfileImageUrl || '');

    useEffect(() => {
        if (!user) return;

        setLang(user?.userLanguage || 'en');
        setScreen(user?.defaultLandingPage || ROUTES.ADMIN_SETTINGS_SYSTEM_INFO);
        setProfileImageFile(null);
        setDeleteProfileImage(false);
        setProfileImageError('');
    }, [user]);

    const handleSelectProfileImage = useCallback(file => {
        setProfileImageFile(file);
        setPreviewUrl(URL.createObjectURL(file));
        setDeleteProfileImage(false);
        setProfileImageError('');
    }, []);

    const handleResetProfileImage = useCallback(() => {
        setProfileImageFile(null);
        setPreviewUrl('');
        setDeleteProfileImage(true);
        setProfileImageError('');
    }, []);

    // 서버 기준에 안 맞는 파일 — 기본 이미지로 변경과 같이 비우고 문구 표시
    const handleInvalidProfileImage = useCallback(message => {
        setProfileImageFile(null);
        setPreviewUrl('');
        setDeleteProfileImage(true);
        setProfileImageError(message);
    }, []);

    const handleSave = useCallback(async () => {
        openDialog('alert', {
            title: t('accountSettingSaveConfirmDialog.title'),
            description: t('accountSettingSaveConfirmDialog.text'),
            onClick: async () => {
                const res = await api.PatchForm('users', {
                    ul: lang,
                    pif: profileImageFile || null,
                    dlp: screen,
                    dpi: deleteProfileImage,
                });

                if (res?.status === 200) {
                    setLanguage('SUPER_ADMIN', lang);
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    router.reload();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    }, [lang, profileImageFile, deleteProfileImage, screen, openDialog, router, showSnackbar, t]);

    const handleOpenUpdatePassword = async () => {
        openDialog('changePassword', {
            item: user,
        });
    };

    return (
        <Box
            sx={{
                width: { xs: '100%', md: '100%' },
                maxWidth: { md: 840 },
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
            }}
        >
            {/* 계정 설정 */}
            <Stack direction={{ sm: 'row', xs: 'column' }} alignItems={'center'} gap={1}>
                <Stack
                    direction={'row'}
                    alignItems={'center'}
                    gap={6}
                    pb={{ xs: 6, md: 4 }}
                    pl={{ md: 2, xs: 0 }}
                    sx={{ width: '100%' }}
                >
                    <ProfileImageEditor
                        src={previewUrl}
                        onSelectImage={handleSelectProfileImage}
                        onResetImage={handleResetProfileImage}
                        onInvalidImage={handleInvalidProfileImage}
                    />
                    <Stack gap={2} pr={2} sx={{ width: '100%' }}>
                        <Typography variant="h4_h5">{user?.userName}</Typography>
                        {profileImageError && <FieldErrorMessage message={profileImageError} />}
                    </Stack>
                </Stack>
            </Stack>

            <Stack direction={{ xs: 'column' }} sx={{ gap: { xs: 5, md: 0 } }}>
                <UserInfoStack
                    isFirst
                    title={t('UserInfo.adminEmail')}
                    ele={
                        <Typography variant={'body5'} color={'text_100'} sx={{ width: '100%' }}>
                            {user?.userId}
                        </Typography>
                    }
                />

                <UserInfoStack
                    title={t('common.label.password')}
                    ele={
                        <Stack
                            direction={'row'}
                            justifyContent={'space-between'}
                            alignItems={'center'}
                            sx={{ width: '100%' }}
                        >
                            <Stack
                                direction={'row'}
                                sx={{ gap: 1, width: { xs: 'max-content', md: '100%' } }}
                            >
                                {Array(6)
                                    .fill(0)
                                    .map((_, i) => (
                                        <PasswordRoundBox key={i} />
                                    ))}
                            </Stack>

                            <StyledButton
                                variant={'text'}
                                sx={{ gap: '2px', px: '6px', py: 0, height: 24, minWidth: 0 }}
                                onClick={handleOpenUpdatePassword}
                            >
                                <Image
                                    src={'/setting/icon-ic-edit.svg'}
                                    alt={'edit'}
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant={'body9'}
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color={'text_tab_selected'}
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                <UserInfoStack
                    isLast
                    title={t('UserInfo.language')}
                    ele={
                        <Box display={'flex'} alignItems={'center'} sx={{ width: '100%' }}>
                            <Box
                                component="button"
                                type="button"
                                ref={langTriggerRef}
                                onClick={e => setLangAnchorEl(e.currentTarget)}
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                    p: 0,
                                    border: 0,
                                    backgroundColor: 'transparent',
                                    cursor: 'pointer',
                                }}
                            >
                                <Typography variant={'body5'} color={'text_80'}>
                                    {langLabelMap[lang] ?? t('common.language.auto')}
                                </Typography>
                                {langAnchorEl ? (
                                    <ArrowFilledUp width={14} height={14} opacity={'.6'} />
                                ) : (
                                    <ArrowFilledDown width={14} height={14} opacity={'.6'} />
                                )}
                            </Box>
                            <Menu
                                anchorEl={langAnchorEl}
                                open={Boolean(langAnchorEl)}
                                onClose={() => {
                                    setLangAnchorEl(null);
                                    langTriggerRef.current?.focus();
                                }}
                                PaperProps={{
                                    sx: {
                                        mt: 1,
                                        minWidth: 125,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                <MenuItem
                                    onClick={() => {
                                        setLang('ko');
                                        setLangAnchorEl(null);
                                        langTriggerRef.current?.focus();
                                    }}
                                >
                                    {t('common.language.ko')}
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setLang('en');
                                        setLangAnchorEl(null);
                                        langTriggerRef.current?.focus();
                                    }}
                                >
                                    {t('common.language.en')}
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setLang('es');
                                        setLangAnchorEl(null);
                                        langTriggerRef.current?.focus();
                                    }}
                                >
                                    {t('common.language.es')}
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setLang('auto');
                                        setLangAnchorEl(null);
                                        langTriggerRef.current?.focus();
                                    }}
                                >
                                    {t('common.language.auto')}
                                </MenuItem>
                            </Menu>
                        </Box>
                    }
                />
            </Stack>

            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    onClick={handleSave}
                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </Stack>

            <MyApiKeySection roleType="SUPER_ADMIN" />
        </Box>
    );
}

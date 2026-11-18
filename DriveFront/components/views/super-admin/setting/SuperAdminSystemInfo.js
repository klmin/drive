import { Box, Stack, Typography } from '@mui/material';
import Image from 'next/image';
import palette from '@/theme/palette';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import typography from '@/theme/typography';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useDialog } from '@/provider/DialogProvider';

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

export default function SuperAdminSystemInfo() {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { openDialog } = useDialog();

    const [baseDomainChange, setBaseDomainChange] = useState(false);
    const [baseDomain, setBaseDomain] = useState('');
    const [version, setVersion] = useState('');

    const baseDomainInputRef = useRef(null);

    const PLACEHOLDER_BASE_DOMAIN = t('superAdmin.setting.baseDomain.placeholder');

    const closeAllEditMode = useCallback(() => {
        setBaseDomainChange(false);
    }, []);

    const getBaseDomain = async () => {
        const res = await api.Get('super-admin/base-domain', {});

        if (res?.status === 200) {
            const data = res?.data?.data;
            setBaseDomain(data?.address || data?.baseDomain || '');
        }
    };

    const handleServerSave = useCallback(async () => {
        openDialog('alert', {
            title: t('adminServerSettingSaveConfirmDialog.title'),
            description: t('adminServerSettingSaveConfirmDialog.text'),
            onClick: async () => {
                const res = await api.Put('super-admin/base-domain', {
                    bd: baseDomain || '',
                });

                if (res?.status === 200) {
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    closeAllEditMode();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    }, [baseDomain, openDialog, showSnackbar, closeAllEditMode, t]);

    const getBuildInfo = async () => {
        const res = await api.Get('build/summary', {});

        if (res?.status === 200) {
            const build = res?.data;
            setVersion(build.buildInfo);
        }
    };

    useEffect(() => {
        getBaseDomain();
        getBuildInfo();
    }, []);

    return (
        <Box
            sx={{
                width: { xs: '100%', md: '100%' },
                maxWidth: { md: 840 },
                display: 'flex',
                flexDirection: 'column',
                gap: '16px',
                pb: '39px',
            }}
        >
            {/** 도메인 설정 / 제품 버전 */}
            <Stack direction={{ xs: 'column' }} sx={{ gap: { xs: 5, md: 0 } }}>
                <UserInfoStack
                    isFirst
                    title={t('superAdmin.setting.baseDomain.label')}
                    ele={
                        <Stack
                            direction={'row'}
                            justifyContent={'space-between'}
                            alignItems={'center'}
                            sx={{ width: '100%' }}
                        >
                            {baseDomainChange ? (
                                <StyledTextField
                                    ref={baseDomainInputRef}
                                    variant="outlined"
                                    value={baseDomain}
                                    placeholder={PLACEHOLDER_BASE_DOMAIN}
                                    onChange={e => setBaseDomain(e.target.value)}
                                    sx={{ width: '100%' }}
                                    InputProps={{
                                        sx: {
                                            ...typography.body5,
                                            width: '100%',
                                            backgroundColor: '#f5f5f5',
                                            '& fieldset': { border: 'none' },
                                        },
                                    }}
                                />
                            ) : (
                                <Typography
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                    variant={'body5'}
                                    color={baseDomain ? 'text_60' : 'text_40'}
                                >
                                    {baseDomain || PLACEHOLDER_BASE_DOMAIN}
                                </Typography>
                            )}

                            <StyledButton
                                variant={'text'}
                                sx={{ gap: '2px', px: '6px', py: 0, height: 24, minWidth: 0 }}
                                onClick={() => {
                                    setBaseDomainChange(!baseDomainChange);
                                    setTimeout(
                                        () =>
                                            baseDomainInputRef.current &&
                                            baseDomainInputRef.current.focus(),
                                        100
                                    );
                                }}
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
                    title={t('adminServer.version')}
                    ele={
                        <Typography sx={{ width: '100%' }} variant={'body5'} color={'text_60'}>
                            {version}
                        </Typography>
                    }
                />
            </Stack>

            {/** 서버 설정 저장 버튼 */}
            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    onClick={handleServerSave}
                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </Stack>
        </Box>
    );
}

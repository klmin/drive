import { Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import StyledSwitchButton from '@/components/common/button/StyledSwitchButton';
import React, { useCallback, useEffect, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useClientConfig from '@/hooks/useClientConfig';

export default function Authentication() {
    const t = useTranslations();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const [mfaStatus, setMfaStatus] = useState(null);
    // SYSTEM_MANAGED 는 2FA 사용 여부 저장이 막혀 있어(TENANT_011) 스위치·저장을 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();

    useEffect(() => {
        if (!user) return;
    }, [user]);

    const getMfaStatusInfo = async () => {
        const res = await api.Get('admins/system-config/mfa-status', {});

        if (res?.status === 200) {
            const data = res?.data?.data;
            setMfaStatus(data?.enabled || false);
        }
    };

    const handleMfaChange = event => {
        setMfaStatus(event.target.checked);
    };

    const handleMfaStatusSave = useCallback(async () => {
        openDialog('alert', {
            title: t(
                mfaStatus
                    ? 'adminMfaSettingSaveConfirmDialog.enable.title'
                    : 'adminMfaSettingSaveConfirmDialog.disabled.title'
            ),
            description: t(
                mfaStatus
                    ? 'adminMfaSettingSaveConfirmDialog.enable.text'
                    : 'adminMfaSettingSaveConfirmDialog.disabled.text'
            ),
            buttonName: t('common.button.save'),
            onClick: async () => {
                const res = await api.Put('admins/system-config/mfa-status', {
                    enabled: mfaStatus,
                });

                if (res?.data?.code === 200) {
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    getMfaStatusInfo();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    }, [mfaStatus]);

    useEffect(() => {
        getMfaStatusInfo();
    }, []);

    return (
        <Box
            sx={{
                maxWidth: 840,
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            <Box
                sx={{
                    pb: '20px',
                }}
            >
                <Typography noWrap variant={'h4_h5'} color={'text_100'}>
                    {t('adminMfa.title')}
                </Typography>
            </Box>

            {/** MFA 설정 */}
            <Stack
                direction={{
                    xs: 'column',
                }}
                sx={{
                    borderRadius: 2,
                    border: {
                        md: `1px solid ${palette.control_border_normal}`,
                        xs: 'none',
                    },
                    gap: {
                        xs: 5,
                        md: 0,
                    },
                }}
            >
                <Box
                    sx={{
                        border: `1px solid ${palette.control_border_normal}`,
                        borderRadius: 2,
                        p: 6,
                        background: palette.bg_white,
                    }}
                >
                    {/* Title + Desc */}
                    <Stack direction="column" spacing={2} sx={{ mb: 3 }}>
                        <Typography variant="h5" color="text_80">
                            {t('adminMfa.field')}
                        </Typography>
                        <Typography variant="body7" color="text_60">
                            {t('adminMfa.fieldDesc')}
                        </Typography>
                    </Stack>

                    {/* 기존 정보 UI */}
                    <Stack
                        direction={{ md: 'row', xs: 'column' }}
                        sx={{
                            // border: `1px solid ${palette.control_border_normal}`,
                            borderRadius: 2,
                            mb: 3,
                        }}
                    >
                        <Box
                            sx={{
                                pt: 3,
                                pb: 3,
                                width: '100%',
                            }}
                        >
                            <StyledSwitchButton
                                checked={mfaStatus}
                                onChange={handleMfaChange}
                                disabled={isSystemManaged}
                                label={
                                    mfaStatus
                                        ? t('common.status.enabled')
                                        : t('common.status.disabled')
                                }
                            />
                        </Box>
                    </Stack>

                    {/* 검정 영역 - 버튼 */}
                    <Stack
                        justifyContent={'flex-end'}
                        alignItems={{ xs: 'flex-end', md: 'flex-end' }}
                    >
                        <StyledButton
                            color={'btn_point_bg'}
                            variant="contained"
                            disabled={isSystemManaged}
                            onClick={handleMfaStatusSave}
                            sx={{ px: { xs: '29.5px', md: 2 }, minWidth: 80 }}
                        >
                            <Typography variant={'body9'} color={'white'}>
                                {t('common.button.save')}
                            </Typography>
                        </StyledButton>
                    </Stack>
                </Box>
            </Stack>
        </Box>
    );
}

import { FormControl, FormControlLabel, RadioGroup, Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useClientConfig from '@/hooks/useClientConfig';

const UserInfoStack = ({ title, text, ele, sx, id }) => {
    return (
        <Stack
            direction={{
                md: 'row',
                xs: 'column',
            }}
            sx={{
                borderBottom: {
                    md: id === 'language' ? '' : `1px solid ${palette.control_border_normal}`,
                    xs: 'none',
                },
            }}
        >
            <Typography
                sx={{
                    backgroundColor: {
                        md: palette.bg_gray,
                    },
                    p: {
                        md: 3,
                        xs: 0,
                    },
                    minWidth: 200,
                    borderRight: {
                        ...sx,
                        md: `1px solid ${palette.control_border_normal}`,
                    },
                }}
                variant={'body7'}
                color={'text_80'}
            >
                {title}
            </Typography>
            {text ? (
                <Typography
                    sx={{
                        py: {
                            md: 3,
                            xs: 0,
                        },
                        pl: {
                            md: 6,
                            xs: 0,
                        },
                        width: '100%',
                    }}
                    color={'text_100'}
                    variant={'body5'}
                >
                    {text}
                </Typography>
            ) : (
                ele
            )}
        </Stack>
    );
};

export default function Intellect({ loading, setLoading }) {
    const t = useTranslations();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    // SYSTEM_MANAGED 는 노트 설정 저장이 막혀 있어(TENANT_011) 선택·저장을 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();

    const [status, setStatus] = useState(null);

    useEffect(() => {
        if (!user) return;
    }, [user]);

    const getIntellectInfo = async () => {
        const res = await api.Get('admins/system-config/intellect', {});

        if (res?.status === 200) {
            const data = res?.data?.data;
            setStatus(data?.enabled || false);
            setLoading(!loading);
        }
    };

    const handleChange = event => {
        setStatus(event.target.value);
    };

    const handleIntellectSave = useCallback(async () => {
        openDialog('alert', {
            title: t('adminIntellectSettingSaveConfirmDialog.title'),
            description: t('adminIntellectSettingSaveConfirmDialog.text'),
            onClick: async () => {
                const res = await api.Put('admins/system-config/intellect', {
                    enabled: status || false,
                });

                if (res?.status === 200) {
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    await getIntellectInfo();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    }, [status]);

    useEffect(() => {
        getIntellectInfo();
    }, []);

    return (
        <Box
            id={'general-container'}
            sx={{
                maxWidth: 840,
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
                pt: {
                    md: 'calc(32px + 30px + 32px + 16px)',
                    xs: '87px',
                },
                //  상단 패딩 + tab메뉴 높이 + 기본 패딩 + 컨텐츠 패딩
            }}
        >
            <Stack
                direction={{
                    sm: 'row',
                    xs: 'column',
                }}
                alignItems={'center'}
                gap={1}
            >
                <Stack
                    direction={'row'}
                    alignItems={'center'}
                    gap={6}
                    pb={{
                        xs: 6,
                        md: 4,
                    }}
                    pl={{
                        md: 2,
                        xs: 0,
                    }}
                    sx={{ width: '100%' }}
                >
                    <Typography variant="body5" color={'text_80'} sx={{ whiteSpace: 'pre-line' }}>
                        {t(`adminIntellect.description`)}
                    </Typography>
                </Stack>
            </Stack>

            {/** 인텔릭트 설정 */}
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
                    {/* 노란색 영역 - Title + Desc */}
                    <Stack direction="column" spacing={0.5} sx={{ mb: 3 }}>
                        <Typography variant="body3" color="text_80">
                            {t('adminIntellect.field')}
                        </Typography>
                        <Typography variant="body7" color="text_60">
                            {t('adminIntellect.fieldDesc')}
                        </Typography>
                    </Stack>

                    {/* 빨간 영역 - 기존 정보 UI */}
                    <Stack
                        direction={{ md: 'row', xs: 'column' }}
                        sx={{
                            // border: `1px solid ${palette.control_border_normal}`,
                            borderRadius: 2,
                            mb: 3,
                        }}
                    >
                        <Box sx={{ pt: 3, pb: 3, width: '100%' }}>
                            <FormControl
                                disabled={isSystemManaged}
                                sx={isSystemManaged ? { opacity: 0.6 } : undefined}
                            >
                                <RadioGroup
                                    name={'intellect'}
                                    value={status}
                                    onChange={handleChange}
                                    sx={{
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 1.5, // 항목 간 간격
                                    }}
                                >
                                    <FormControlLabel
                                        value={true}
                                        control={<StyledRadioButton />}
                                        label={
                                            <Typography variant="body5" color="text_80">
                                                {t('common.status.enabled')}
                                            </Typography>
                                        }
                                    />
                                    <FormControlLabel
                                        value={false}
                                        control={<StyledRadioButton />}
                                        label={
                                            <Typography variant="body5" color="text_80">
                                                {t('common.status.disabled')}
                                            </Typography>
                                        }
                                    />
                                </RadioGroup>
                            </FormControl>
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
                            onClick={handleIntellectSave}
                            sx={{ px: { xs: '29.5px', md: 2 }, minWidth: 80 }}
                        >
                            <Typography variant={'body9'} color={'white'}>
                                {t('common.button.save')}
                            </Typography>
                        </StyledButton>
                    </Stack>
                </Box>
            </Stack>

            {/** 인텔릭트 버전 */}
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
                    mt: 6,
                }}
            >
                <UserInfoStack
                    title={t('adminIntellect.version')}
                    text={'Beta 1.0.0'}
                    sx={{ borderTopLeftRadius: '8px' }}
                />
            </Stack>
        </Box>
    );
}

import { Box, Divider, Stack, Typography } from '@mui/material';
import Switch from '@mui/material/Switch';
import { styled } from '@mui/material/styles';
import Image from 'next/image';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import palette from '@/theme/palette';
import React, { useCallback, useEffect, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useClientConfig from '@/hooks/useClientConfig';
import ShareTypeIcon from '@/components/common/share/ShareTypeIcon';

const PrimarySwitch = styled(Switch)({
    width: 30,
    height: 18,
    padding: 0,
    '& .MuiSwitch-switchBase': {
        padding: 3,
        '&.Mui-checked': {
            transform: 'translateX(12px)',
            '& + .MuiSwitch-track': {
                backgroundColor: palette.text_tab_selected,
                opacity: 1,
            },
        },
    },
    '& .MuiSwitch-thumb': {
        width: 12,
        height: 12,
        backgroundColor: palette.white,
        boxShadow: 'none',
    },
    '& .MuiSwitch-track': {
        borderRadius: 9,
        backgroundColor: palette.toggle_off,
        opacity: 1,
    },
});

function DisabledToggle({ checked }) {
    return (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <LockOutlinedIcon sx={{ fontSize: 16, color: '#898B92' }} />
            <Box sx={{ opacity: 0.4, pointerEvents: 'none' }}>
                <PrimarySwitch checked={checked} />
            </Box>
        </Box>
    );
}

export default function ShareFeature() {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const [isEnforced, setIsEnforced] = useState(null); // true면 전역 설정, false면 개별 설정
    // SYSTEM_MANAGED 는 공유 설정 저장이 막혀 있어(TENANT_011) 전역 강제와 같이 토글을 잠근다
    const { isSystemManaged } = useClientConfig();
    const toggleLocked = isEnforced || isSystemManaged;
    const [shareEnabled, setShareEnabled] = useState(null); // 공유 기능 on/off
    const [checkingShareExists, setCheckingShareExists] = useState(false); // 공유 데이터 조회 중 토글 잠금
    const [externalShareAllowed, setExternalShareAllowed] = useState(false); // 외부 공유 허용 on/off
    const [allowedScopes, setAllowedScopes] = useState({
        restricted: false, // 제한됨 on/off
        internal: false, // 조직 내부 공개 on/off
        public: false, // 전체 공개 on/off
    });
    const [initialSettings, setInitialSettings] = useState(null); // 조회 시점 값 (변경 여부 비교용)

    const getShareSettingInfo = async () => {
        const res = await api.Get('admins/share-settings', {});
        if (res?.data?.code === 200) {
            const data = res?.data?.data;
            const resolved = {
                shareEnabled: data?.shareEnabled,
                externalShareAllowed: data?.externalShareAllowed,
                allowedScopes: {
                    restricted: data?.allowSpecificUsers,
                    internal: data?.allowOrgLink,
                    public: data?.allowPublic,
                },
            };
            setIsEnforced(data?.isEnforced); // 전체 적용 여부
            setShareEnabled(resolved.shareEnabled);
            setExternalShareAllowed(resolved.externalShareAllowed);
            setAllowedScopes(resolved.allowedScopes);
            setInitialSettings(resolved);
        }
    };

    const saveShareSettings = async () => {
        const res = await api.Patch('admins/share-settings', {
            se: shareEnabled, // 공유 기능 on/off
            esa: externalShareAllowed, // 외부 공유 허용 on/off
            sua: allowedScopes.restricted, // 제한됨 on/off
            ola: allowedScopes.internal, // 조직 내부 공개 on/off
            pa: allowedScopes.public, // 전체 공개 on/off
        });
        if (res?.status === 200) {
            showSnackbar(t('adminSetting.save.success'), 'success');
            setInitialSettings({ shareEnabled, externalShareAllowed, allowedScopes });
        } else {
            showSnackbar(t('adminSetting.save.fail'), 'error');
        }
    };

    /** 공유 데이터 존재 여부. 조회 실패 시 안내를 띄우도록 true. */
    const getShareExists = async () => {
        const res = await api.Get('admins/system-config/shares/exists', {});
        return res?.data?.code === 200 ? res?.data?.data?.exists : true;
    };

    const handleShareEnabledChange = async e => {
        const checked = e.target.checked;
        if (checked) {
            setShareEnabled(true);
            const isUnconfigured =
                !externalShareAllowed &&
                !allowedScopes.restricted &&
                !allowedScopes.internal &&
                !allowedScopes.public;
            if (isUnconfigured) {
                setAllowedScopes({
                    restricted: true,
                    internal: true,
                    public: false,
                });
            }
            return;
        }
        setCheckingShareExists(true);
        let exists;
        try {
            exists = await getShareExists();
        } finally {
            setCheckingShareExists(false);
        }
        if (!exists) {
            setShareEnabled(false);
            return;
        }
        openDialog('alert', {
            title: t('admin.share.disabledInfoDialog.title'),
            description: t('admin.share.disabledInfoDialog.desc'),
            buttonName: t('admin.share.disabledInfoDialog.confirm'),
            onClick: () => {
                setShareEnabled(false);
            },
        });
    };

    const handleExternalShareChange = e => {
        const checked = e.target.checked;
        setExternalShareAllowed(checked);
        if (!checked) {
            setAllowedScopes(prev => ({ ...prev, public: false }));
        }
    };

    const handleAccessScopeChange = (key, e) => {
        const checked = e.target.checked;
        if (!checked) {
            const onCount = Object.values(allowedScopes).filter(Boolean).length;
            if (onCount <= 1) {
                showSnackbar(t('admin.share.policy.accessScope.minOneRequired'), 'error');
                return;
            }
        }
        setAllowedScopes(prev => ({
            ...prev,
            [key]: checked,
        }));
        if (key === 'public' && checked) {
            setExternalShareAllowed(true);
        }
    };

    const handleSave = useCallback(() => {
        const noAccessScopeSelected =
            shareEnabled &&
            !allowedScopes.restricted &&
            !allowedScopes.internal &&
            !allowedScopes.public;
        if (noAccessScopeSelected) {
            showSnackbar(t('admin.share.policy.accessScope.minOneRequired'), 'error');
            return;
        }
        openDialog('alert', {
            title: t('common.saveConfirmDialog.title'),
            buttonName: t('common.button.save'),
            onClick: saveShareSettings,
        });
    }, [shareEnabled, externalShareAllowed, allowedScopes]);

    useEffect(() => {
        getShareSettingInfo();
    }, []);

    const ACCESS_SCOPE_ITEMS = [
        {
            key: 'restricted',
            label: t('common.shareType.restricted'),
            desc: t('common.shareType.restrictedDesc'),
        },
        {
            key: 'internal',
            label: t('common.shareType.internal'),
            desc: t('common.shareType.internalDesc'),
        },
        {
            key: 'public',
            label: t('common.shareType.public'),
            desc: t('common.shareType.publicDesc'),
        },
    ];

    if (shareEnabled === null) {
        return null;
    }

    const isDirty =
        initialSettings !== null &&
        (shareEnabled !== initialSettings.shareEnabled ||
            externalShareAllowed !== initialSettings.externalShareAllowed ||
            allowedScopes.restricted !== initialSettings.allowedScopes.restricted ||
            allowedScopes.internal !== initialSettings.allowedScopes.internal ||
            allowedScopes.public !== initialSettings.allowedScopes.public);

    return (
        <Box
            sx={{
                width: { xs: '100%', md: '100%' },
                maxWidth: { xs: 472, md: 840 },
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-end',
                alignSelf: { xs: 'flex-start', md: 'auto' },
                gap: '56px',
            }}
        >
            {/* 헤더 */}
            <Box
                sx={{
                    alignSelf: 'stretch',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '12px',
                }}
            >
                <Typography sx={{ typography: 'h2', color: palette.text_100 }}>
                    {t('admin.share.setting.title')}
                </Typography>

                {/* 전역 설정 안내 info box */}
                {isEnforced && (
                    <Box
                        sx={{
                            alignSelf: 'stretch',
                            px: '12px',
                            py: '7px',
                            background: palette.btn_white_bg_hover,
                            borderRadius: '4px',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                        }}
                    >
                        <Image
                            src="/common/share/icon-ic-share-Info.svg"
                            alt="info"
                            width={16}
                            height={16}
                        />
                        <Typography sx={{ typography: 'body9', color: palette.text_80 }}>
                            {t('admin.share.globalSetting.infoBox')}
                        </Typography>
                    </Box>
                )}
            </Box>

            {/* 본문 */}
            <Box
                sx={{
                    alignSelf: 'stretch',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'flex-end',
                    gap: '32px',
                }}
            >
                <Box
                    sx={{
                        alignSelf: 'stretch',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '24px',
                    }}
                >
                    {/* 공유 기능 사용 설정 */}
                    <Box
                        sx={{
                            alignSelf: 'stretch',
                            borderRadius: '6px',
                            display: 'inline-flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}
                    >
                        <Box
                            sx={{
                                flex: '1 1 0',
                                display: 'inline-flex',
                                flexDirection: 'column',
                                gap: '4px',
                            }}
                        >
                            <Typography sx={{ typography: 'body4', color: palette.text_80 }}>
                                {t('admin.share.setting.field')}
                            </Typography>
                            <Typography sx={{ typography: 'body9', color: palette.text_60 }}>
                                {shareEnabled
                                    ? t('admin.share.setting.descEnabled')
                                    : t('admin.share.setting.descDisabled')}
                            </Typography>
                        </Box>
                        {toggleLocked ? (
                            <DisabledToggle checked={shareEnabled} />
                        ) : (
                            <PrimarySwitch
                                checked={shareEnabled}
                                disabled={checkingShareExists}
                                onChange={handleShareEnabledChange}
                            />
                        )}
                    </Box>

                    <Divider sx={{ borderColor: palette.divider_8 }} />

                    {/* 외부 공유 허용 */}
                    {shareEnabled && (
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                borderRadius: '6px',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '24px',
                            }}
                        >
                            <Box
                                sx={{
                                    flex: '1 1 0',
                                    display: 'inline-flex',
                                    flexDirection: 'column',
                                    gap: '4px',
                                }}
                            >
                                <Typography sx={{ typography: 'body4', color: palette.text_80 }}>
                                    {t('admin.share.policy.externalShare.title')}
                                </Typography>
                                <Typography sx={{ typography: 'body9', color: palette.text_60 }}>
                                    {t('admin.share.policy.externalShare.desc')}
                                </Typography>
                            </Box>
                            {toggleLocked ? (
                                <DisabledToggle checked={externalShareAllowed} />
                            ) : (
                                <PrimarySwitch
                                    checked={externalShareAllowed}
                                    onChange={handleExternalShareChange}
                                />
                            )}
                        </Box>
                    )}

                    {shareEnabled && <Divider sx={{ borderColor: palette.divider_8 }} />}

                    {/* 접근 권한 허용 범위 */}
                    {shareEnabled && (
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                borderRadius: '6px',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '18px',
                            }}
                        >
                            <Typography sx={{ typography: 'body4', color: palette.text_80 }}>
                                {t('admin.share.policy.accessScope.title')}
                            </Typography>
                            <Box
                                sx={{
                                    alignSelf: 'stretch',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '20px',
                                    pb: '8px',
                                }}
                            >
                                {ACCESS_SCOPE_ITEMS.map(({ key, label, desc }) => (
                                    <Box
                                        key={key}
                                        sx={{
                                            alignSelf: 'stretch',
                                            borderRadius: '6px',
                                            display: 'inline-flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center',
                                        }}
                                    >
                                        <Stack
                                            direction="row"
                                            alignItems="center"
                                            gap="12px"
                                            sx={{ flex: '1 1 0' }}
                                        >
                                            <ShareTypeIcon type={key} />
                                            <Box
                                                sx={{
                                                    flex: '1 1 0',
                                                    display: 'flex',
                                                    flexDirection: 'column',
                                                    gap: '4px',
                                                }}
                                            >
                                                <Typography
                                                    sx={{
                                                        typography: 'body6',
                                                        color: palette.text_80,
                                                    }}
                                                >
                                                    {label}
                                                </Typography>
                                                <Typography
                                                    sx={{
                                                        typography: 'body9',
                                                        color: palette.text_60,
                                                    }}
                                                >
                                                    {desc}
                                                </Typography>
                                            </Box>
                                        </Stack>
                                        {toggleLocked ? (
                                            <DisabledToggle checked={allowedScopes[key]} />
                                        ) : key === 'public' && !externalShareAllowed ? (
                                            <Box sx={{ opacity: 0.4, pointerEvents: 'none' }}>
                                                <PrimarySwitch checked={false} />
                                            </Box>
                                        ) : (
                                            <PrimarySwitch
                                                checked={allowedScopes[key]}
                                                onChange={e => handleAccessScopeChange(key, e)}
                                            />
                                        )}
                                    </Box>
                                ))}
                            </Box>
                        </Box>
                    )}

                    {shareEnabled && <Divider sx={{ borderColor: palette.divider_8 }} />}
                </Box>

                {/* 저장 버튼 - 전역 설정이 아닐 때만 표시 */}
                {!isEnforced && (
                    <Stack
                        direction={{ xs: 'row', md: 'row-reverse' }}
                        justifyContent="flex-start"
                        alignItems="center"
                        gap="4px"
                        sx={{ alignSelf: 'stretch' }}
                    >
                        <StyledButton
                            onClick={handleSave}
                            disabled={!isDirty || isSystemManaged}
                            sx={{
                                width: { xs: '80px', md: 'auto' },
                                height: { xs: '32px', md: '24px' },
                                px: { xs: '16px', md: '8px' },
                                py: '8px',
                                background: palette.btn_point_bg,
                                borderRadius: '4px',
                                minWidth: 'auto',
                                overflow: 'hidden',
                                gap: '7px',
                                '&:hover': { background: palette.btn_point_bg },
                            }}
                        >
                            <Typography sx={{ typography: 'body9', color: palette.white }}>
                                {t('common.button.save')}
                            </Typography>
                        </StyledButton>
                    </Stack>
                )}
            </Box>
        </Box>
    );
}

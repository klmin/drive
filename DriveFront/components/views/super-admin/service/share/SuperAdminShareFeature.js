import {
    Box,
    Divider,
    FormControl,
    FormControlLabel,
    RadioGroup,
    Stack,
    Typography,
} from '@mui/material';
import Switch from '@mui/material/Switch';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import React, { useCallback, useEffect, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
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

const radioSx = {
    color: palette.divider_12,
    '&.Mui-checked': {
        color: palette.text_80,
    },
    p: '8px',
};

export default function SuperAdminShareFeature() {
    const t = useTranslations();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const [scope, setScope] = useState('global'); // 'global'이면 전체 적용, 'tenant'이면 테넌트별 개별 설정
    // 아래 초기값을 변경하여 화면 테스트 가능 (API 연동 후 getShareSettingInfo에서 실제 값으로 대체됨)
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
        const res = await api.Get('super-admin/share-settings', {});
        if (res?.data?.code === 200) {
            const data = res?.data?.data;
            const resolved = {
                scope: data?.isEnforced ? 'global' : 'tenant',
                shareEnabled: data?.shareEnabled,
                externalShareAllowed: data?.externalShareAllowed,
                allowedScopes: {
                    restricted: data?.allowSpecificUsers,
                    internal: data?.allowOrgLink,
                    public: data?.allowPublic,
                },
            };
            setScope(resolved.scope);
            setShareEnabled(resolved.shareEnabled);
            setExternalShareAllowed(resolved.externalShareAllowed);
            setAllowedScopes(resolved.allowedScopes);
            setInitialSettings(resolved);
        }
    };

    const saveShareSettings = async () => {
        const res = await api.Put('super-admin/share-settings', {
            e: scope === 'global', // 전체 적용 on/off
            se: shareEnabled, // 공유 기능 on/off
            esa: externalShareAllowed, // 외부 공유 허용 on/off
            sua: allowedScopes.restricted, // 제한됨 on/off
            ola: allowedScopes.internal, // 조직 내부 공개 on/off
            pa: allowedScopes.public, // 전체 공개 on/off
        });
        if (res?.status === 200) {
            showSnackbar(t('adminSetting.save.success'), 'success');
            setInitialSettings({ scope, shareEnabled, externalShareAllowed, allowedScopes });
        } else {
            showSnackbar(t('adminSetting.save.fail'), 'error');
        }
    };

    const handleScopeChange = e => {
        const newScope = e.target.value;
        const isGlobal = newScope === 'global';
        openDialog('alert', {
            title: t(
                isGlobal
                    ? 'superAdmin.featureSetting.globalConfirmDialog.title'
                    : 'superAdmin.featureSetting.individualConfirmDialog.title'
            ),
            description: t(
                isGlobal
                    ? 'superAdmin.featureSetting.globalConfirmDialog.desc'
                    : 'superAdmin.featureSetting.individualConfirmDialog.desc'
            ),
            buttonName: t('common.button.confirm'),
            onClick: () => setScope(newScope),
        });
    };

    /** 공유 데이터 존재 여부. 조회 실패 시 안내를 띄우도록 true. */
    const getShareExists = async () => {
        const res = await api.Get('super-admin/share-settings/shares/exists', {});
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
            scope === 'global' &&
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
    }, [scope, shareEnabled, externalShareAllowed, allowedScopes]);

    useEffect(() => {
        if (!user) return;
    }, [user]);

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
        (scope !== initialSettings.scope ||
            shareEnabled !== initialSettings.shareEnabled ||
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
                    gap: '8px',
                }}
            >
                <Typography sx={{ typography: 'h2', color: palette.text_100 }}>
                    {t('superAdmin.featureSetting.shareTitle')}
                </Typography>
                <Typography sx={{ typography: 'body6', color: palette.text_80 }}>
                    {t('superAdmin.featureSetting.infoBox')}
                </Typography>
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
                    {/* 적용 범위 */}
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
                                {t('superAdmin.featureSetting.scope.title')}
                            </Typography>
                            <Typography sx={{ typography: 'body9', color: palette.text_60 }}>
                                {t('superAdmin.featureSetting.scope.shareDesc')}
                            </Typography>
                        </Box>

                        <FormControl>
                            <RadioGroup
                                row
                                value={scope}
                                onChange={handleScopeChange}
                                sx={{ gap: '20px' }}
                            >
                                <FormControlLabel
                                    value="global"
                                    control={<StyledRadioButton value="global" sx={radioSx} />}
                                    label={
                                        <Typography
                                            sx={{ typography: 'body5', color: palette.text_80 }}
                                        >
                                            {t('superAdmin.featureSetting.scope.global')}
                                        </Typography>
                                    }
                                    sx={{ m: 0 }}
                                />
                                <FormControlLabel
                                    value="tenant"
                                    control={<StyledRadioButton value="tenant" sx={radioSx} />}
                                    label={
                                        <Typography
                                            sx={{ typography: 'body5', color: palette.text_80 }}
                                        >
                                            {t('superAdmin.featureSetting.scope.individual')}
                                        </Typography>
                                    }
                                    sx={{ m: 0 }}
                                />
                            </RadioGroup>
                        </FormControl>
                    </Box>

                    {scope === 'global' && <Divider sx={{ borderColor: palette.divider_8 }} />}

                    {/* 공유 기능 사용 설정 */}
                    {scope === 'global' && (
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
                                    {t('admin.share.setting.field')}
                                </Typography>
                                <Typography sx={{ typography: 'body9', color: palette.text_60 }}>
                                    {shareEnabled
                                        ? t('admin.share.setting.descEnabled')
                                        : t('admin.share.setting.descDisabled')}
                                </Typography>
                            </Box>
                            <PrimarySwitch
                                checked={shareEnabled}
                                disabled={checkingShareExists}
                                onChange={handleShareEnabledChange}
                            />
                        </Box>
                    )}

                    {scope === 'global' && <Divider sx={{ borderColor: palette.divider_8 }} />}

                    {/* 외부 공유 허용 */}
                    {scope === 'global' && shareEnabled && (
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
                            <PrimarySwitch
                                checked={externalShareAllowed}
                                onChange={handleExternalShareChange}
                            />
                        </Box>
                    )}

                    {scope === 'global' && shareEnabled && (
                        <Divider sx={{ borderColor: palette.divider_8 }} />
                    )}

                    {/* 접근 권한 허용 범위 */}
                    {scope === 'global' && shareEnabled && (
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
                                            alignItems: 'center',
                                            gap: '24px',
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                flex: '1 1 0',
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: '12px',
                                            }}
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
                                        </Box>
                                        {key === 'public' && !externalShareAllowed ? (
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

                    {scope === 'global' && shareEnabled && (
                        <Divider sx={{ borderColor: palette.divider_8 }} />
                    )}
                </Box>

                {/* 저장 버튼 */}
                <Stack
                    direction={{ xs: 'row', md: 'row-reverse' }}
                    justifyContent="flex-start"
                    alignItems="center"
                    gap="4px"
                    sx={{ alignSelf: 'stretch' }}
                >
                    <StyledButton
                        onClick={handleSave}
                        disabled={!isDirty}
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
                        <Typography
                            sx={{
                                typography: 'body9',
                                color: palette.white,
                            }}
                        >
                            {t('common.button.save')}
                        </Typography>
                    </StyledButton>
                </Stack>
            </Box>
        </Box>
    );
}

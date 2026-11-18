import {
    Box,
    Divider,
    FormControl,
    FormControlLabel,
    RadioGroup,
    Stack,
    Typography,
} from '@mui/material';
import palette from '@/theme/palette';
import React, { useCallback, useEffect, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { emptyCheck } from '@/utils/userUtils';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import { useUser } from '@/context/UserContext';
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
import SuperAdminJwtSettings from './SuperAdminJwtSettings';
import SuperAdminOidcSettings from './SuperAdminOidcSettings';

const INITIAL_FORM = {
    redirectUri: '',
    ssoProviderSeq: '',
    displayName: '',
    status: '',
    issuer: '',
    audience: '',
    keySourceType: 'public_key',
    publicKey: '',
    jwksUri: '',
    emailClaimName: '',
    errorRedirectUrl: '',
};

const radioSx = {
    color: palette.divider_12,
    '&.Mui-checked': {
        color: palette.text_80,
    },
    p: '8px',
};

export default function SuperAdminLoginSettings() {
    const t = useTranslations();
    const router = useRouter();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const [loginType, setLoginType] = useState(null);
    const [savedLoginType, setSavedLoginType] = useState(null);
    const [formData, setFormData] = useState(INITIAL_FORM);
    const [oidcProviders, setOidcProviders] = useState([]);
    const [isDisabled, setIsDisabled] = useState(true);
    const [scope, setScope] = useState(null); // 적용 상태 global: 전체 적용, tenant: 개별 적용
    const [initialSettings, setInitialSettings] = useState(null); // 조회 시점 값 (변경 여부 비교용)

    const [errorMessageDisplayName, setErrorMessageDisplayName] = useState('');
    const [errorMessageIssuer, setErrorMessageIssuer] = useState('');
    const [errorMessagePublicKey, setErrorMessagePublicKey] = useState('');
    const [errorMessageJwksUri, setErrorMessageJwksUri] = useState('');
    const [errorMessageErrorRedirectUrl, setErrorMessageErrorRedirectUrl] = useState('');

    const isJwt = () => loginType === 'jwt';
    const isOidc = () => loginType === 'oidc';

    const getLoginSettingInfo = async () => {
        const res = await api.Get('super-admin/login-settings', {});
        if (res?.data?.code === 200) {
            const data = res?.data?.data;
            const resolvedScope = data?.isEnforced ? 'global' : 'tenant';
            setLoginType(data?.loginType);
            setSavedLoginType(data?.loginType);
            setScope(resolvedScope);

            let resolvedFormData = INITIAL_FORM;
            let resolvedOidcProviders = [];

            if (data?.loginType === 'jwt') {
                const configs = data?.configs[0];
                resolvedFormData = {
                    ssoProviderSeq: configs?.ssoProviderSeq,
                    displayName: configs?.displayName ?? '',
                    status: configs?.status,
                    issuer: configs?.issuer ?? '',
                    audience: configs?.audience ?? '',
                    keySourceType: configs?.keySourceType ?? 'public_key',
                    publicKey: configs?.publicKey ?? '',
                    jwksUri: configs?.jwksUri ?? '',
                    emailClaimName: configs?.emailClaimName ?? '',
                    errorRedirectUrl: configs?.errorRedirectUrl ?? '',
                };
                setFormData(resolvedFormData);
            } else if (data?.loginType === 'oidc') {
                resolvedOidcProviders = (data?.configs ?? []).map(c => ({
                    ssoProviderSeq: c.ssoProviderSeq,
                    displayName: c.displayName ?? '',
                    status: c.status,
                    issuerUrl: c.issuerUrl ?? '',
                    clientId: c.clientId ?? '',
                    clientSecret: c.clientSecret ?? '',
                    scopes: c.scopes ?? '',
                    allowIdpInitiated: c.allowIdpInitiated ?? false,
                    registerDate: c.registerDate ?? '',
                }));
                setOidcProviders(resolvedOidcProviders);
            }

            setInitialSettings({
                scope: resolvedScope,
                loginType: data?.loginType,
                formData: resolvedFormData,
                oidcProviders: resolvedOidcProviders,
            });
        }
    };

    const getProviderConfig = async (scope, loginType) => {
        const res = await api.Get('super-admin/login-settings', {
            params: { s: scope, l: loginType },
        });
        if (res?.data?.code !== 200) return;

        const data = res?.data?.data;
        setScope(scope);
        setLoginType(loginType);

        if (loginType === 'jwt') {
            const configs = data?.configs[0];
            setFormData({
                ssoProviderSeq: configs?.ssoProviderSeq,
                displayName: configs?.displayName ?? '',
                status: configs?.status,
                issuer: configs?.issuer ?? '',
                audience: configs?.audience ?? '',
                keySourceType: configs?.keySourceType ?? 'public_key',
                publicKey: configs?.publicKey ?? '',
                jwksUri: configs?.jwksUri ?? '',
                emailClaimName: configs?.emailClaimName ?? '',
                errorRedirectUrl: configs?.errorRedirectUrl ?? '',
            });
        } else if (loginType === 'oidc') {
            setOidcProviders(
                (data?.configs ?? []).map(c => ({
                    ssoProviderSeq: c.ssoProviderSeq,
                    displayName: c.displayName ?? '',
                    status: c.status,
                    issuerUrl: c.issuerUrl ?? '',
                    clientId: c.clientId ?? '',
                    clientSecret: c.clientSecret ?? '',
                    scopes: c.scopes ?? '',
                    allowIdpInitiated: c.allowIdpInitiated ?? false,
                    registerDate: c.registerDate ?? '',
                }))
            );
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
            onClick: async () => {
                getProviderConfig(newScope, 'local');
            },
        });
    };

    const handleLoginTypeChange = event => {
        getProviderConfig('global', event.target.value);
    };

    const buildLocalPayload = () => ({ l: 'local', ie: true });

    const buildJwtPayload = () => ({
        l: 'jwt',
        ie: true,
        ps: [
            {
                ssoProviderSeq: formData.ssoProviderSeq || null,
                displayName: formData.displayName,
                config: {
                    type: 'jwt',
                    issuer: formData.issuer,
                    audience: formData.audience || null,
                    keySourceType: formData.keySourceType,
                    publicKey: formData.keySourceType === 'public_key' ? formData.publicKey : null,
                    jwksUri: formData.keySourceType === 'jwks' ? formData.jwksUri : null,
                    emailClaimName: formData.emailClaimName || null,
                    errorRedirectUrl: formData.errorRedirectUrl || null,
                },
            },
        ],
    });

    const buildOidcPayload = () => ({
        l: 'oidc',
        ie: true,
        ps: oidcProviders.map(p => ({
            ssoProviderSeq: p.ssoProviderSeq || null,
            displayName: p.displayName,
            status: p.status,
            config: {
                type: 'oidc',
                issuerUrl: p.issuerUrl,
                clientId: p.clientId,
                clientSecret: p.clientSecret || null,
            },
        })),
    });

    const saveLoginSettings = async () => {
        const payload =
            scope !== 'global'
                ? { ie: false }
                : isJwt()
                  ? buildJwtPayload()
                  : isOidc()
                    ? buildOidcPayload()
                    : buildLocalPayload();

        const res = await api.Put('super-admin/login-settings', payload);
        if (res?.data?.code === 200) {
            getLoginSettingInfo();
            showSnackbar(t('adminSetting.save.success'), 'success');
        } else {
            showSnackbar(t('adminSetting.save.fail'), 'error');
        }
    };

    const handleSave = useCallback(() => {
        if (scope !== 'global') {
            openDialog('alert', {
                title: t('common.saveConfirmDialog.title'),
                buttonName: t('common.button.save'),
                onClick: saveLoginSettings,
            });
            return;
        }
        openDialog('alert', {
            title: isJwt()
                ? t('adminLoginSettingSaveConfirmDialog.jwtTitle')
                : isOidc()
                  ? t('admin.loginSetting.oidcConfirmDialog.title')
                  : t('adminLoginSettingSaveConfirmDialog.localTitle'),
            description: isJwt()
                ? t('adminLoginSettingSaveConfirmDialog.jwtDesc')
                : isOidc()
                  ? t('admin.loginSetting.oidcConfirmDialog.desc')
                  : t('adminLoginSettingSaveConfirmDialog.localDesc'),
            buttonName: t('common.button.change'),
            onClick: saveLoginSettings,
        });
    }, [scope, loginType, formData, oidcProviders]);

    const handleChange = e => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        try {
            if (name === 'displayName') {
                emptyCheck(value, t);
                setErrorMessageDisplayName('');
            }
            if (name === 'issuer') {
                emptyCheck(value, t);
                setErrorMessageIssuer('');
            }
            if (name === 'publicKey') {
                emptyCheck(value, t);
                setErrorMessagePublicKey('');
            }
            if (name === 'jwksUri') {
                emptyCheck(value, t);
                setErrorMessageJwksUri('');
            }
            if (name === 'errorRedirectUrl') {
                emptyCheck(value, t);
                setErrorMessageErrorRedirectUrl('');
            }
        } catch (error) {
            if (name === 'displayName') setErrorMessageDisplayName(error.message);
            if (name === 'issuer') setErrorMessageIssuer(error.message);
            if (name === 'publicKey') setErrorMessagePublicKey(error.message);
            if (name === 'jwksUri') setErrorMessageJwksUri(error.message);
            if (name === 'errorRedirectUrl') setErrorMessageErrorRedirectUrl(error.message);
        }
    };

    useEffect(() => {
        const commonValid = errorMessageDisplayName === '' && formData.displayName.trim() !== '';

        const jwtKeyValid =
            formData.keySourceType === 'public_key'
                ? errorMessagePublicKey === '' && formData.publicKey.trim() !== ''
                : errorMessageJwksUri === '' && formData.jwksUri.trim() !== '';

        const jwtValid =
            isJwt() &&
            errorMessageIssuer === '' &&
            formData.issuer.trim() !== '' &&
            jwtKeyValid &&
            errorMessageErrorRedirectUrl === '' &&
            formData.errorRedirectUrl.trim() !== '';

        if (isJwt()) {
            setIsDisabled(!(commonValid && jwtValid));
        } else if (isOidc()) {
            setIsDisabled(oidcProviders.length === 0);
        } else {
            setIsDisabled(false);
        }
    }, [
        loginType,
        formData,
        oidcProviders,
        errorMessageDisplayName,
        errorMessageIssuer,
        errorMessagePublicKey,
        errorMessageJwksUri,
        errorMessageErrorRedirectUrl,
    ]);

    useEffect(() => {
        if (!user) return;
    }, [user]);

    useEffect(() => {
        getLoginSettingInfo();
    }, []);

    const isDirty =
        initialSettings !== null &&
        (scope !== initialSettings.scope ||
            (scope === 'global' &&
                (loginType !== initialSettings.loginType ||
                    (isJwt() &&
                        JSON.stringify(formData) !== JSON.stringify(initialSettings.formData)) ||
                    (isOidc() &&
                        JSON.stringify(oidcProviders) !==
                            JSON.stringify(initialSettings.oidcProviders)))));

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
                sx={{ alignSelf: 'stretch', display: 'flex', flexDirection: 'column', gap: '8px' }}
            >
                <Typography sx={{ typography: 'h2', color: palette.text_100 }}>
                    {t('superAdmin.featureSetting.loginTitle')}
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
                                {t('superAdmin.featureSetting.scope.loginDesc')}
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

                    {/* 로그인 방식 */}
                    {scope === 'global' && (
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '14px',
                            }}
                        >
                            <Typography sx={{ typography: 'body4', color: palette.text_80 }}>
                                {t('adminLogin.field')}
                            </Typography>

                            <FormControl sx={{ width: '100%' }}>
                                <RadioGroup
                                    name="loginAuth"
                                    value={loginType}
                                    onChange={handleLoginTypeChange}
                                    sx={{ display: 'flex', flexDirection: 'column', gap: '4px' }}
                                >
                                    {[
                                        {
                                            value: 'local',
                                            label: t('adminLogin.localAuth'),
                                            desc: t('adminLogin.localFieldDesc'),
                                        },
                                        {
                                            value: 'oidc',
                                            label: t('admin.loginSetting.loginMethod.oidc'),
                                            desc: t('admin.loginSetting.oidcInfoBox.desc'),
                                        },
                                        {
                                            value: 'jwt',
                                            label: t('adminLogin.jwtAuth'),
                                            desc: t('adminLogin.jwtFieldDesc'),
                                        },
                                    ].map(({ value, label, desc }) => (
                                        <FormControlLabel
                                            key={value}
                                            value={value}
                                            control={<StyledRadioButton value={value} />}
                                            sx={{
                                                m: 0,
                                                p: '8px',
                                                borderRadius: '40px',
                                                gap: '8px',
                                                alignItems: 'flex-start',
                                            }}
                                            label={
                                                <Stack direction="column" gap="8px">
                                                    <Stack
                                                        direction="row"
                                                        alignItems="center"
                                                        gap="6px"
                                                        sx={{ mt: '1px' }}
                                                    >
                                                        <Typography variant="body5" color="text_80">
                                                            {label}
                                                        </Typography>
                                                        {savedLoginType === value && (
                                                            <Box
                                                                sx={{
                                                                    px: '8px',
                                                                    py: '2px',
                                                                    background: `${palette.text_tab_selected}26`,
                                                                    borderRadius: '25px',
                                                                    display: 'flex',
                                                                    justifyContent: 'center',
                                                                    alignItems: 'center',
                                                                }}
                                                            >
                                                                <Typography
                                                                    sx={{
                                                                        color: palette.text_tab_selected,
                                                                        typography:
                                                                            'caption_medium',
                                                                    }}
                                                                >
                                                                    {t('adminLogin.using')}
                                                                </Typography>
                                                            </Box>
                                                        )}
                                                    </Stack>
                                                    <Typography
                                                        sx={{
                                                            color: palette.text_80,
                                                            fontSize: '14px',
                                                            fontWeight: 400,
                                                            lineHeight: '20px',
                                                            whiteSpace: 'pre-line',
                                                            pb: '8px',
                                                        }}
                                                    >
                                                        {desc}
                                                    </Typography>
                                                </Stack>
                                            }
                                        />
                                    ))}
                                </RadioGroup>
                            </FormControl>

                            {isJwt() && (
                                <SuperAdminJwtSettings
                                    formData={formData}
                                    handleChange={handleChange}
                                    errorMessageDisplayName={errorMessageDisplayName}
                                    errorMessageIssuer={errorMessageIssuer}
                                    errorMessagePublicKey={errorMessagePublicKey}
                                    errorMessageJwksUri={errorMessageJwksUri}
                                    errorMessageErrorRedirectUrl={errorMessageErrorRedirectUrl}
                                />
                            )}
                            {isOidc() && (
                                <SuperAdminOidcSettings
                                    oidcProviders={oidcProviders}
                                    setOidcProviders={setOidcProviders}
                                />
                            )}
                        </Box>
                    )}
                </Box>

                {/* 버튼 */}
                <Stack
                    direction={{ xs: 'row', md: 'row-reverse' }}
                    justifyContent="flex-start"
                    alignItems="center"
                    gap="4px"
                    sx={{ alignSelf: 'stretch' }}
                >
                    <StyledButton
                        onClick={handleSave}
                        disabled={isDisabled || !isDirty}
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
                                fontSize: '12px',
                                color: palette.white,
                                lineHeight: '16px',
                                fontWeight: 400,
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

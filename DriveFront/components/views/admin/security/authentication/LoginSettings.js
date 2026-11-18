import { FormControlLabel, Stack, Typography, FormControl, RadioGroup } from '@mui/material';
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import React, { useCallback, useEffect, useState } from 'react';
import api from '@/utils/api';
import { emptyCheck } from '@/utils/userUtils';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import useClientConfig from '@/hooks/useClientConfig';
import JwtSettings from './JwtSettings';
import OidcSettings from './OidcSettings';
import Image from 'next/image';

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

export default function LoginSettings() {
    const t = useTranslations();
    const router = useRouter();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    // SYSTEM_MANAGED 는 로그인 설정 저장이 막혀 있어(TENANT_011) 로그인 방식 선택·저장을 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();

    const [loginType, setLoginType] = useState(null);
    const [savedLoginType, setSavedLoginType] = useState(null);
    const [formData, setFormData] = useState(INITIAL_FORM);
    const [oidcProviders, setOidcProviders] = useState([]);
    const [isDisabled, setIsDisabled] = useState(true);
    const [superAdminEnforced, setSuperAdminEnforced] = useState(null);
    const [initialSettings, setInitialSettings] = useState(null); // 조회 시점 값 (변경 여부 비교용)

    const [errorMessageDisplayName, setErrorMessageDisplayName] = useState('');
    const [errorMessageIssuer, setErrorMessageIssuer] = useState('');
    const [errorMessagePublicKey, setErrorMessagePublicKey] = useState('');
    const [errorMessageJwksUri, setErrorMessageJwksUri] = useState('');
    const [errorMessageErrorRedirectUrl, setErrorMessageErrorRedirectUrl] = useState('');

    const isJwt = () => loginType === 'jwt';
    const isOidc = () => loginType === 'oidc';

    const getLoginSettingInfo = async () => {
        const res = await api.Get('admins/login-settings', {});
        if (res?.data?.code === 200) {
            const data = res?.data?.data;
            setLoginType(data?.loginType);
            setSavedLoginType(data?.loginType);
            setSuperAdminEnforced(data?.superAdminEnforced);

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
                loginType: data?.loginType,
                formData: resolvedFormData,
                oidcProviders: resolvedOidcProviders,
            });
        }
    };

    const getProviderConfig = async loginType => {
        const res = await api.Get('admins/login-settings', { params: { loginType } });
        if (res?.data?.code !== 200) return;

        const data = res?.data?.data;

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

    const handleLoginTypeChange = event => {
        setLoginType(event.target.value);
        getProviderConfig(event.target.value);
    };

    const buildLocalPayload = () => ({ loginType: 'local' });

    const buildJwtPayload = () => ({
        loginType: 'jwt',
        providers: [
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
        loginType: 'oidc',
        providers: oidcProviders.map(p => ({
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
        const payload = isJwt()
            ? buildJwtPayload()
            : isOidc()
              ? buildOidcPayload()
              : buildLocalPayload();

        const res = await api.Patch('admins/login-settings', payload);
        if (res?.data?.code === 200) {
            getLoginSettingInfo();
            showSnackbar(t('adminSetting.save.success'), 'success');
        } else {
            showSnackbar(t('adminSetting.save.fail'), 'error');
        }
    };

    const handleSave = useCallback(() => {
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
    }, [loginType, formData, oidcProviders]);

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
        (loginType !== initialSettings.loginType ||
            (isJwt() && JSON.stringify(formData) !== JSON.stringify(initialSettings.formData)) ||
            (isOidc() &&
                JSON.stringify(oidcProviders) !== JSON.stringify(initialSettings.oidcProviders)));

    return (
        <>
            {/** 전체 적용 - 슈퍼 어드민 설정 적용 */}
            {superAdminEnforced && (
                <Box
                    sx={{
                        width: { xs: '100%', md: '100%' },
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-end',
                        alignSelf: { xs: 'flex-start', md: 'auto' },
                        gap: '32px',
                    }}
                >
                    <Box
                        sx={{
                            width: '100%',
                            px: '12px',
                            py: '80px',
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'center',
                            alignItems: 'center',
                            gap: '16px',
                        }}
                    >
                        {/* 그래픽 자리 */}
                        <Box
                            sx={{
                                width: 120,
                                height: 120,
                                position: 'relative',
                                overflow: 'hidden',
                                flexShrink: 0,
                            }}
                        >
                            <Image
                                src={'/admin/login-setting/icon-ic-login-global.svg'}
                                alt={'global setting'}
                                width={120}
                                height={120}
                            />
                        </Box>

                        {/* 텍스트 영역 */}
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                gap: '16px',
                            }}
                        >
                            <Typography
                                sx={{
                                    alignSelf: 'stretch',
                                    textAlign: 'center',
                                    color: palette.text_80,
                                    typography: 'h3',
                                }}
                            >
                                {t('admin.loginSetting.globalSetting.lockTitle')}
                            </Typography>
                            <Typography
                                sx={{
                                    alignSelf: 'stretch',
                                    textAlign: 'center',
                                    color: palette.text_60,
                                    typography: 'body5',
                                    whiteSpace: 'pre-line',
                                }}
                            >
                                {t('admin.loginSetting.globalSetting.infoBox')}
                            </Typography>
                        </Box>
                    </Box>
                </Box>
            )}

            {/** 개별 테넌트 적용 - 로그인 방식 */}
            {superAdminEnforced != null && !superAdminEnforced && (
                <Box
                    sx={{
                        width: { xs: '100%', md: '100%' },
                        maxWidth: { xs: 472, md: 840 },
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-end',
                        alignSelf: { xs: 'flex-start', md: 'auto' },
                        gap: '32px',
                    }}
                >
                    <Box
                        sx={{
                            alignSelf: 'stretch',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'flex-start',
                            gap: '16px',
                        }}
                    >
                        <Typography sx={{ typography: 'h5', color: palette.text_100 }}>
                            {t('adminLogin.field')}
                        </Typography>

                        <FormControl
                            disabled={isSystemManaged}
                            sx={{ width: '100%', ...(isSystemManaged && { opacity: 0.6 }) }}
                        >
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
                                                                    typography: 'caption_medium',
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
                                                        typography: 'body5',
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
                            <JwtSettings
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
                            <OidcSettings
                                oidcProviders={oidcProviders}
                                setOidcProviders={setOidcProviders}
                            />
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
                            disabled={isDisabled || !isDirty || isSystemManaged}
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
                                    color: palette.white,
                                    typography: 'body9',
                                }}
                            >
                                {t('common.button.save')}
                            </Typography>
                        </StyledButton>
                    </Stack>
                </Box>
            )}
        </>
    );
}

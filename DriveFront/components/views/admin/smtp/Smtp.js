import { Menu, MenuItem, Stack, Typography } from '@mui/material';
import { ArrowFilledDown, ArrowFilledUp } from '@/icon/Arrow';
import Box from '@mui/material/Box';
import Image from 'next/image';
import palette from '@/theme/palette';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import typography from '@/theme/typography';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useAuth from '@/hooks/useAuth';
import { useDialog } from '@/provider/DialogProvider';
import useClientConfig from '@/hooks/useClientConfig';
import MaskedValue from '@/components/common/MaskedValue';
import { MASKED_PLACEHOLDER } from '@/utils/masking';

/** 저장된 비밀번호·OAuth 토큰 표시값 — 길이를 드러내지 않도록 고정 */
const SAVED_SECRET_MASK = '********';

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
                color={'text_60'}
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

export default function Smtp({ loading, setLoading }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [user] = useAuth();

    const [smtpConfigSeq, setSmtpConfigSeq] = useState();

    // Client Name
    const [clientNameChange, setClientNameChange] = useState(false);
    const [clientName, setClientName] = useState('');
    const clientNameInputRef = useRef(null);

    // Host
    const [hostChange, setHostChange] = useState(false);
    const [host, setHost] = useState('');
    const hostInputRef = useRef(null);

    // Port
    const [port, setPort] = useState('');
    const portTriggerRef = useRef(null);
    const [portAnchorEl, setPortAnchorEl] = useState(null);

    // Encryption Type
    const [encryptionType, setEncryptionType] = useState('');
    const encryptionTriggerRef = useRef(null);
    const [encryptionAnchorEl, setEncryptionAnchorEl] = useState(null);

    // Auth Type
    const [authType, setAuthType] = useState('');
    const authTypeTriggerRef = useRef(null);
    const [authTypeAnchorEl, setAuthTypeAnchorEl] = useState(null);

    const authTypeLabelMap = { IDPW: 'ID/PW', OAUTH: 'OAuth' };

    // Username
    const [usernameChange, setUsernameChange] = useState(false);
    const [username, setUsername] = useState('');
    const usernameInputRef = useRef(null);

    // App Password — 저장된 값은 표시용 * 로만 두고, 새로 입력한 값만 저장 요청에 보낸다
    const [appPasswordChange, setAppPasswordChange] = useState(false);
    const [maskedAppPassword, setMaskedAppPassword] = useState('');
    const [appPassword, setAppPassword] = useState('');
    const appPasswordInputRef = useRef(null);

    // OAuth Info — 앱 비밀번호와 같이 조회 값은 표시용, 새로 입력한 값만 저장 요청에 보낸다
    const [oAuthInfoChange, setOAuthInfoChange] = useState(false);
    const [maskedOAuthInfo, setMaskedOAuthInfo] = useState('');
    const [oAuthInfo, setOAuthInfo] = useState('');
    const oAuthInfoInputRef = useRef(null);

    // From Address
    const [fromAddressChange, setFromAddressChange] = useState(false);
    const [fromAddress, setFromAddress] = useState('');
    const fromAddressInputRef = useRef(null);

    // Test Receiver Address
    const [testReceiverAddressChange, setTestReceiverAddressChange] = useState(false);
    const [testReceiverAddress, setTestReceiverAddress] = useState('');
    const testReceiverAddressInputRef = useRef(null);

    const { openDialog } = useDialog();
    // SYSTEM_MANAGED 는 SMTP 저장·발송 테스트가 막혀 있어(TENANT_011) 수정·선택·저장 버튼을 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();
    // SYSTEM_MANAGED 는 SMTP 값이 보통 없어(조회 404) 빈 칸 대신 가짜 값을 블러로 보여준다
    const displayValue = value => (isSystemManaged && !value ? MASKED_PLACEHOLDER : value);
    // 포트·암호화·인증 방식은 SYSTEM_MANAGED 면 값 유무와 관계없이 블러
    const lockedValue = value => (isSystemManaged ? MASKED_PLACEHOLDER : value);
    // 비밀번호·OAuth 토큰은 조회 응답에 값이 없고 저장 여부만 오므로, 저장돼 있으면 고정 길이 * 로 표시
    const savedSecretValue = saved => (saved ? SAVED_SECRET_MASK : '');

    useEffect(() => {
        if (!user) return;
    }, [user]);

    const getSmtpInfo = async () => {
        const res = await api.Get('admins/smtp-config', {});

        if (res?.status === 200) {
            const smtpConfig = res?.data?.data;

            // 실제 값 세팅
            setSmtpConfigSeq(smtpConfig.smtpConfigSeq || undefined);
            setClientName(smtpConfig.clientName || undefined);
            setHost(smtpConfig.host || undefined);
            setPort(smtpConfig.port || undefined);
            setEncryptionType(smtpConfig.encryptionType || undefined);
            setAuthType(smtpConfig.authType || undefined);
            setUsername(smtpConfig.username || undefined);
            setMaskedAppPassword(savedSecretValue(smtpConfig.hasPassword));
            setAppPassword('');
            setMaskedOAuthInfo(savedSecretValue(smtpConfig.hasOauthInfo));
            setOAuthInfo('');
            setFromAddress(smtpConfig.fromAddress || undefined);
            setTestReceiverAddress(smtpConfig.testReceiverAddress || undefined);

            // 로딩 상태 갱신
            setLoading(false);
        }
    };

    const closeAllEditMode = useCallback(() => {
        setClientNameChange(false);
        setHostChange(false);
        setUsernameChange(false);
        setAppPasswordChange(false);
        setOAuthInfoChange(false);
        setFromAddressChange(false);
        setTestReceiverAddressChange(false);
    }, []);

    const handleSmtpSave = useCallback(async () => {
        openDialog('alert', {
            title: t('adminSmtpSettingSaveConfirmDialog.title'),
            description: t('adminSmtpSettingSaveConfirmDialog.text'),
            onClick: async () => {
                const res = await api.Post('admins/smtp-config', {
                    smtpConfigSeq: smtpConfigSeq || undefined,
                    clientName: clientName || undefined,
                    host: host || undefined,
                    port: port || undefined,
                    encryptionType: encryptionType || undefined,
                    authType: authType || undefined,
                    username: username || undefined,
                    encPwd: appPassword || undefined,
                    oauthInfo: oAuthInfo || undefined,
                    fromAddress: fromAddress || undefined,
                    testReceiverAddress: testReceiverAddress || undefined,
                });

                if (res?.code === 200) {
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    closeAllEditMode();
                    getSmtpInfo();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    }, [
        clientName,
        host,
        port,
        encryptionType,
        authType,
        username,
        appPassword,
        oAuthInfo,
        fromAddress,
        testReceiverAddress,
    ]);

    const handleEmailTest = async () => {
        openDialog('alert', {
            title: t('adminSmtpSettingEmailConfirmDialog.title'),
            description: t('adminSmtpSettingEmailConfirmDialog.text'),
            onClick: async () => {
                const res = await api.Post('admins/smtp-config/test', {
                    smtpConfigSeq: smtpConfigSeq || undefined,
                });

                if (res?.code === 200) {
                    showSnackbar(t('adminSmtp.testemail.success'), 'success');
                } else {
                    showSnackbar(t('adminSmtp.testemail.fail'), 'error');
                }
            },
        });
    };

    useEffect(() => {
        getSmtpInfo();
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
            {/** SMTP 설정 */}
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
                {/* Client Name */}
                <UserInfoStack
                    title={t('adminSmtp.clientName')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {clientNameChange ? (
                                <StyledTextField
                                    ref={clientNameInputRef}
                                    variant="outlined"
                                    value={clientName}
                                    onChange={e => setClientName(e.target.value)}
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
                                <MaskedValue
                                    value={displayValue(clientName)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setClientNameChange(!clientNameChange);
                                    setTimeout(() => clientNameInputRef.current?.focus(), 100);
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                {/* Host */}
                <UserInfoStack
                    title={t('adminSmtp.host')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {hostChange ? (
                                <StyledTextField
                                    ref={hostInputRef}
                                    variant="outlined"
                                    value={host}
                                    onChange={e => setHost(e.target.value)}
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
                                <MaskedValue
                                    value={displayValue(host)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setHostChange(!hostChange);
                                    setTimeout(() => hostInputRef.current?.focus(), 100);
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                {/* Port */}
                <UserInfoStack
                    title={t('adminSmtp.port')}
                    ele={
                        <Box
                            display="flex"
                            alignItems="center"
                            px={{ md: 6, xs: 0 }}
                            sx={{ height: { xs: 32, md: 40 } }}
                        >
                            <Box
                                component="button"
                                type="button"
                                disabled={isSystemManaged}
                                ref={portTriggerRef}
                                onClick={e => setPortAnchorEl(e.currentTarget)}
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                    p: 0,
                                    border: 0,
                                    backgroundColor: 'transparent',
                                    cursor: isSystemManaged ? 'default' : 'pointer',
                                }}
                            >
                                <MaskedValue
                                    value={lockedValue(port)}
                                    dummy={'587'}
                                    color={'text_80'}
                                />
                                {portAnchorEl ? (
                                    <ArrowFilledUp width={14} height={14} opacity={'.6'} />
                                ) : (
                                    <ArrowFilledDown width={14} height={14} opacity={'.6'} />
                                )}
                            </Box>
                            <Menu
                                anchorEl={portAnchorEl}
                                open={Boolean(portAnchorEl)}
                                onClose={() => {
                                    setPortAnchorEl(null);
                                    portTriggerRef.current?.focus();
                                }}
                                PaperProps={{
                                    sx: {
                                        mt: 1,
                                        minWidth: 80,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                <MenuItem
                                    onClick={() => {
                                        setPort('465');
                                        setPortAnchorEl(null);
                                        portTriggerRef.current?.focus();
                                    }}
                                >
                                    465
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setPort('587');
                                        setPortAnchorEl(null);
                                        portTriggerRef.current?.focus();
                                    }}
                                >
                                    587
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setPort('25');
                                        setPortAnchorEl(null);
                                        portTriggerRef.current?.focus();
                                    }}
                                >
                                    25
                                </MenuItem>
                            </Menu>
                        </Box>
                    }
                />

                {/* Encryption Type */}
                <UserInfoStack
                    title={t('adminSmtp.encryptionType')}
                    ele={
                        <Box
                            display="flex"
                            alignItems="center"
                            px={{ md: 6, xs: 0 }}
                            sx={{ height: { xs: 32, md: 40 } }}
                        >
                            <Box
                                component="button"
                                type="button"
                                disabled={isSystemManaged}
                                ref={encryptionTriggerRef}
                                onClick={e => setEncryptionAnchorEl(e.currentTarget)}
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                    p: 0,
                                    border: 0,
                                    backgroundColor: 'transparent',
                                    cursor: isSystemManaged ? 'default' : 'pointer',
                                }}
                            >
                                <MaskedValue
                                    value={lockedValue(encryptionType)}
                                    dummy={'TLS'}
                                    color={'text_80'}
                                />
                                {encryptionAnchorEl ? (
                                    <ArrowFilledUp width={14} height={14} opacity={'.6'} />
                                ) : (
                                    <ArrowFilledDown width={14} height={14} opacity={'.6'} />
                                )}
                            </Box>
                            <Menu
                                anchorEl={encryptionAnchorEl}
                                open={Boolean(encryptionAnchorEl)}
                                onClose={() => {
                                    setEncryptionAnchorEl(null);
                                    encryptionTriggerRef.current?.focus();
                                }}
                                PaperProps={{
                                    sx: {
                                        mt: 1,
                                        minWidth: 80,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                <MenuItem
                                    onClick={() => {
                                        setEncryptionType('TLS');
                                        setEncryptionAnchorEl(null);
                                        encryptionTriggerRef.current?.focus();
                                    }}
                                >
                                    TLS
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setEncryptionType('SSL');
                                        setEncryptionAnchorEl(null);
                                        encryptionTriggerRef.current?.focus();
                                    }}
                                >
                                    SSL
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setEncryptionType('NONE');
                                        setEncryptionAnchorEl(null);
                                        encryptionTriggerRef.current?.focus();
                                    }}
                                >
                                    NONE
                                </MenuItem>
                            </Menu>
                        </Box>
                    }
                />

                {/* Auth Type */}
                <UserInfoStack
                    title={t('adminSmtp.authType')}
                    ele={
                        <Box
                            display="flex"
                            alignItems="center"
                            px={{ md: 6, xs: 0 }}
                            sx={{ height: { xs: 32, md: 40 } }}
                        >
                            <Box
                                component="button"
                                type="button"
                                disabled={isSystemManaged}
                                ref={authTypeTriggerRef}
                                onClick={e => setAuthTypeAnchorEl(e.currentTarget)}
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                    p: 0,
                                    border: 0,
                                    backgroundColor: 'transparent',
                                    cursor: isSystemManaged ? 'default' : 'pointer',
                                }}
                            >
                                <MaskedValue
                                    value={lockedValue(authTypeLabelMap[authType] ?? authType)}
                                    dummy={'ID/PW'}
                                    color={'text_80'}
                                />
                                {authTypeAnchorEl ? (
                                    <ArrowFilledUp width={14} height={14} opacity={'.6'} />
                                ) : (
                                    <ArrowFilledDown width={14} height={14} opacity={'.6'} />
                                )}
                            </Box>
                            <Menu
                                anchorEl={authTypeAnchorEl}
                                open={Boolean(authTypeAnchorEl)}
                                onClose={() => {
                                    setAuthTypeAnchorEl(null);
                                    authTypeTriggerRef.current?.focus();
                                }}
                                PaperProps={{
                                    sx: {
                                        mt: 1,
                                        minWidth: 80,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                <MenuItem
                                    onClick={() => {
                                        setAuthType('IDPW');
                                        setAuthTypeAnchorEl(null);
                                        authTypeTriggerRef.current?.focus();
                                    }}
                                >
                                    ID/PW
                                </MenuItem>
                                <MenuItem
                                    onClick={() => {
                                        setAuthType('OAUTH');
                                        setAuthTypeAnchorEl(null);
                                        authTypeTriggerRef.current?.focus();
                                    }}
                                >
                                    OAuth
                                </MenuItem>
                            </Menu>
                        </Box>
                    }
                />

                {/* Username */}
                <UserInfoStack
                    title={t('adminSmtp.username')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {usernameChange ? (
                                <StyledTextField
                                    ref={usernameInputRef}
                                    variant="outlined"
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
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
                                <MaskedValue
                                    value={displayValue(username)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setUsernameChange(!usernameChange);
                                    setTimeout(() => usernameInputRef.current?.focus(), 100);
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                {/* App Password */}
                <UserInfoStack
                    title={t('adminSmtp.appPassword')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {appPasswordChange ? (
                                <StyledTextField
                                    ref={appPasswordInputRef}
                                    variant="outlined"
                                    value={appPassword}
                                    onChange={e => setAppPassword(e.target.value)}
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
                                <MaskedValue
                                    value={lockedValue(appPassword || maskedAppPassword)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setAppPasswordChange(!appPasswordChange);
                                    setTimeout(() => appPasswordInputRef.current?.focus(), 100);
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                {/* OAuth Info */}
                <UserInfoStack
                    title={t('adminSmtp.oauthInfo')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {oAuthInfoChange ? (
                                <StyledTextField
                                    ref={oAuthInfoInputRef}
                                    variant="outlined"
                                    value={oAuthInfo}
                                    onChange={e => setOAuthInfo(e.target.value)}
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
                                <MaskedValue
                                    value={lockedValue(oAuthInfo || maskedOAuthInfo)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setOAuthInfoChange(!oAuthInfoChange);
                                    setTimeout(() => oAuthInfoInputRef.current?.focus(), 100);
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                {/* From Address */}
                <UserInfoStack
                    title={t('adminSmtp.fromAddress')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {fromAddressChange ? (
                                <StyledTextField
                                    ref={fromAddressInputRef}
                                    variant="outlined"
                                    value={fromAddress}
                                    onChange={e => setFromAddress(e.target.value)}
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
                                <MaskedValue
                                    value={displayValue(fromAddress)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setFromAddressChange(!fromAddressChange);
                                    setTimeout(() => fromAddressInputRef.current?.focus(), 100);
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />

                {/* Test Receiver Address */}
                <UserInfoStack
                    title={t('adminSmtp.testReceiverAddress')}
                    ele={
                        <Stack
                            direction="row"
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems="center"
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            {testReceiverAddressChange ? (
                                <>
                                    <StyledTextField
                                        ref={testReceiverAddressInputRef}
                                        variant="outlined"
                                        value={testReceiverAddress}
                                        onChange={e => setTestReceiverAddress(e.target.value)}
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
                                </>
                            ) : (
                                <MaskedValue
                                    value={displayValue(testReceiverAddress)}
                                    sx={{ width: { xs: 'max-content', md: '100%' } }}
                                />
                            )}
                            <StyledButton
                                variant="text"
                                disabled={isSystemManaged}
                                sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                onClick={() => {
                                    setTestReceiverAddressChange(!testReceiverAddressChange);
                                    setTimeout(
                                        () => testReceiverAddressInputRef.current?.focus(),
                                        100
                                    );
                                }}
                            >
                                <Image
                                    src="/setting/icon-ic-edit.svg"
                                    alt="edit"
                                    width={16}
                                    height={16}
                                />
                                <Typography
                                    variant="body9"
                                    sx={{ display: { xs: 'none', md: 'block' } }}
                                    color="text_tab_selected"
                                >
                                    {t('common.button.update')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    }
                />
            </Stack>
            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    disabled={isSystemManaged}
                    onClick={handleEmailTest}
                    sx={{ px: { xs: '29.5px', md: 4 }, height: { xs: 32, md: 24 } }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('adminSmtp.testemail.btn')}
                    </Typography>
                </StyledButton>
            </Stack>

            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    disabled={isSystemManaged}
                    onClick={handleSmtpSave}
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

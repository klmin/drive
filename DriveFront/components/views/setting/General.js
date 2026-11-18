import {
    Box,
    Menu,
    MenuItem,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import Image from 'next/image';
import palette from '@/theme/palette';
import convertBytesToReadableSize from '@/utils/math';
import StyledTextField, { FieldErrorMessage } from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { styled } from '@mui/material/styles';
import typography from '@/theme/typography';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { getDialCodeDigits } from '@/utils/format';
import { useRouter } from '@/hooks/useRouter';
import { useDialog } from '@/provider/DialogProvider';
import { ArrowFilledDown, ArrowFilledUp } from '@/icon/Arrow';
import ProfileImageEditor from '@/components/common/input/ProfileImageEditor';
import dayjs from 'dayjs';
import CountrySelect from '@/components/common/input/CountrySelect';
import { trimEdgeSpaces, validatePhoneAndCountry } from '@/utils/userUtils';
import { setLanguage } from '@/utils/authTokens';
import { useUser } from '@/context/UserContext';
import MyApiKeySection from '@/components/common/MyApiKeySection';

const Root = styled('div')(({ theme }) => ({
    table: {
        boxShadow: 'none',
        width: '100%',
        height: '100%',
    },

    '.table-section-label': {
        padding: '19px 8px 9px',
        ...typography.body6,
        color: palette.text_60,
        [theme.breakpoints.down('md')]: {
            height: 'auto',
            padding: '19px 8px 9px',
            borderBottom: 'none',
        },
    },

    tHead: {
        height: 30,
        borderTop: `1px solid ${palette.divider_8}`,
        borderBottom: `1px solid ${palette.divider_8}`,
        borderRight: 'none',
        backgroundColor: palette.white,
    },

    th: {
        ...typography.body9,
        color: palette.text_60,
        padding: '0px 8px 0px 12px',
        borderBottom: `none !important`,
        borderTop: `1px solid ${palette.divider_8}`,
        [theme.breakpoints.down('md')]: {
            padding: '0px 6px',
        },
    },

    '& thead tr': {
        position: 'sticky',
        top: 0,
        zIndex: 1,
        backgroundColor: 'white',
        boxShadow: `0 3px 5px -2px ${palette.divider_12}`,
    },

    td: {
        height: 44,
        padding: '0px 8px 0px 12px',
        fontSize: 13,
        fontWeight: 'normal',
        color: palette.text_80,
        cursor: 'pointer',
        borderBottom: `1px solid ${palette.divider_8}`,
        [theme.breakpoints.down('md')]: {
            height: 86,
            padding: '8px 6px',
            borderBottom: 'none',
            borderTop: `1px solid ${palette.divider_8}`,
        },
    },
}));

const PasswordRoundBox = () => {
    return (
        <Box sx={{ width: 8, height: 8, backgroundColor: palette.text_80, borderRadius: '50%' }} />
    );
};

const UserInfoStack = ({ title, ele, isFirst, isLast, noFixedHeight }) => {
    const border = { md: `1px solid ${palette.control_border_normal}` };
    return (
        <Stack direction={{ md: 'row', xs: 'column' }}>
            <Typography
                sx={{
                    backgroundColor: { md: palette.bg_gray },
                    height: { md: noFixedHeight ? 'auto' : 40 },
                    py: { md: noFixedHeight ? '11px' : '11px', xs: 0 },
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
                    alignItems: noFixedHeight ? 'flex-start' : 'center',
                }}
                variant={'body7'}
                color={'text_60'}
            >
                {title}
            </Typography>
            <Box
                sx={{
                    flex: 1,
                    height: { md: noFixedHeight ? 'auto' : 40 },
                    borderTop: { md: isFirst ? border.md : 'none' },
                    borderBottom: border,
                    borderRight: border,
                    borderTopRightRadius: { md: isFirst ? '6px' : 0 },
                    borderBottomRightRadius: { md: isLast ? '6px' : 0 },
                    display: 'flex',
                    alignItems: noFixedHeight ? 'flex-start' : 'center',
                    pl: { md: '24px', xs: 0 },
                    pr: { md: '16px', xs: 0 },
                    py: { md: noFixedHeight ? '11px' : 0, xs: 0 },
                }}
            >
                {ele}
            </Box>
        </Stack>
    );
};

export default function General() {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const user = useUser();
    // SYSTEM_MANAGED 보호 계정은 본인 비밀번호를 바꿀 수 없다(TENANT_011) — 값이 없으면 허용으로 본다
    const passwordLocked = user?.isSelfPasswordChangeable === false;
    const router = useRouter();

    const tableContainerRef = useRef(null);
    const screenTriggerRef = useRef(null);
    const langTriggerRef = useRef(null);

    const langLabelMap = {
        ko: t('common.language.ko'),
        en: t('common.language.en'),
        es: t('common.language.es'),
    };

    const [passwordChange, setPasswordChange] = useState(false);
    const [phoneChange, setPhoneChange] = useState(false);
    const [profileImageFile, setProfileImageFile] = useState('');
    const [deleteProfileImage, setDeleteProfileImage] = useState(false);
    const [profileImageError, setProfileImageError] = useState('');
    const [countryCode, setCountryCode] = useState(null);
    const [phone, setPhone] = useState('');
    const [lang, setLang] = useState('');
    const [screen, setScreen] = useState('');
    const [screenAnchorEl, setScreenAnchorEl] = useState(null);
    const [langAnchorEl, setLangAnchorEl] = useState(null);

    const [cursorId, setCursorId] = useState(null);
    const [hasNext, setHasNext] = useState(false);
    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    const [securityList, setSecurityList] = useState([]);
    const [version, setVersion] = useState('');
    const [errorMessagePhone, setErrorMessagePhone] = useState('');
    const [isDisabled, setIsDisabled] = useState(true);
    const [loadingInit, setLoadingInit] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);

    const phoneInputRef = useRef(null);
    const passwordInputRef = useRef(null);

    const { openDialog } = useDialog();

    const currentProfileImageUrl = api.getProfileImage(user?.userSeq);
    const [previewUrl, setPreviewUrl] = useState(currentProfileImageUrl || '');

    useEffect(() => {
        if (!user) return;

        setCountryCode(
            user?.countryCode
                ? {
                      name: '',
                      value: '+' + user?.countryCode,
                  }
                : null
        );
        setPhone(user?.phoneNumber || '');
        setLang(user?.userLanguage || 'en');
        setScreen(user?.defaultLandingPage || 'home');
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
                    pn: phone || '',
                    ul: lang,
                    pif: profileImageFile || null,
                    dlp: screen,
                    ct: getDialCodeDigits(countryCode?.value) || '',
                    dpi: deleteProfileImage,
                });

                if (res?.status === 200) {
                    setLanguage('USER', lang);
                    showSnackbar(t('account.save.success'), 'success');
                    router.reload();
                } else {
                    showSnackbar(t('account.save.fail'), 'error');
                }
            },
        });
    }, [phone, lang, profileImageFile, deleteProfileImage, screen, countryCode, version]);

    const getSecurityList = async isNewSearch => {
        if (isNewSearch) setLoadingInit(true);
        else setLoadingMore(true);

        try {
            const res = await api.Get('users/log', {
                params: {
                    ps: isNewSearch || !cursorId ? 20 : 10,
                    ci: isNewSearch ? null : cursorId,
                },
            });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                const newData = res?.data?.data ?? [];
                if (isNewSearch) {
                    setSecurityList(newData);
                } else {
                    setSecurityList(prevList => [...prevList, ...newData]);
                }
            }
        } finally {
            if (isNewSearch) setLoadingInit(false);
            else setLoadingMore(false);
        }
    };

    const handleOpenUpdatePassword = async () => {
        openDialog('changePassword', {});
    };

    const getBuildInfo = async () => {
        const res = await api.Get('build/summary', {});

        if (res?.status === 200) {
            const build = res?.data;
            setVersion(build.buildInfo);
        }
    };

    const handleChange = async e => {
        const { name, value } = e.target;

        let checkValue = name === 'countryCode' ? value : trimEdgeSpaces(value);
        setPhone(checkValue);

        try {
            if (name === 'phoneNumber' || name === 'countryCode') {
                const phone = name === 'phoneNumber' ? checkValue : phone;
                const code = name === 'countryCode' ? checkValue : countryCode?.value || '';
                validatePhoneAndCountry(phone, code, t);
                setErrorMessagePhone('');
            }
        } catch (error) {
            if (name === 'phoneNumber' || name === 'countryCode') {
                setErrorMessagePhone(error.message);
            }
        }
    };

    const handleTableScroll = useCallback(() => {
        const container = tableContainerRef.current;
        if (container) {
            const { scrollTop, scrollHeight, clientHeight } = container;

            if (scrollHeight - scrollTop - clientHeight < 100 && hasNext && !loadingMore) {
                setCursorSearchCount(prev => prev + 1);
            }
        }
    }, [hasNext, loadingMore]);

    const handleScreenMenuOpen = e => {
        setScreenAnchorEl(e.currentTarget);
    };

    const handleLangMenuOpen = e => {
        setLangAnchorEl(e.currentTarget);
    };

    const handleScreenMenuClose = () => {
        setScreenAnchorEl(null);
        requestAnimationFrame(() => {
            screenTriggerRef.current?.focus();
        });
    };

    const handleLangMenuClose = () => {
        setLangAnchorEl(null);
        requestAnimationFrame(() => {
            langTriggerRef.current?.focus();
        });
    };

    const handleScreenSelect = value => {
        setScreenAnchorEl(null);
        requestAnimationFrame(() => {
            screenTriggerRef.current?.focus();
            setScreen(value);
        });
    };

    const handleLangSelect = value => {
        setLangAnchorEl(null);
        requestAnimationFrame(() => {
            langTriggerRef.current?.focus();
            setLang(value);
        });
    };

    useEffect(() => {
        const container = tableContainerRef.current;
        if (container) {
            container.addEventListener('scroll', handleTableScroll);
            return () => {
                container.removeEventListener('scroll', handleTableScroll);
            };
        }
    }, [handleTableScroll]);

    useEffect(() => {
        getSecurityList(true);
        getBuildInfo();
    }, []);

    useEffect(() => {
        if (cursorSearchCount <= 0) return;
        if (!hasNext) return;
        if (loadingMore) return;
        getSecurityList(false);
    }, [cursorSearchCount]);

    useEffect(() => {
        const isValid = errorMessagePhone === '';

        if (isValid) {
            setIsDisabled(false);
        } else {
            setIsDisabled(true);
        }
    }, [phone]);

    return (
        <Box
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
                    <ProfileImageEditor
                        src={previewUrl}
                        onSelectImage={handleSelectProfileImage}
                        onResetImage={handleResetProfileImage}
                        onInvalidImage={handleInvalidProfileImage}
                    />
                    <Stack gap={2} pr={2} sx={{ width: '100%' }}>
                        <Typography variant="h4_h5">{user?.userName}</Typography>
                        {profileImageError && <FieldErrorMessage message={profileImageError} />}
                        <Box
                            sx={{
                                maxWidth: 400,
                                height: 8,
                                background: palette.divider_8,
                                borderRadius: 2,
                                border: `1px solid #3a3d4933`,
                            }}
                        >
                            <Box
                                sx={{
                                    width: `${(user?.useQuotaByte / user?.totalQuotaByte) * 100}%`,
                                    height: 7,
                                    background: 'black',
                                    borderRadius:
                                        user?.useQuotaByte >= user?.totalQuotaByte
                                            ? 0
                                            : '8px 0px 0px 8px',
                                }}
                            />
                        </Box>
                        <Typography variant="body8" color={'text_100'} component={'span'}>
                            {convertBytesToReadableSize(user?.useQuotaByte)}
                            <Typography variant="body9" color={'text_60'} component={'span'}>
                                &nbsp;of&nbsp;
                            </Typography>
                            <Typography variant="body8" color={'text_80'} component={'span'}>
                                {convertBytesToReadableSize(user?.totalQuotaByte)}
                            </Typography>
                            <Typography variant="body9" color={'text_80'} component={'span'}>
                                &nbsp;used
                            </Typography>
                        </Typography>
                    </Stack>
                </Stack>
            </Stack>

            <Stack direction={{ xs: 'column' }} sx={{ gap: { xs: 5, md: 0 } }}>
                <UserInfoStack
                    isFirst
                    title={t('UserInfo.userEmail')}
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
                            {passwordChange ? (
                                <StyledTextField
                                    ref={passwordInputRef}
                                    variant="outlined"
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
                                <Stack
                                    direction={'row'}
                                    sx={{ gap: 1, width: { xs: 'max-content', md: '100%' } }}
                                >
                                    {/* 바꿀 수 없는 비밀번호는 점 표시도 하지 않는다 */}
                                    {!passwordLocked &&
                                        Array(6)
                                            .fill(0)
                                            .map((_, i) => <PasswordRoundBox key={i} />)}
                                </Stack>
                            )}

                            <StyledButton
                                variant={'text'}
                                sx={{ gap: '2px', px: '6px', py: 0, height: 24, minWidth: 0 }}
                                onClick={handleOpenUpdatePassword}
                                disabled={passwordLocked}
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
                    noFixedHeight
                    title={t('common.label.phone')}
                    ele={
                        <Stack spacing={0.5} sx={{ width: '100%' }}>
                            <Stack
                                direction={'row'}
                                justifyContent={'space-between'}
                                alignItems={'center'}
                                sx={{ width: '100%', minHeight: 18 }}
                            >
                                <Stack
                                    direction="row"
                                    alignItems="center"
                                    sx={{ flex: 1, minWidth: 0 }}
                                >
                                    {phoneChange ? (
                                        <>
                                            <CountrySelect
                                                countryCode={countryCode}
                                                setCountryCode={setCountryCode}
                                            />
                                            <StyledTextField
                                                name="phoneNumber"
                                                ref={phoneInputRef}
                                                variant="outlined"
                                                value={phone}
                                                placeholder={t('common.validate.numericOnly')}
                                                onChange={e => {
                                                    const el = e.target;
                                                    const val = el.value;
                                                    if (val === '' || /^([0-9]+|-1)$/.test(val)) {
                                                        handleChange(e);
                                                    } else {
                                                        el.value = '';
                                                        handleChange(e);
                                                    }
                                                }}
                                                sx={{ flex: 1 }}
                                                inputProps={{
                                                    inputMode: 'numeric',
                                                    pattern: '[0-9]*',
                                                    maxLength: 50,
                                                }}
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
                                        <Typography
                                            sx={{ width: { xs: 'max-content', md: '100%' } }}
                                            variant={'body5'}
                                            color={'text_60'}
                                        >
                                            {`${countryCode?.value || ''} ${phone}`}
                                        </Typography>
                                    )}
                                </Stack>

                                <StyledButton
                                    variant={'text'}
                                    sx={{ gap: '2px', px: '6px', py: 0, height: 24, minWidth: 0 }}
                                    onClick={() => {
                                        setPhoneChange(!phoneChange);
                                        setTimeout(
                                            () =>
                                                phoneInputRef.current &&
                                                phoneInputRef.current.focus(),
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
                            {errorMessagePhone && (
                                <Typography variant={'body9'} color={'status_error'}>
                                    {errorMessagePhone}
                                </Typography>
                            )}
                        </Stack>
                    }
                />

                <UserInfoStack
                    title={t('UserInfo.startScreen')}
                    ele={
                        <Box display={'flex'} alignItems={'center'} sx={{ width: '100%' }}>
                            <Box
                                component="button"
                                type="button"
                                ref={screenTriggerRef}
                                onClick={handleScreenMenuOpen}
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
                                    {screen === 'home'
                                        ? t('UserInfo.home')
                                        : t('common.label.myDrive')}
                                </Typography>
                                {screenAnchorEl ? (
                                    <ArrowFilledUp width={14} height={14} opacity={'.6'} />
                                ) : (
                                    <ArrowFilledDown width={14} height={14} opacity={'.6'} />
                                )}
                            </Box>
                            <Menu
                                anchorEl={screenAnchorEl}
                                open={Boolean(screenAnchorEl)}
                                onClose={handleScreenMenuClose}
                                PaperProps={{
                                    sx: {
                                        mt: 1,
                                        minWidth: 125,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                <MenuItem onClick={() => handleScreenSelect('home')} value={'home'}>
                                    {t('UserInfo.home')}
                                </MenuItem>
                                <MenuItem
                                    onClick={() => handleScreenSelect('my-drive')}
                                    value={'my-drive'}
                                >
                                    {t('common.label.myDrive')}
                                </MenuItem>
                            </Menu>
                        </Box>
                    }
                />

                <UserInfoStack
                    title={t('UserInfo.language')}
                    ele={
                        <Box display={'flex'} alignItems={'center'} sx={{ width: '100%' }}>
                            <Box
                                component="button"
                                type="button"
                                ref={langTriggerRef}
                                onClick={handleLangMenuOpen}
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
                                onClose={handleLangMenuClose}
                                PaperProps={{
                                    sx: {
                                        mt: 1,
                                        minWidth: 125,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                <MenuItem onClick={() => handleLangSelect('ko')} value={'ko'}>
                                    {t('common.language.ko')}
                                </MenuItem>
                                <MenuItem onClick={() => handleLangSelect('en')} value={'en'}>
                                    {t('common.language.en')}
                                </MenuItem>
                                <MenuItem onClick={() => handleLangSelect('es')} value={'es'}>
                                    {t('common.language.es')}
                                </MenuItem>
                                <MenuItem onClick={() => handleLangSelect('auto')} value={'auto'}>
                                    {t('common.language.auto')}
                                </MenuItem>
                            </Menu>
                        </Box>
                    }
                />

                <UserInfoStack
                    isLast
                    title={t('adminServer.version')}
                    ele={
                        <Typography variant={'body5'} color={'text_80'} sx={{ width: '100%' }}>
                            {version}
                        </Typography>
                    }
                />
            </Stack>

            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    onClick={handleSave}
                    disabled={isDisabled}
                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </Stack>

            <MyApiKeySection roleType="USER" />

            <Stack direction={'column'} gap={4} mt={{ md: 8, xs: 6 }}>
                <Stack direction={'row'} alignItems={'center'} gap={2}>
                    <Box
                        display={'flex'}
                        alignItems={'center'}
                        justifyContent={'center'}
                        sx={{
                            width: 30,
                            height: 30,
                            backgroundColor: palette.table_bg_selected,
                            borderRadius: 30,
                        }}
                    >
                        <Image
                            src={'/setting/icon-ic-security.svg'}
                            alt={'security'}
                            width={24}
                            height={24}
                        />
                    </Box>
                    <Typography variant={'h5'}>{t('Settings.security.title')}</Typography>
                </Stack>

                <Root>
                    <TableContainer sx={{ maxHeight: '470px' }} ref={tableContainerRef}>
                        <Table stickyHeader aria-label="sticky table">
                            <TableHead
                                sx={{
                                    display: {
                                        xs: 'none',
                                        md: 'table-header-group',
                                    },
                                }}
                            >
                                <TableRow>
                                    <TableCell className={'table-header-cell'}>
                                        {t('Settings.security.location')}
                                    </TableCell>
                                    <TableCell className={'table-header-cell'}>
                                        {t('Settings.security.timestamp')}
                                    </TableCell>
                                    <TableCell className={'table-header-cell'}>
                                        {t('Settings.security.eventType.name')}
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {securityList.map((s, index) => (
                                    <TableRow key={index}>
                                        <TableCell
                                            sx={{
                                                maxWidth: '580px',
                                            }}
                                        >
                                            <Box display="flex" alignItems="center" gap={2}>
                                                <Image
                                                    src={'/common/icon-ic-security-list.svg'}
                                                    alt={'location'}
                                                    width={24}
                                                    height={24}
                                                />

                                                <Stack direction={'column'} gap={'2px'}>
                                                    <Typography
                                                        variant={'body7'}
                                                        color={'text_100'}
                                                    >
                                                        {s.region} / {s.ipAddress}
                                                    </Typography>
                                                    <Typography
                                                        variant={'body9'}
                                                        sx={{
                                                            display: {
                                                                xs: 'block',
                                                                md: 'none',
                                                            },
                                                        }}
                                                    >
                                                        {dayjs(s.registerDate).format(
                                                            'YYYY. M. D. H:mm'
                                                        )}
                                                    </Typography>
                                                    <Typography
                                                        variant={'body9'}
                                                        sx={{
                                                            display: {
                                                                xs: 'block',
                                                                md: 'none',
                                                            },
                                                            fontWeight: 500,
                                                        }}
                                                        color={
                                                            s.activityType === 'LOGIN'
                                                                ? palette.text_tab_selected
                                                                : s.activityType === 'LOGOUT'
                                                                  ? palette.status_error
                                                                  : palette.status_confirm
                                                        }
                                                    >
                                                        {s.activityType}
                                                    </Typography>
                                                </Stack>
                                            </Box>
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                width: '136px',
                                                display: {
                                                    xs: 'none',
                                                    md: 'table-cell',
                                                },
                                            }}
                                        >
                                            {dayjs(s.registerDate).format('YYYY. M. D. H:mm')}
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                width: '150px',
                                                display: {
                                                    xs: 'none',
                                                    md: 'table-cell',
                                                },
                                            }}
                                            style={{
                                                fontWeight: 500,
                                                color:
                                                    s.activityType === 'LOGIN'
                                                        ? palette.text_tab_selected
                                                        : s.activityType === 'LOGOUT'
                                                          ? palette.status_error
                                                          : palette.status_confirm,
                                            }}
                                        >
                                            {s.activityType}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Root>
            </Stack>
            <Box sx={{ height: '64px', flexShrink: 0 }} />
        </Box>
    );
}

import { FormControlLabel, Stack, Typography, InputBase, IconButton } from '@mui/material';
import StyledSwitchButton from '@/components/common/button/StyledSwitchButton';
import Box from '@mui/material/Box';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import CloseIcon from '@mui/icons-material/Close';
import palette from '@/theme/palette';
import React, { useCallback, useEffect, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useClientConfig from '@/hooks/useClientConfig';
import Image from 'next/image';

export default function AccessControll() {
    const t = useTranslations();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const [accessRestrictStatus, setAccessRestrictStatus] = useState(null);
    const [allowedCountries, setAllowedCountries] = useState([]);
    const [countryInput, setCountryInput] = useState('');
    const [allowedIps, setAllowedIps] = useState([]);
    const [ipInput, setIpInput] = useState('');
    const [countryError, setCountryError] = useState('');
    const [ipError, setIpError] = useState('');
    const [accessRestrictionError, setAccessRestrictionError] = useState('');
    const isAllowListEmpty =
        allowedCountries.length === 0 && allowedIps.length === 0 && accessRestrictStatus;

    useEffect(() => {
        if (!user) return;
    }, [user]);

    const getAccessRestrictStatusInfo = async () => {
        const res = await api.Get('admins/system-config/access-restriction-status', {});

        if (res?.status === 200) {
            const data = res?.data?.data;
            console.log('data', data);
            setAccessRestrictStatus(data?.enabled || false);
            setAllowedCountries(data?.allowedCountries || []);
            setAllowedIps(data?.allowedIps || []);
        }
    };

    // SYSTEM_MANAGED 는 접근 제한 저장이 막혀 있어(TENANT_011) 토글·허용 국가·IP 입력·저장을 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();

    const handleAccessRestrictChange = event => {
        setAccessRestrictStatus(event.target.checked);
        setAccessRestrictionError('');
    };

    const handleAccessRestrictStatusSave = useCallback(async () => {
        if (isAllowListEmpty) {
            setAccessRestrictionError(t('adminAccessRestriction.validateAccessRestriction'));
            setCountryError('');
            setIpError('');
            return;
        }
        openDialog('alert', {
            title: t('adminAccessRestrictionSettingSaveConfirmDialog.title'),
            buttonName: t('common.button.save'),
            onClick: async () => {
                const res1 = await api.Put('admins/access-restriction/countries', {
                    countries: allowedCountries,
                });
                const res2 = await api.Put('admins/access-restriction/ips', {
                    ips: allowedIps,
                });
                const res3 = await api.Put('admins/system-config/access-restriction-status', {
                    enabled: accessRestrictStatus,
                });
                if (
                    res1?.data?.code === 200 &&
                    res2?.data?.code === 200 &&
                    res3?.data?.code === 200
                ) {
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    getAccessRestrictStatusInfo();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    }, [isAllowListEmpty, accessRestrictStatus, allowedCountries, allowedIps]);

    const handleCountryKeyDown = e => {
        if (e.key !== 'Enter') return;
        const code = countryInput.trim().toUpperCase();
        if (!/^[A-Z]{2}$/.test(code)) {
            setCountryError(t('adminAccessRestriction.validateCountryCode'));
            setAccessRestrictionError('');
            return;
        }
        setCountryError('');
        setAccessRestrictionError('');
        if (!allowedCountries.includes(code)) {
            setAllowedCountries(prev => [...prev, code]);
        }
        setCountryInput('');
    };

    const handleRemoveCountry = code => {
        setAllowedCountries(prev => prev.filter(c => c !== code));
    };

    const isValidIpv4 = ip => {
        const parts = ip.split('.');
        if (parts.length !== 4) return false;

        return parts.every(part => {
            if (!/^\d+$/.test(part)) return false;
            const num = Number(part);
            return num >= 0 && num <= 255;
        });
    };

    const ipv4ToInt = octets =>
        (octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3];

    const isValidCidr = cidr => {
        if (!/^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/.test(cidr)) return false;

        const [ipPart, prefixPart] = cidr.split('/');
        const prefix = prefixPart !== undefined ? parseInt(prefixPart, 10) : 32;

        if (prefix < 0 || prefix > 32) return false;
        if (!isValidIpv4(ipPart)) return false;

        const octets = ipPart.split('.').map(Number);
        const networkInt = ipv4ToInt(octets);
        const mask = prefix === 0 ? 0 : 0xffffffff << (32 - prefix);

        // CIDR이면 네트워크 주소만 허용
        return (networkInt & mask) === networkInt;
    };

    const handleIpKeyDown = e => {
        if (e.key !== 'Enter') return;
        const ip = ipInput.trim();
        if (!isValidCidr(ip)) {
            setIpError(t('adminAccessRestriction.validateIpAddress'));
            setAccessRestrictionError('');
            return;
        }
        setIpError('');
        setAccessRestrictionError('');
        if (!allowedIps.includes(ip)) {
            setAllowedIps(prev => [...prev, ip]);
        }
        setIpInput('');
    };

    const handleRemoveIp = ip => {
        setAllowedIps(prev => prev.filter(i => i !== ip));
    };

    useEffect(() => {
        getAccessRestrictStatusInfo();
    }, []);

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
                //  상단 패딩 + tab메뉴 높이 + 기본 패딩 + 컨텐츠 패딩
            }}
        >
            {/** 접근 제한 설정 */}
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
                            {t('adminAccessRestriction.field')}
                        </Typography>
                        <Typography variant="body7" color="text_60">
                            {t('adminAccessRestriction.fieldDesc')}
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
                            }}
                        >
                            <StyledSwitchButton
                                checked={accessRestrictStatus}
                                onChange={handleAccessRestrictChange}
                                disabled={isSystemManaged}
                                label={
                                    accessRestrictStatus
                                        ? t('common.status.enabled')
                                        : t('common.status.disabled')
                                }
                            />
                        </Box>
                    </Stack>

                    {accessRestrictStatus && (
                        <Stack direction="column" spacing={6} sx={{ mb: 6 }}>
                            {/* 접근 허용 국가 */}
                            <Stack direction="column" spacing={4}>
                                <Typography variant="body1" color="text_80">
                                    {t('adminAccessRestriction.allowedCountries')}
                                </Typography>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        border: `1px solid ${palette.control_border_normal}`,
                                        borderRadius: 1,
                                        height: 44,
                                        px: 2,
                                        py: 0.5,
                                    }}
                                >
                                    <InputBase
                                        value={countryInput}
                                        onChange={e => {
                                            setCountryInput(e.target.value);
                                            setCountryError('');
                                        }}
                                        onKeyDown={handleCountryKeyDown}
                                        disabled={isSystemManaged}
                                        placeholder={t(
                                            'adminAccessRestriction.countryCodePlaceholder'
                                        )}
                                        sx={{ flex: 1, fontSize: '14px' }}
                                    />
                                    <IconButton
                                        size="small"
                                        disabled={isSystemManaged}
                                        onClick={() => {
                                            setCountryInput('');
                                            setCountryError('');
                                        }}
                                        sx={{ p: 0.5 }}
                                    >
                                        <CancelOutlinedIcon
                                            fontSize="small"
                                            sx={{ color: palette.text_40 }}
                                        />
                                    </IconButton>
                                </Box>
                                <Box
                                    sx={{
                                        border: `1px solid ${palette.control_border_normal}`,
                                        borderRadius: 1,
                                        minHeight: 98,
                                        overflow: 'hidden',
                                    }}
                                >
                                    {allowedCountries.length === 0 ? (
                                        <Box sx={{ height: 49, p: 2 }}>
                                            <Typography variant="body7" color="text_40">
                                                {t('adminAccessRestriction.emptyAllowedCountries')}
                                            </Typography>
                                        </Box>
                                    ) : (
                                        allowedCountries.map((code, index) => (
                                            <Box
                                                key={code}
                                                sx={{
                                                    display: 'flex',
                                                    height: 49,
                                                    alignItems: 'center',
                                                    justifyContent: 'space-between',
                                                    px: 2,
                                                    py: 1.5,
                                                    borderBottom:
                                                        index < allowedCountries.length - 1
                                                            ? `1px solid ${palette.control_border_normal}`
                                                            : 'none',
                                                }}
                                            >
                                                <Typography variant="body7" color="text_60">
                                                    {code}
                                                </Typography>
                                                <IconButton
                                                    size="small"
                                                    disabled={isSystemManaged}
                                                    onClick={() => handleRemoveCountry(code)}
                                                    sx={{ p: 0.5 }}
                                                >
                                                    <CloseIcon
                                                        fontSize="small"
                                                        sx={{
                                                            color: palette.text_40,
                                                            fontSize: '16px',
                                                        }}
                                                    />
                                                </IconButton>
                                            </Box>
                                        ))
                                    )}
                                </Box>
                                {countryError && (
                                    <Box
                                        sx={{
                                            p: 0,
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 0.5,
                                        }}
                                    >
                                        <Image
                                            src={'/common/icon-ic-message-error.svg'}
                                            alt={'error'}
                                            width={20}
                                            height={20}
                                        />
                                        <Typography variant="body7" color="error">
                                            {countryError}
                                        </Typography>
                                    </Box>
                                )}
                            </Stack>

                            {/* 접근 허용 IP */}
                            <Stack direction="column" spacing={4}>
                                <Typography variant="body1" color="text_80">
                                    {t('adminAccessRestriction.allowedIps')}
                                </Typography>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        border: `1px solid ${palette.control_border_normal}`,
                                        borderRadius: 1,
                                        height: 44,
                                        px: 2,
                                        py: 0.5,
                                    }}
                                >
                                    <InputBase
                                        value={ipInput}
                                        onChange={e => {
                                            setIpInput(e.target.value);
                                            setIpError('');
                                        }}
                                        onKeyDown={handleIpKeyDown}
                                        disabled={isSystemManaged}
                                        placeholder={t(
                                            'adminAccessRestriction.ipAddressPlaceholder'
                                        )}
                                        sx={{ flex: 1, fontSize: '14px' }}
                                    />
                                    <IconButton
                                        size="small"
                                        disabled={isSystemManaged}
                                        onClick={() => {
                                            setIpInput('');
                                            setIpError('');
                                        }}
                                        sx={{ p: 0.5 }}
                                    >
                                        <CancelOutlinedIcon
                                            fontSize="small"
                                            sx={{ color: palette.text_40 }}
                                        />
                                    </IconButton>
                                </Box>
                                <Box
                                    sx={{
                                        border: `1px solid ${palette.control_border_normal}`,
                                        borderRadius: 1,
                                        minHeight: 98,
                                        overflow: 'hidden',
                                    }}
                                >
                                    {allowedIps.length === 0 ? (
                                        <Box sx={{ height: 49, p: 2 }}>
                                            <Typography variant="body7" color="text_40">
                                                {t('adminAccessRestriction.emptyAllowedIps')}
                                            </Typography>
                                        </Box>
                                    ) : (
                                        allowedIps.map((ip, index) => (
                                            <Box
                                                key={ip}
                                                sx={{
                                                    display: 'flex',
                                                    height: 49,
                                                    alignItems: 'center',
                                                    justifyContent: 'space-between',
                                                    px: 2,
                                                    py: 1.5,
                                                    borderBottom:
                                                        index < allowedIps.length - 1
                                                            ? `1px solid ${palette.control_border_normal}`
                                                            : 'none',
                                                }}
                                            >
                                                <Typography variant="body7" color="text_60">
                                                    {ip}
                                                </Typography>
                                                <IconButton
                                                    size="small"
                                                    disabled={isSystemManaged}
                                                    onClick={() => handleRemoveIp(ip)}
                                                    sx={{ p: 0.5 }}
                                                >
                                                    <CloseIcon
                                                        fontSize="small"
                                                        sx={{
                                                            color: palette.text_40,
                                                            fontSize: '16px',
                                                        }}
                                                    />
                                                </IconButton>
                                            </Box>
                                        ))
                                    )}
                                </Box>
                                {ipError && (
                                    <Box
                                        sx={{
                                            p: 0,
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 0.5,
                                        }}
                                    >
                                        <Image
                                            src={'/common/icon-ic-message-error.svg'}
                                            alt={'error'}
                                            width={20}
                                            height={20}
                                        />
                                        <Typography variant="body7" color="error">
                                            {ipError}
                                        </Typography>
                                    </Box>
                                )}
                            </Stack>
                        </Stack>
                    )}
                    {accessRestrictionError && (
                        <Box
                            sx={{
                                p: 0,
                                mb: 3,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 0.5,
                            }}
                        >
                            <Image
                                src={'/common/icon-ic-message-error.svg'}
                                alt={'error'}
                                width={20}
                                height={20}
                            />
                            <Typography variant="body7" color="error">
                                {accessRestrictionError}
                            </Typography>
                        </Box>
                    )}
                </Box>
            </Stack>

            {/* 검정 영역 - 버튼 */}
            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-end', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    onClick={handleAccessRestrictStatusSave}
                    disabled={
                        !!countryError ||
                        !!ipError ||
                        !!accessRestrictionError ||
                        isAllowListEmpty ||
                        isSystemManaged
                    }
                    sx={{ px: { xs: '29.5px', md: 2 }, minWidth: 80, minHeight: 40 }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </Stack>
        </Box>
    );
}

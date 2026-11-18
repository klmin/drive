import React, { useEffect, useState } from 'react';
import {
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    Typography,
} from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField from '@/components/common/input/StyledTextField';
import QuotaInput from '@/components/common/input/QuotaInput';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import api from '@/utils/api';
import { tenantAddressPrefix } from '@/utils/tenantPrefix';
import { emptyCheck } from '@/utils/userUtils';
import {
    bytesToGbNumber,
    bytesToQuotaInput,
    isQuotaInputUnchanged,
    quotaErrorMessage,
    quotaValueForUnit,
    resolveQuotaBytes,
    validateTenantQuotaCapacity,
    validateTenantTotalQuota,
} from '@/utils/quota';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import Image from 'next/image';

const fieldSx = {
    width: '100%',
    '& .MuiInputBase-input': {
        color: palette.text_80,
    },
};

const formControlSx = {
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
};

const formLabelSx = {
    ...typography.body5,
    color: palette.text_80,
    '& .MuiFormLabel-asterisk': {
        color: palette.status_error,
        fontWeight: 600,
    },
};

export default function UpdateTenantDialog({ open, onClose, reload, tenantSeq, listPatch }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [formData, setFormData] = useState({
        tenantName: '',
        subdomain: '',
        totalQuota: '',
        totalQuotaUnit: 'GB',
        allowedIp: '',
    });
    // 조회한 총 용량 — 입력을 건드리지 않았으면 이 값을 그대로 보낸다(MB 반올림 표시로 값이 바뀌지 않게)
    const [origin, setOrigin] = useState({ bytes: null, value: '', unit: 'GB' });
    const [baseDomain, setBaseDomain] = useState('');
    const [baseDomainSeq, setBaseDomainSeq] = useState(null);
    const [originSubdomain, setOriginSubdomain] = useState('');
    const [allowedIps, setAllowedIps] = useState([]);
    // 유형·사용자 모드 — 생성 후 바꿀 수 없어 표시만 한다
    const [tenantKind, setTenantKind] = useState({ tenantType: null, userMode: null });
    const [isSubmitting, setIsSubmitting] = useState(false);
    const systemManaged = tenantKind.tenantType === 'SYSTEM_MANAGED';

    const [errorTenantName, setErrorTenantName] = useState('');
    const [errorDomain, setErrorDomain] = useState('');
    const [errorTotalQuota, setErrorTotalQuota] = useState('');
    const [errorAllowedIp, setErrorAllowedIp] = useState('');
    // 총 용량 입력 판정용 스토리지 가용량·할당량 합 — 조회 실패면 null(인라인 상·하한 판정 생략)
    const [quotaCapacity, setQuotaCapacity] = useState(null);

    useEffect(() => {
        if (open) {
            setQuotaCapacity(null);
            fetchQuotaCapacity();
            setErrorTenantName('');
            setErrorDomain('');
            setErrorTotalQuota('');
            setErrorAllowedIp('');
            fetchBaseDomain();
            fetchTenants();
        }
    }, [open]);

    const fetchBaseDomain = async () => {
        const res = await api.Get('super-admin/base-domain', {});
        if (res?.status === 200) {
            setBaseDomain(res?.data?.data?.address ?? res?.data?.data?.baseDomain ?? '');
            setBaseDomainSeq(res?.data?.data?.baseDomainSeq ?? null);
        }
    };

    const fetchQuotaCapacity = async () => {
        const res = await api.Get(`super-admin/tenants/quota-capacity?ts=${tenantSeq}`, {});
        if (res?.status === 200) setQuotaCapacity(res?.data?.data ?? null);
    };

    const fetchTenants = async () => {
        const res = await api.Get(`super-admin/tenants/${tenantSeq}`, {});
        if (res?.status === 200) {
            // 키가 없는 테넌트(데이터 누락)는 null → 빈칸, 입력해야 저장 가능
            const totalQuotaByte = res?.data?.data?.totalQuotaByte ?? null;
            const { value: totalQuota, unit: totalQuotaUnit } = bytesToQuotaInput(totalQuotaByte);
            setOrigin({ bytes: totalQuotaByte, value: totalQuota, unit: totalQuotaUnit });
            setFormData(prev => ({
                ...prev,
                tenantName: res?.data?.data?.tenantName ?? '',
                subdomain: res?.data?.data?.subdomain ?? '',
                totalQuota,
                totalQuotaUnit,
            }));
            setOriginSubdomain(res?.data?.data?.subdomain ?? '');
            setAllowedIps(res?.data?.data?.allowedIps ?? []);
            setTenantKind({
                tenantType: res?.data?.data?.tenantType ?? null,
                userMode: res?.data?.data?.userMode ?? null,
            });
        } else {
            return;
        }
    };

    const isDisabled =
        !!errorTenantName ||
        !!errorDomain ||
        !!errorTotalQuota ||
        !formData.tenantName.trim() ||
        !formData.subdomain.trim() ||
        !formData.totalQuota;

    // 조회한 값·단위 그대로(MB 로 나누어떨어지지 않는 값 포함)면 저장 때 원래 byte 를 보내므로 검증하지 않는다
    // 형식 → 할당량 합·스토리지 가용량 순으로 판정
    const validateTotalQuota = (value, unit, capacity = quotaCapacity) => {
        try {
            if (!isQuotaInputUnchanged(value, unit, origin)) {
                validateTenantTotalQuota(value, t);
                validateTenantQuotaCapacity(resolveQuotaBytes(value, unit, origin), capacity, t);
            }
            setErrorTotalQuota('');
        } catch (error) {
            setErrorTotalQuota(error.message);
        }
    };

    // 가용량을 늦게 받으면 이미 고친 값을 다시 판정
    useEffect(() => {
        if (quotaCapacity)
            validateTotalQuota(formData.totalQuota, formData.totalQuotaUnit, quotaCapacity);
    }, [quotaCapacity]);

    // 단위 변경 — 빈칸(저장 비활성, 오류 문구는 띄우지 않음), 조회한 단위로 돌아오면 조회 값 복원
    const handleTotalQuotaUnitChange = unit => {
        const value = quotaValueForUnit(unit, origin);
        setFormData(prev => ({ ...prev, totalQuota: value, totalQuotaUnit: unit }));
        if (value === '') setErrorTotalQuota('');
        else validateTotalQuota(value, unit);
    };

    const handleChange = e => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        try {
            if (name === 'tenantName') {
                emptyCheck(value, t);
                setErrorTenantName('');
            }
            if (name === 'subdomain') {
                emptyCheck(value, t);
                setErrorDomain('');
            }
            if (name === 'totalQuota') {
                validateTotalQuota(value, formData.totalQuotaUnit);
            }
            if (name === 'allowedIp') {
                setErrorAllowedIp('');
            }
        } catch (error) {
            if (name === 'tenantName') setErrorTenantName(error.message);
            if (name === 'subdomain') setErrorDomain(error.message);
        }
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
        return (networkInt & mask) === networkInt;
    };

    const handleAllowedIpKeyDown = e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const ip = formData.allowedIp.trim();
            if (!ip) return;
            if (!isValidCidr(ip)) {
                setErrorAllowedIp(t('adminAccessRestriction.validateIpAddress'));
                return;
            }
            setErrorAllowedIp('');
            if (!allowedIps.includes(ip)) {
                setAllowedIps(prev => [...prev, ip]);
                setFormData(prev => ({ ...prev, allowedIp: '' }));
            }
        }
    };

    const handleSubmit = async () => {
        if (isDisabled || isSubmitting) return;
        setIsSubmitting(true);
        try {
            const body = {
                tn: formData.tenantName,
                sd: formData.subdomain,
                bs: baseDomainSeq,
                tq: resolveQuotaBytes(formData.totalQuota, formData.totalQuotaUnit, origin),
                ips: allowedIps,
            };
            const res = await api.Patch(`super-admin/tenants/${tenantSeq}`, body);
            if (res?.status === 200 || res?.code === 200) {
                showSnackbar(t('common.toast.editSuccess'), 'success');
                // 주소(clientUrl)는 서버가 만들어 주므로 테넌트 코드가 바뀌면 목록을 다시 읽고, 아니면 바뀐 행만 갱신
                if (listPatch && formData.subdomain === originSubdomain) {
                    listPatch.patch(tenantSeq, { tenantName: formData.tenantName });
                } else {
                    await reload?.();
                }
                onClose();
            } else {
                const quotaError = quotaErrorMessage(res?.errorCode, t);
                if (quotaError) setErrorTotalQuota(quotaError.inline);
                showSnackbar(quotaError?.toast ?? t('common.toast.editFail'), 'error');
            }
        } catch {
            showSnackbar(t('common.toast.editFail'), 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    pt: 8,
                    px: 8,
                    pb: 6,
                    margin: 0,
                    maxWidth: '480px !important',
                    width: '100%',
                    maxHeight: '90vh',
                    display: 'flex',
                    flexDirection: 'column',
                },
            }}
        >
            <DialogTitle sx={{ p: 0, mb: 4, flexShrink: 0 }}>
                <Typography variant="body1">{t('superAdmin.tenantDialog.editTitle')}</Typography>
            </DialogTitle>

            <DialogContent
                sx={{
                    p: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '16px',
                    flex: 1,
                    minHeight: 0,
                    overflowY: 'auto',
                    pt: '4px',
                    scrollbarWidth: 'none',
                    msOverflowStyle: 'none',
                    '&::-webkit-scrollbar': { display: 'none' },
                }}
            >
                {/* 유형·사용자 모드 — 표시만(생성 후 변경 불가) */}
                {tenantKind.tenantType && (
                    <Typography variant="body7" color="text_60">
                        {t('superAdmin.tenantDialog.tenantType.label')}:{' '}
                        {t(`superAdmin.tenantManagement.tenantType.${tenantKind.tenantType}`)}
                        {' · '}
                        {t('superAdmin.tenantDialog.userMode.label')}:{' '}
                        {t(`superAdmin.tenantManagement.userMode.${tenantKind.userMode}`)}
                        {' — '}
                        {t('superAdmin.tenantDialog.tenantType.immutable')}
                    </Typography>
                )}

                {/* 테넌트명 */}
                <FormControl fullWidth sx={formControlSx}>
                    <FormLabel required sx={formLabelSx}>
                        {t('superAdmin.tenantDialog.tenantName.label')}
                    </FormLabel>
                    <StyledTextField
                        name="tenantName"
                        value={formData.tenantName}
                        onChange={handleChange}
                        placeholder={t('superAdmin.tenantDialog.tenantName.placeholder')}
                        variant="outlined"
                        height={40}
                        error={!!errorTenantName}
                        errorMessage={errorTenantName}
                        sx={fieldSx}
                    />
                </FormControl>

                {/* 테넌트 도메인 */}
                <FormControl fullWidth sx={formControlSx}>
                    <FormLabel required sx={formLabelSx}>
                        {t('superAdmin.tenantDialog.tenantDomain.label')}
                    </FormLabel>
                    <Box sx={{ display: 'flex', flexDirection: 'row' }}>
                        <Box
                            sx={{
                                height: 40,
                                px: '14px',
                                display: 'flex',
                                alignItems: 'center',
                                bgcolor: palette.btn_white_bg_hover,
                                border: `1px solid ${palette.control_border_normal}`,
                                borderRight: 'none',
                                borderTopLeftRadius: '4px',
                                borderBottomLeftRadius: '4px',
                                whiteSpace: 'nowrap',
                            }}
                        >
                            <Typography variant="body3" color="text_80">
                                {tenantAddressPrefix(baseDomain || 'thinkfree.com')}
                            </Typography>
                        </Box>
                        <StyledTextField
                            name="subdomain"
                            value={formData.subdomain}
                            onChange={handleChange}
                            placeholder={t('superAdmin.tenantDialog.tenantDomain.placeholder')}
                            variant="outlined"
                            height={40}
                            error={!!errorDomain}
                            errorMessage={errorDomain}
                            sx={{
                                ...fieldSx,
                                '& .MuiInputBase-root': {
                                    borderTopLeftRadius: '0 !important',
                                    borderBottomLeftRadius: '0 !important',
                                },
                            }}
                        />
                    </Box>
                </FormControl>

                {/* 테넌트 총 용량(GB/MB) — 서버에는 byte 로 보낸다 */}
                <FormControl fullWidth sx={formControlSx}>
                    <FormLabel required sx={formLabelSx}>
                        {t('superAdmin.tenantDialog.totalQuota.label')}
                    </FormLabel>
                    <QuotaInput
                        name="totalQuota"
                        value={formData.totalQuota}
                        unit={formData.totalQuotaUnit}
                        onChange={handleChange}
                        onUnitChange={handleTotalQuotaUnitChange}
                        placeholder={t('superAdmin.tenantDialog.totalQuota.placeholder')}
                        error={!!errorTotalQuota}
                        errorMessage={errorTotalQuota}
                        sx={fieldSx}
                    />
                    {/* 서버가 계산한 총 용량 하한, 조회 실패면 숨김 */}
                    {quotaCapacity?.tenantMinimumQuotaByte != null && (
                        <Typography variant="body7" color="text_60">
                            {t('superAdmin.tenantDialog.totalQuota.minimumDesc', {
                                allocatedQuota: bytesToGbNumber(
                                    quotaCapacity.tenantMinimumQuotaByte,
                                    { ceil: true }
                                ),
                            })}
                        </Typography>
                    )}
                </FormControl>

                {/* 접근 허용 IP — SYSTEM_MANAGED 는 ADMIN 허용 IP 를 두지 않아 숨긴다(서버도 무시) */}
                {!systemManaged && (
                    <FormControl fullWidth sx={formControlSx}>
                        <FormLabel sx={formLabelSx}>
                            {t('adminAccessRestriction.allowedIps')}
                        </FormLabel>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '7px' }}>
                            <Box sx={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <StyledTextField
                                    name="allowedIp"
                                    value={formData.allowedIp}
                                    onChange={handleChange}
                                    onKeyDown={handleAllowedIpKeyDown}
                                    placeholder={t('adminAccessRestriction.ipAddressPlaceholder')}
                                    variant="outlined"
                                    height={40}
                                    error={!!errorAllowedIp}
                                    errorMessage={errorAllowedIp}
                                    sx={{
                                        ...fieldSx,
                                        '& .MuiInputBase-root.Mui-focused': { outline: 'none' },
                                        '& .MuiInputBase-root.Mui-error': { outline: 'none' },
                                    }}
                                    slotProps={{
                                        input: {
                                            endAdornment: formData.allowedIp ? (
                                                <Box
                                                    onClick={() =>
                                                        setFormData(prev => ({
                                                            ...prev,
                                                            allowedIp: '',
                                                        }))
                                                    }
                                                    sx={{
                                                        cursor: 'pointer',
                                                        flexShrink: 0,
                                                        width: 20,
                                                        height: 20,
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                    }}
                                                >
                                                    <Image
                                                        src="/common/icon-ic-table-close.svg"
                                                        alt="clear"
                                                        width={20}
                                                        height={20}
                                                    />
                                                </Box>
                                            ) : null,
                                        },
                                    }}
                                />
                                {allowedIps.length > 0 && (
                                    <Box
                                        sx={{
                                            flexShrink: 0,
                                            height: '120px',
                                            overflowY: 'auto',
                                            py: '4px',
                                            bgcolor: palette.white,
                                            borderRadius: '4px',
                                            outline: `1px solid ${palette.control_border_normal}`,
                                            outlineOffset: '-1px',
                                        }}
                                    >
                                        {allowedIps.map((ip, index) => (
                                            <Box
                                                key={ip}
                                                sx={{
                                                    height: 40,
                                                    px: '12px',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'space-between',
                                                    bgcolor: palette.white,
                                                    '&:hover': {
                                                        bgcolor: palette.btn_gray_bg_normal,
                                                    },
                                                }}
                                            >
                                                <Typography variant="body3" color="text_80">
                                                    {ip}
                                                </Typography>
                                                <Box
                                                    onMouseDown={e => e.preventDefault()}
                                                    onClick={() =>
                                                        setAllowedIps(prev =>
                                                            prev.filter((_, i) => i !== index)
                                                        )
                                                    }
                                                    sx={{
                                                        width: 20,
                                                        height: 20,
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        cursor: 'pointer',
                                                        flexShrink: 0,
                                                    }}
                                                >
                                                    <Image
                                                        src="/common/icon-ic-close-gray.svg"
                                                        alt="remove"
                                                        width={16}
                                                        height={16}
                                                    />
                                                </Box>
                                            </Box>
                                        ))}
                                    </Box>
                                )}
                            </Box>
                            <Typography variant="body7" color="text_60">
                                {t('superAdmin.tenantDialog.allowedIp.desc')}
                            </Typography>
                        </Box>
                    </FormControl>
                )}
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: 6, flexShrink: 0 }}>
                <StyledButton
                    onClick={onClose}
                    color="btn_border"
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                >
                    <Typography variant="body5" color="text_100">
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color="btn_point_bg"
                    variant="contained"
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                    onClick={handleSubmit}
                    disabled={isDisabled || isSubmitting}
                >
                    <Typography variant="body5" color="white">
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

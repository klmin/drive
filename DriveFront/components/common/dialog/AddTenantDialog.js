import React, { useEffect, useState } from 'react';
import {
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    MenuItem,
    Typography,
} from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledSelect from '@/components/common/input/StyledSelect';
import QuotaInput from '@/components/common/input/QuotaInput';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import api from '@/utils/api';
import { tenantAddressPrefix } from '@/utils/tenantPrefix';
import { emptyCheck, validateEmailPattern, validatePassword } from '@/utils/userUtils';
import { convertSizeToBytes } from '@/utils/math';
import {
    quotaErrorMessage,
    validateTenantQuotaCapacity,
    validateTenantTotalQuota,
} from '@/utils/quota';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useDialog } from '@/provider/DialogProvider';
import { useTranslations } from 'next-intl';
import { useRouter } from '@/hooks/useRouter';
import Image from 'next/image';
import { ROUTES } from '@/constants/routes';

/** 테넌트 유형(tt)·사용자 모드(um) — 생성 후 바꿀 수 없다. SINGLE 은 SYSTEM_MANAGED 만(서버 TENANT_009) */
const TENANT_TYPES = ['SELF_MANAGED', 'SYSTEM_MANAGED'];
const USER_MODES = ['MULTI', 'SINGLE'];

const INITIAL_FORM = {
    tenantType: 'SELF_MANAGED',
    userMode: 'MULTI',
    tenantName: '',
    subDomain: '',
    totalQuota: '1',
    totalQuotaUnit: 'GB',
    allowedIp: '',
    adminName: '',
    adminEmail: '',
    adminPassword: '',
};

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

export default function AddTenantDialog({ open, onClose, reload }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { openDialog } = useDialog();
    const router = useRouter();
    const [formData, setFormData] = useState(INITIAL_FORM);
    const [baseDomainSeq, setBaseDomainSeq] = useState(null);
    const [baseDomain, setBaseDomain] = useState('');
    const [allowedIps, setAllowedIps] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [errorTenantName, setErrorTenantName] = useState('');
    const [errorDomain, setErrorDomain] = useState('');
    const [errorTotalQuota, setErrorTotalQuota] = useState('');
    const [errorAdminName, setErrorAdminName] = useState('');
    const [errorAdminEmail, setErrorAdminEmail] = useState('');
    const [errorAdminPassword, setErrorAdminPassword] = useState('');
    const [errorAllowedIp, setErrorAllowedIp] = useState('');
    // 총 용량 입력 판정용 스토리지 가용량 — 조회 실패면 null(인라인 상한 판정 생략)
    const [quotaCapacity, setQuotaCapacity] = useState(null);

    useEffect(() => {
        if (open) {
            setFormData(INITIAL_FORM);
            setQuotaCapacity(null);
            fetchQuotaCapacity();
            setErrorTenantName('');
            setErrorDomain('');
            setErrorTotalQuota('');
            setErrorAdminName('');
            setErrorAdminEmail('');
            setErrorAdminPassword('');
            setAllowedIps([]);
            setErrorAllowedIp('');
            fetchBaseDomain();
        }
    }, [open]);

    const fetchQuotaCapacity = async () => {
        const res = await api.Get('super-admin/tenants/quota-capacity', {});
        if (res?.status === 200) setQuotaCapacity(res?.data?.data ?? null);
    };

    // 정수 형식 → 스토리지 가용량 순으로 판정(실패 시 throw)
    const checkTotalQuota = (value, unit, capacity = quotaCapacity) => {
        validateTenantTotalQuota(value, t);
        validateTenantQuotaCapacity(convertSizeToBytes(value, unit), capacity, t);
    };

    // 가용량을 늦게 받으면 이미 들어간 값(기본 1GB 포함)을 다시 판정
    useEffect(() => {
        if (!quotaCapacity) return;
        try {
            checkTotalQuota(formData.totalQuota, formData.totalQuotaUnit, quotaCapacity);
            setErrorTotalQuota('');
        } catch (error) {
            setErrorTotalQuota(error.message);
        }
    }, [quotaCapacity]);

    const fetchBaseDomain = async () => {
        const res = await api.Get('super-admin/base-domain', {});
        if (res?.status === 200) {
            const data = res?.data?.data;
            if (!data?.baseDomain) {
                onClose();
                openDialog('alert', {
                    title: t('superAdmin.tenantManagement.domainSettingDialog.title'),
                    description: t('superAdmin.tenantManagement.domainSettingDialog.desc'),
                    buttonName: t('superAdmin.tenantManagement.domainSettingDialog.confirm'),
                    cancelButtonName: '',
                    onClick: async () => {
                        router.push(ROUTES.SUPERADMIN_SETTINGS_SYSTEM_INFO);
                    },
                });
                return;
            }
            setBaseDomainSeq(data?.baseDomainSeq ?? null);
            setBaseDomain(data?.address ?? data?.baseDomain ?? '');
        }
    };

    const isDisabled =
        !!errorTenantName ||
        !!errorDomain ||
        !!errorTotalQuota ||
        !!errorAdminName ||
        !!errorAdminEmail ||
        !!errorAdminPassword ||
        !formData.tenantName.trim() ||
        !formData.subDomain.trim() ||
        !formData.totalQuota ||
        !formData.adminName.trim() ||
        !formData.adminEmail.trim() ||
        !formData.adminPassword.trim();

    // 단위 변경 — 환산하지 않고 빈칸(저장 비활성, 오류 문구는 띄우지 않음)
    const handleTotalQuotaUnitChange = unit => {
        setFormData(prev => ({ ...prev, totalQuota: '', totalQuotaUnit: unit }));
        setErrorTotalQuota('');
    };

    const systemManaged = formData.tenantType === 'SYSTEM_MANAGED';

    const handleChange = e => {
        const { name, value } = e.target;
        if (name === 'tenantType') {
            // SELF_MANAGED 는 MULTI 만 — 유형을 되돌리면 모드도 MULTI 로
            setFormData(prev => ({
                ...prev,
                tenantType: value,
                userMode: value === 'SYSTEM_MANAGED' ? prev.userMode : 'MULTI',
            }));
            return;
        }
        setFormData(prev => ({ ...prev, [name]: value }));
        try {
            if (name === 'tenantName') {
                emptyCheck(value, t);
                setErrorTenantName('');
            }
            if (name === 'subDomain') {
                emptyCheck(value, t);
                setErrorDomain('');
            }
            if (name === 'totalQuota') {
                checkTotalQuota(value, formData.totalQuotaUnit);
                setErrorTotalQuota('');
            }
            if (name === 'allowedIp') {
                setErrorAllowedIp('');
            }
            if (name === 'adminName') {
                emptyCheck(value, t);
                setErrorAdminName('');
            }
            if (name === 'adminEmail') {
                emptyCheck(value, t);
                validateEmailPattern(value, t);
                setErrorAdminEmail('');
            }
            if (name === 'adminPassword') {
                emptyCheck(value, t);
                validatePassword(value, t);
                setErrorAdminPassword('');
            }
        } catch (error) {
            if (name === 'tenantName') setErrorTenantName(error.message);
            if (name === 'subDomain') setErrorDomain(error.message);
            if (name === 'totalQuota') setErrorTotalQuota(error.message);
            if (name === 'adminName') setErrorAdminName(error.message);
            if (name === 'adminEmail') setErrorAdminEmail(error.message);
            if (name === 'adminPassword') setErrorAdminPassword(error.message);
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
                sd: formData.subDomain,
                bs: baseDomainSeq,
                e: formData.adminEmail,
                n: formData.adminName,
                p: formData.adminPassword,
                tq: convertSizeToBytes(formData.totalQuota, formData.totalQuotaUnit),
                tt: formData.tenantType,
                um: formData.userMode,
                // SYSTEM_MANAGED 는 ADMIN 허용 IP 를 두지 않는다(서버도 무시)
                ips: systemManaged ? [] : allowedIps,
            };
            const res = await api.Post('super-admin/tenants', body);
            if (res?.status === 200 || res?.code === 200 || res?.code === 201) {
                showSnackbar(t('common.toast.addSuccess'), 'success');
                await reload?.();
                onClose();
            } else {
                const quotaError = quotaErrorMessage(res?.errorCode, t);
                if (quotaError) setErrorTotalQuota(quotaError.inline);
                showSnackbar(quotaError?.toast ?? t('common.toast.addFail'), 'error');
            }
        } catch {
            showSnackbar(t('common.toast.addFail'), 'error');
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
                <Typography variant="body1">{t('superAdmin.tenantDialog.addTitle')}</Typography>
            </DialogTitle>

            <DialogContent
                sx={{
                    p: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '32px',
                    flex: 1,
                    minHeight: 0,
                    overflowY: 'auto',
                    scrollbarWidth: 'none',
                    msOverflowStyle: 'none',
                    '&::-webkit-scrollbar': { display: 'none' },
                }}
            >
                {/* 기본 필드 그룹 */}
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', pt: '4px' }}>
                    {/* 유형·사용자 모드 — 생성 후 변경 불가 */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '7px' }}>
                        <Box sx={{ display: 'flex', flexDirection: 'row', gap: '12px' }}>
                            <FormControl fullWidth sx={formControlSx}>
                                <FormLabel required sx={formLabelSx}>
                                    {t('superAdmin.tenantDialog.tenantType.label')}
                                </FormLabel>
                                <StyledSelect
                                    select
                                    name="tenantType"
                                    value={formData.tenantType}
                                    onChange={handleChange}
                                    height={40}
                                    variant="outlined"
                                    sx={fieldSx}
                                >
                                    {TENANT_TYPES.map(type => (
                                        <MenuItem key={type} value={type}>
                                            <Typography variant="body5">
                                                {t(
                                                    `superAdmin.tenantManagement.tenantType.${type}`
                                                )}
                                            </Typography>
                                        </MenuItem>
                                    ))}
                                </StyledSelect>
                            </FormControl>
                            <FormControl fullWidth sx={formControlSx}>
                                <FormLabel required sx={formLabelSx}>
                                    {t('superAdmin.tenantDialog.userMode.label')}
                                </FormLabel>
                                <StyledSelect
                                    select
                                    name="userMode"
                                    value={formData.userMode}
                                    onChange={handleChange}
                                    disabled={!systemManaged}
                                    height={40}
                                    variant="outlined"
                                    sx={fieldSx}
                                >
                                    {USER_MODES.map(mode => (
                                        <MenuItem key={mode} value={mode}>
                                            <Typography variant="body5">
                                                {t(`superAdmin.tenantManagement.userMode.${mode}`)}
                                            </Typography>
                                        </MenuItem>
                                    ))}
                                </StyledSelect>
                            </FormControl>
                        </Box>
                        <Typography variant="body7" color="text_60" sx={{ whiteSpace: 'pre-line' }}>
                            {systemManaged
                                ? t(
                                      formData.userMode === 'SINGLE'
                                          ? 'superAdmin.tenantDialog.tenantType.systemSingleDesc'
                                          : 'superAdmin.tenantDialog.tenantType.systemMultiDesc'
                                  )
                                : t('superAdmin.tenantDialog.tenantType.selfDesc')}
                        </Typography>
                    </Box>

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
                        <Box
                            sx={{
                                display: 'flex',
                                flexDirection: 'row',
                            }}
                        >
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
                                name="subDomain"
                                value={formData.subDomain}
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
                    </FormControl>

                    {/* 접근 허용 IP — SYSTEM_MANAGED 는 ADMIN 허용 IP 를 두지 않아 숨긴다 */}
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
                                        placeholder={t(
                                            'adminAccessRestriction.ipAddressPlaceholder'
                                        )}
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
                                                            // opacity: 0.4,
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
                </Box>

                {/* 어드민 대표 계정 섹션 */}
                <Box
                    sx={{
                        p: 4,
                        bgcolor: palette.bg_gray,
                        borderRadius: '6px',
                        border: `1px solid ${palette.divider_8}`,
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '20px',
                    }}
                >
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <Typography variant="subtitle1" color="text_100">
                            {t('superAdmin.tenantDialog.adminAccount.title')}
                        </Typography>
                        <Typography
                            variant="body10"
                            color="text_80"
                            sx={{ whiteSpace: 'pre-line' }}
                        >
                            {t('superAdmin.tenantDialog.adminAccount.desc')}
                        </Typography>
                    </Box>

                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        {/* 이름 */}
                        <FormControl fullWidth sx={formControlSx}>
                            <FormLabel required sx={formLabelSx}>
                                {t('common.label.name')}
                            </FormLabel>
                            <StyledTextField
                                name="adminName"
                                value={formData.adminName}
                                onChange={handleChange}
                                placeholder={t('common.placeholder.name')}
                                variant="outlined"
                                height={40}
                                error={!!errorAdminName}
                                errorMessage={errorAdminName}
                                sx={fieldSx}
                            />
                        </FormControl>

                        {/* 이메일 */}
                        <FormControl fullWidth sx={formControlSx}>
                            <FormLabel required sx={formLabelSx}>
                                {t('common.label.email')}
                            </FormLabel>
                            <StyledTextField
                                name="adminEmail"
                                value={formData.adminEmail}
                                onChange={handleChange}
                                placeholder={t('common.placeholder.email')}
                                variant="outlined"
                                height={40}
                                error={!!errorAdminEmail}
                                errorMessage={errorAdminEmail}
                                sx={fieldSx}
                            />
                        </FormControl>

                        {/* 초기 비밀번호 */}
                        <FormControl fullWidth sx={formControlSx}>
                            <FormLabel required sx={formLabelSx}>
                                {t('common.label.initialPassword')}
                            </FormLabel>
                            <StyledTextField
                                name="adminPassword"
                                value={formData.adminPassword}
                                onChange={handleChange}
                                placeholder={t('common.placeholder.initialPassword')}
                                variant="outlined"
                                height={40}
                                error={!!errorAdminPassword}
                                errorMessage={errorAdminPassword}
                                sx={fieldSx}
                            />
                        </FormControl>
                    </Box>
                </Box>
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
                        {t('common.button.add')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
